package dev.foldman.cast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.litvak.chromecast.api.v2.ChromeCast
import su.litvak.chromecast.api.v2.Media
import su.litvak.chromecast.api.v2.MediaStatus
import java.util.concurrent.ConcurrentHashMap

class AndroidCastManager(private val context: Context) {
    companion object {
        const val TAPEMAN_APP_ID = "8E0C5F4B"
        const val DEFAULT_MEDIA_RECEIVER_APP_ID = "CC1AD845"
        private const val CAST_SERVICE_TYPE = "_googlecast._tcp."
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _availableDevices = MutableStateFlow<List<CastDevice>>(emptyList())
    val availableDevices: StateFlow<List<CastDevice>> = _availableDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<CastDevice?>(null)
    val connectedDevice: StateFlow<CastDevice?> = _connectedDevice.asStateFlow()

    val isConnected: Boolean get() = _connectedDevice.value != null

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectingDeviceId = MutableStateFlow<String?>(null)
    val connectingDeviceId: StateFlow<String?> = _connectingDeviceId.asStateFlow()

    private val _connectionStatus = MutableStateFlow<String?>(null)
    val connectionStatus: StateFlow<String?> = _connectionStatus.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(0.20f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var client: ChromeCast? = null
    private var pollJob: Job? = null
    private var currentAppId: String = TAPEMAN_APP_ID
    private var lastUrl: String? = null

    // Discovered devices cache
    private val deviceMap = ConcurrentHashMap<String, CastDevice>()

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    init {
        startScan()
    }

    fun startScan() {
        if (_isScanning.value) return
        val nsd = nsdManager ?: return

        scope.launch {
            _isScanning.value = true
            deviceMap.clear()
            _availableDevices.value = emptyList()

            try {
                if (multicastLock == null) {
                    multicastLock = wifiManager?.createMulticastLock("tapeman_cast_mdns")?.apply {
                        setReferenceCounted(true)
                        acquire()
                    }
                }

                stopDiscoverySafely()

                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(regType: String) {}

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.serviceType.contains("googlecast")) {
                            runCatching {
                                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}

                                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                                        val host = resolved.host?.hostAddress ?: return
                                        if (host.startsWith("127.")) return
                                        val port = resolved.port

                                        val attrs = resolved.attributes
                                        val friendlyName = attrs["fn"]?.let { String(it) } ?: resolved.serviceName
                                        val model = attrs["md"]?.let { String(it) } ?: "Google Cast"
                                        val deviceId = "cast_${resolved.serviceName}"

                                        val dev = CastDevice(
                                            id = deviceId,
                                            name = friendlyName,
                                            model = model,
                                            address = host,
                                            port = port,
                                            isCurrent = deviceId == _connectedDevice.value?.id
                                        )

                                        deviceMap[deviceId] = dev
                                        _availableDevices.value = deviceMap.values.sortedBy { it.name }
                                    }
                                })
                            }
                        }
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        val deviceId = "cast_${serviceInfo.serviceName}"
                        deviceMap.remove(deviceId)
                        _availableDevices.value = deviceMap.values.sortedBy { it.name }
                    }

                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        stopDiscoverySafely()
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }

                discoveryListener = listener
                nsd.discoverServices(CAST_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

                // Scan actively for 8 seconds
                delay(8000)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun stopDiscoverySafely() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        runCatching { nsdManager?.stopServiceDiscovery(listener) }
        runCatching {
            multicastLock?.takeIf { it.isHeld }?.release()
        }
        multicastLock = null
    }

    private fun detectMimeType(url: String): String = when {
        url.contains(".flac", ignoreCase = true) -> "audio/flac"
        url.contains(".m4a", ignoreCase = true) || url.contains(".aac", ignoreCase = true) -> "audio/mp4"
        url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
        url.contains(".wav", ignoreCase = true) -> "audio/wav"
        else -> "audio/mpeg"
    }

    private suspend fun ensureAppReady(cast: ChromeCast): Boolean {
        val appId = currentAppId
        for (i in 1..8) {
            try {
                if (cast.isAppRunning(appId)) return true
            } catch (_: Throwable) {}
            if (i == 1) {
                val launched = runCatching { cast.launchApp(appId) }.getOrNull()
                if (launched == null && appId == TAPEMAN_APP_ID) {
                    currentAppId = DEFAULT_MEDIA_RECEIVER_APP_ID
                    runCatching { cast.launchApp(DEFAULT_MEDIA_RECEIVER_APP_ID) }
                }
            }
            delay(400)
        }
        return false
    }

    suspend fun connectToDevice(device: CastDevice): Boolean = withContext(Dispatchers.IO) {
        _connectingDeviceId.value = device.id
        _connectionStatus.value = "Connecting to ${device.name}..."

        try {
            disconnect()
            val cast = ChromeCast(device.address, device.port)
            cast.connect()
            cast.setAutoReconnect(true)

            // Launch Tapeman custom receiver or fallback to Default Media Receiver
            val running = runCatching { cast.status?.runningApp }.getOrNull()
            var launchedAppId = TAPEMAN_APP_ID
            if (running == null || (running.id != TAPEMAN_APP_ID && running.id != DEFAULT_MEDIA_RECEIVER_APP_ID)) {
                val launched = runCatching { cast.launchApp(TAPEMAN_APP_ID) }.getOrNull()
                if (launched == null) {
                    cast.launchApp(DEFAULT_MEDIA_RECEIVER_APP_ID)
                    launchedAppId = DEFAULT_MEDIA_RECEIVER_APP_ID
                } else {
                    launchedAppId = launched.id ?: TAPEMAN_APP_ID
                }
                delay(800)
            } else {
                launchedAppId = running.id
            }
            currentAppId = launchedAppId

            client = cast
            _connectedDevice.value = device.copy(isCurrent = true)
            startPolling()

            _volume.value = 0.20f
            runCatching { cast.setVolume(0.20f) }

            _connectionStatus.value = "Connected to ${device.name}"
            true
        } catch (t: Throwable) {
            val msg = t.localizedMessage ?: t.javaClass.simpleName
            _connectionStatus.value = "Failed: $msg"
            disconnect()
            false
        } finally {
            _connectingDeviceId.value = null
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        runCatching { client?.disconnect() }
        client = null
        _connectedDevice.value = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(600)
                try {
                    val cast = client ?: break
                    if (cast.isConnected) {
                        val status = cast.mediaStatus
                        if (status != null) {
                            _isPlaying.value = status.playerState == MediaStatus.PlayerState.PLAYING
                            _currentPositionMs.value = (status.currentTime * 1000).toLong()
                            val dur = status.media?.duration
                            if (dur != null && dur > 0) {
                                _durationMs.value = (dur * 1000).toLong()
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun createMusicMedia(
        url: String,
        title: String,
        artist: String,
        album: String,
        coverUrl: String?
    ): Media {
        val contentType = detectMimeType(url)
        val thumb = coverUrl?.takeIf { it.isNotBlank() }
            ?: "https://raw.githubusercontent.com/artywah/Tapeman/main/art/icon.png"

        val metadata = mutableMapOf<String, Any>(
            "metadataType" to 3, // MEDIA_TYPE_MUSIC_TRACK
            "title" to title.ifBlank { "Tapeman Audio" },
            "artist" to artist.ifBlank { "Tapeman Stereo" },
            "albumName" to album.ifBlank { "Tapeman Tape" },
            "albumArtist" to artist.ifBlank { "Tapeman Stereo" },
            "seriesTitle" to "Tapeman Stereo",
            "subtitle" to if (album.isNotBlank()) album else artist,
            "images" to listOf(
                mapOf(
                    "url" to thumb,
                    "width" to 512,
                    "height" to 512
                )
            )
        )

        return Media(
            url,
            contentType,
            null,
            Media.StreamType.BUFFERED,
            null,
            metadata,
            null,
            null
        )
    }

    fun play(
        url: String,
        title: String,
        artist: String,
        album: String,
        coverUrl: String?
    ) {
        lastUrl = url
        scope.launch {
            try {
                val cast = client ?: return@launch
                ensureAppReady(cast)
                val media = createMusicMedia(url, title, artist, album, coverUrl)
                cast.load(media)
                _isPlaying.value = true
            } catch (t: Throwable) {
                t.printStackTrace()
                _isPlaying.value = false
            }
        }
    }

    fun pause() {
        scope.launch {
            try {
                client?.pause()
                _isPlaying.value = false
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun resume() {
        scope.launch {
            try {
                client?.play()
                _isPlaying.value = true
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        scope.launch {
            try {
                client?.seek(positionMs / 1000.0)
                _currentPositionMs.value = positionMs
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped
        scope.launch {
            try {
                client?.setVolume(clamped)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }
}
