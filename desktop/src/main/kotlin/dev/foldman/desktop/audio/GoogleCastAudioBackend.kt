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
import su.litvak.chromecast.api.v2.MediaStatus

class GoogleCastAudioBackend : AudioBackend {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var client: ChromeCast? = null
    private var pollJob: Job? = null
    private var activeDevice: AudioOutputDevice? = null

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

    suspend fun connectToDevice(device: AudioOutputDevice): Boolean = withContext(Dispatchers.IO) {
        val addr = device.address
        if (addr.isNullOrBlank()) {
            println("[GoogleCast] Cannot connect: device address is null for ${device.name}")
            return@withContext false
        }

        try {
            disconnect()
            println("[GoogleCast] Connecting to ${device.name} at $addr:${device.port}...")
            val cast = ChromeCast(addr, device.port)
            cast.connect()

            // Launch Default Media Receiver app on screen
            println("[GoogleCast] Launching Default Media Receiver (CC1AD845)...")
            try {
                if (!cast.isAppRunning("CC1AD845")) {
                    cast.launchApp("CC1AD845")
                }
            } catch (e: Throwable) {
                // Try launching anyway
                cast.launchApp("CC1AD845")
            }

            client = cast
            activeDevice = device
            startPolling()
            println("[GoogleCast] Connected successfully to ${device.name}!")
            true
        } catch (t: Throwable) {
            println("[GoogleCast] Connection failed: ${t.message}")
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
        scope.launch {
            try {
                val cast = client ?: return@launch
                val title = if (currentArtist.isNotBlank()) "$currentTrackTitle — $currentArtist" else currentTrackTitle
                val thumb = currentCoverUrl ?: "https://raw.githubusercontent.com/artywah/Tapeman/main/art/icon.png"
                val contentType = when {
                    url.contains(".flac", ignoreCase = true) -> "audio/flac"
                    url.contains(".m4a", ignoreCase = true) || url.contains(".aac", ignoreCase = true) -> "audio/mp4"
                    url.contains(".ogg", ignoreCase = true) -> "audio/ogg"
                    url.contains(".wav", ignoreCase = true) -> "audio/wav"
                    else -> "audio/mp3"
                }

                println("[GoogleCast] Loading media: $title, URL: $url")
                if (!cast.isAppRunning("CC1AD845")) {
                    cast.launchApp("CC1AD845")
                    delay(500)
                }

                cast.load(
                    title,
                    thumb,
                    url,
                    contentType
                )
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
                client?.play()
                _isPlaying.value = true
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
