package dev.foldman.desktop.audio

import dev.foldman.audio.AudioBackend
import dev.foldman.desktop.device.AudioOutputDevice
import dev.foldman.desktop.device.DeviceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesktopAudioRouter(
    val localBackend: JavaFxAudioBackend = JavaFxAudioBackend(),
    val castBackend: GoogleCastAudioBackend = GoogleCastAudioBackend(),
) : AudioBackend {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeTarget = MutableStateFlow(DeviceType.MAC)
    val activeTarget: StateFlow<DeviceType> = _activeTarget.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var lastUrl: String? = null
    private var lastTrackTitle: String = ""
    private var lastTrackArtist: String = ""
    private var lastAlbumTitle: String = ""
    private var lastCoverUrl: String? = null

    init {
        // Synchronize state flows from the active backend to the UI
        scope.launch {
            localBackend.isPlaying.collect {
                if (_activeTarget.value != DeviceType.GOOGLE_CAST) _isPlaying.value = it
            }
        }
        scope.launch {
            castBackend.isPlaying.collect {
                if (_activeTarget.value == DeviceType.GOOGLE_CAST) _isPlaying.value = it
            }
        }
        scope.launch {
            localBackend.currentPositionMs.collect {
                if (_activeTarget.value != DeviceType.GOOGLE_CAST) _currentPositionMs.value = it
            }
        }
        scope.launch {
            castBackend.currentPositionMs.collect {
                if (_activeTarget.value == DeviceType.GOOGLE_CAST) _currentPositionMs.value = it
            }
        }
        scope.launch {
            localBackend.durationMs.collect {
                if (_activeTarget.value != DeviceType.GOOGLE_CAST) _durationMs.value = it
            }
        }
        scope.launch {
            castBackend.durationMs.collect {
                if (_activeTarget.value == DeviceType.GOOGLE_CAST) _durationMs.value = it
            }
        }
        scope.launch {
            localBackend.volume.collect {
                if (_activeTarget.value != DeviceType.GOOGLE_CAST) _volume.value = it
            }
        }
        scope.launch {
            castBackend.volume.collect {
                if (_activeTarget.value == DeviceType.GOOGLE_CAST) _volume.value = it
            }
        }
    }

    fun setTrackInfo(title: String, artist: String, album: String, coverUrl: String?) {
        lastTrackTitle = title
        lastTrackArtist = artist
        lastAlbumTitle = album
        lastCoverUrl = coverUrl
        castBackend.setTrackMetadata(title, artist, album, coverUrl)
    }

    suspend fun switchDevice(device: AudioOutputDevice) {
        val wasPlaying = _isPlaying.value
        val pos = _currentPositionMs.value
        val url = lastUrl

        println("[DesktopAudioRouter] Switching output device to ${device.name} (${device.type})")

        if (device.type == DeviceType.GOOGLE_CAST) {
            _activeTarget.value = DeviceType.GOOGLE_CAST
            localBackend.stop()

            val connected = castBackend.connectToDevice(device)
            if (connected) {
                castBackend.setVolume(_volume.value)
                if (wasPlaying && !url.isNullOrBlank()) {
                    delay(300)
                    castBackend.play(url)
                    if (pos > 1000) {
                        delay(600)
                        castBackend.seekTo(pos)
                    }
                }
            }
        } else {
            // Revert to Mac Built-in or Bluetooth
            _activeTarget.value = device.type
            castBackend.disconnect()

            if (wasPlaying && !url.isNullOrBlank()) {
                localBackend.play(url)
                if (pos > 0) {
                    localBackend.seekTo(pos)
                }
            }
        }
    }

    override fun play(url: String, headers: Map<String, String>) {
        lastUrl = url
        if (_activeTarget.value == DeviceType.GOOGLE_CAST) {
            localBackend.stop()
            castBackend.play(url, headers)
        } else {
            localBackend.play(url, headers)
        }
    }

    override fun pause() {
        if (_activeTarget.value == DeviceType.GOOGLE_CAST) {
            castBackend.pause()
        } else {
            localBackend.pause()
        }
    }

    override fun resume() {
        if (_activeTarget.value == DeviceType.GOOGLE_CAST) {
            castBackend.resume()
        } else {
            localBackend.resume()
        }
    }

    override fun stop() {
        if (_activeTarget.value == DeviceType.GOOGLE_CAST) {
            castBackend.stop()
        } else {
            localBackend.stop()
        }
    }

    override fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        if (_activeTarget.value == DeviceType.GOOGLE_CAST) {
            castBackend.seekTo(positionMs)
        } else {
            localBackend.seekTo(positionMs)
        }
    }

    override fun setVolume(volume: Float) {
        _volume.value = volume
        localBackend.setVolume(volume)
        castBackend.setVolume(volume)
    }

    override fun release() {
        localBackend.release()
        castBackend.release()
    }
}
