package dev.foldman.desktop.audio

import dev.foldman.audio.AudioBackend
import dev.foldman.deck.LocalTrack
import dev.foldman.desktop.device.AudioOutputDevice
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

class GoogleCastAudioBackend : AudioBackend {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(0.20f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var client: ChromeCast? = null
    private var pollJob: Job? = null
    private var activeDevice: AudioOutputDevice? = null
    private var lastUrl: String? = null
    var lastErrorMessage: String? = null

    fun setLastUrl(url: String) {
        lastUrl = url
    }

    // Track metadata for the receiver display
    var currentTrackTitle: String = "Tapeman Audio"
    var currentArtist: String = ""
    var currentAlbum: String = ""
    var currentCoverUrl: String? = null

    fun setTrackMetadata(title: String, artist: String, album: String, coverUrl: String?) {
        currentTrackTitle = title
        currentArtist = artist
        currentAlbum = album
        currentCoverUrl = coverUrl
    }

    private fun resolveIpv4Direct(host: String): String? {
        val cleanHost = host.trimEnd('.')
        // 1. Try dscacheutil
        runCatching {
            val pb = ProcessBuilder("dscacheutil", "-q", "host", "-a", "name", cleanHost)
            val proc = pb.start()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line?.trim() ?: continue
                if (l.startsWith("ip_address:")) {
                    val ip = l.substringAfter("ip_address:").trim()
                    if (ip.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""")) && !ip.startsWith("127.")) {
                        proc.destroy()
                        return ip
                    }
                }
            }
        }
        // 2. Try InetAddress
        runCatching {
            val addr = java.net.InetAddress.getByName(cleanHost)
            val ip = addr.hostAddress
            if (ip.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) {
                return ip
            }
        }
        return null
    }

    fun detectMimeType(url: String): String = when {
        url.contains(".flac", ignoreCase = true) -> "audio/flac"
        url.contains(".m4a", ignoreCase = true) || url.contains(".aac", ignoreCase = true) -> "audio/mp4"
        url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
        url.contains(".wav", ignoreCase = true) -> "audio/wav"
        else -> "audio/mpeg"
    }

    private suspend fun ensureAppReady(cast: ChromeCast): Boolean {
        for (i in 1..8) {
            try {
                if (cast.isAppRunning("CC1AD845")) return true
            } catch (_: Throwable) {}
            if (i == 1) {
                runCatching { cast.launchApp("CC1AD845") }
            }
            delay(400)
        }
        return false
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
            "metadataType" to 3, // 3 = MEDIA_TYPE_MUSIC_TRACK in Google Cast CAF
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

    suspend fun loadStandbyCard() {
        try {
            val cast = client ?: return
            ensureAppReady(cast)
            val silenceUrl = "https://raw.githubusercontent.com/artywah/Tapeman/main/art/silence.mp3"
            println("[GoogleCast] Displaying standby card on ${activeDevice?.name ?: "Cast Device"}")
            val media = createMusicMedia(
                url = silenceUrl,
                title = "Tapeman · Connected",
                artist = "Ready to Play",
                album = "Stereo Cassette Player",
                coverUrl = "https://raw.githubusercontent.com/artywah/Tapeman/main/art/icon.png"
            )
            cast.load(media)
            delay(300)
            cast.pause()
        } catch (t: Throwable) {
            println("[GoogleCast] Standby card note: ${t.message}")
        }
    }

    suspend fun loadReady(url: String) {
        lastUrl = url
        try {
            val cast = client ?: return
            ensureAppReady(cast)
            println("[GoogleCast] Loading ready track: $currentTrackTitle — $currentArtist ($currentAlbum)")
            val media = createMusicMedia(
                url = url,
                title = currentTrackTitle,
                artist = currentArtist,
                album = currentAlbum,
                coverUrl = currentCoverUrl
            )
            cast.load(media)
            delay(300)
            cast.pause()
            _isPlaying.value = false
        } catch (t: Throwable) {
            println("[GoogleCast] loadReady note: ${t.message}")
        }
    }

    private fun resolveInstanceToIp(instanceName: String): String? {
        return runCatching {
            val pb = ProcessBuilder("dns-sd", "-L", instanceName, "_googlecast._tcp", "local")
            val proc = pb.start()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
            var host: String? = null
            val timer = java.util.Timer()
            timer.schedule(object : java.util.TimerTask() {
                override fun run() { proc.destroyForcibly() }
            }, 1200)
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: break
                    if (l.contains("can be reached at")) {
                        val m = Regex("""can be reached at ([^:]+):""").find(l)
                        if (m != null) {
                            host = m.groupValues[1].removeSuffix(".")
                            break
                        }
                    }
                }
            } finally {
                timer.cancel()
                proc.destroyForcibly()
            }
            if (host != null) resolveIpv4Direct(host) else null
        }.getOrNull()
    }

    suspend fun connectToDevice(device: AudioOutputDevice): Boolean = withContext(Dispatchers.IO) {
        var addr = device.address
        if (addr.isNullOrBlank()) {
            val rawInstance = device.id.removePrefix("cast_")
            println("[GoogleCast] Address was null for ${device.name}, attempting resolution for $rawInstance...")
            addr = resolveInstanceToIp(rawInstance)
        }
        if (addr.isNullOrBlank()) {
            val err = "Device address could not be resolved on local network"
            lastErrorMessage = err
            println("[GoogleCast] Cannot connect: $err for ${device.name}")
            return@withContext false
        }
        addr = if (addr.endsWith(".local") || !addr.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) {
            resolveIpv4Direct(addr) ?: addr
        } else addr

        try {
            disconnect()
            println("[GoogleCast] Connecting to ${device.name} at $addr:${device.port}...")
            val cast = ChromeCast(addr, device.port)
            cast.connect()
            cast.setAutoReconnect(true)

            // Launch Default Media Receiver app directly (matching verified working protocol)
            println("[GoogleCast] Launching Default Media Receiver (CC1AD845)...")
            val running = runCatching { cast.status?.runningApp }.getOrNull()
            if (running == null || running.id != "CC1AD845") {
                cast.launchApp("CC1AD845")
                delay(800)
            }

            client = cast
            activeDevice = device
            startPolling()
            _volume.value = 0.20f
            runCatching { cast.setVolume(0.20f) }
            println("[GoogleCast] Connected successfully to ${device.name} at 20% volume!")
            lastErrorMessage = null
            true
        } catch (t: Throwable) {
            val msg = t.localizedMessage ?: t.javaClass.simpleName
            lastErrorMessage = msg
            println("[GoogleCast] Connection failed: $msg")
            t.printStackTrace()
            false
        }
    }

    fun disconnect() {
        pollJob?.cancel()
        pollJob = null
        try {
            client?.disconnect()
        } catch (_: Throwable) {}
        client = null
        activeDevice = null
        _isPlaying.value = false
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(600)
                try {
                    val cast = client ?: break
                    if (cast.isConnected) {
                        val mediaStatus = cast.mediaStatus
                        if (mediaStatus != null) {
                            _isPlaying.value = mediaStatus.playerState == MediaStatus.PlayerState.PLAYING
                            _currentPositionMs.value = (mediaStatus.currentTime * 1000).toLong()
                            val dur = mediaStatus.media?.duration
                            if (dur != null && dur > 0) {
                                _durationMs.value = (dur * 1000).toLong()
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        lastUrl = url
        scope.launch {
            try {
                var cast = client
                if (cast == null || !cast.isConnected) {
                    val dev = activeDevice
                    if (dev != null) {
                        println("[GoogleCast] Reconnecting to ${dev.name} before playing...")
                        connectToDevice(dev)
                        cast = client
                    }
                }
                if (cast == null) {
                    println("[GoogleCast] Play aborted: no active cast device connected")
                    _isPlaying.value = false
                    return@launch
                }
                ensureAppReady(cast)

                println("[GoogleCast] Loading media with rich MUSIC_TRACK metadata: $currentTrackTitle — $currentArtist ($currentAlbum)")
                val media = createMusicMedia(
                    url = url,
                    title = currentTrackTitle,
                    artist = currentArtist,
                    album = currentAlbum,
                    coverUrl = currentCoverUrl
                )
                cast.load(media)
                _isPlaying.value = true
            } catch (t: Throwable) {
                println("[GoogleCast] Play failed: ${t.message}")
                t.printStackTrace()
                _isPlaying.value = false
            }
        }
    }

    override fun pause() {
        scope.launch {
            try {
                client?.pause()
                _isPlaying.value = false
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun resume() {
        scope.launch {
            try {
                var cast = client
                if (cast == null || !cast.isConnected) {
                    val dev = activeDevice
                    if (dev != null) {
                        println("[GoogleCast] Reconnecting to ${dev.name} before resume...")
                        connectToDevice(dev)
                        cast = client
                    }
                }
                if (cast == null) return@launch
                val mediaStatus = runCatching { cast.mediaStatus }.getOrNull()
                val url = lastUrl
                if ((mediaStatus == null || mediaStatus.media == null) && !url.isNullOrBlank()) {
                    play(url)
                } else {
                    cast.play()
                    _isPlaying.value = true
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun stop() {
        scope.launch {
            try {
                client?.stopApp()
                _isPlaying.value = false
                _currentPositionMs.value = 0L
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun seekTo(positionMs: Long) {
        scope.launch {
            try {
                client?.seek(positionMs / 1000.0)
                _currentPositionMs.value = positionMs
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        scope.launch {
            try {
                client?.setVolume(clamped)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun release() {
        disconnect()
    }
}
