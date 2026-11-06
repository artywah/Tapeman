package dev.foldman.desktop.audio

import dev.foldman.audio.AudioBackend
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class JavaFxAudioBackend : AudioBackend {
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null

    companion object {
        private val initialized = AtomicBoolean(false)
        fun initPlatform() {
            if (initialized.compareAndSet(false, true)) {
                try {
                    Platform.startup {}
                } catch (e: IllegalStateException) {
                    // Already initialized
                }
            }
        }
    }

    init {
        initPlatform()
    }

    override fun play(url: String, headers: Map<String, String>) {
        Platform.runLater {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.dispose()

                val media = Media(url)
                val player = MediaPlayer(media).apply {
                    volume = _volume.value.toDouble()
                    setOnReady {
                        _durationMs.value = media.duration.toMillis().toLong()
                        play()
                    }
                    setOnPlaying { _isPlaying.value = true }
                    setOnPaused { _isPlaying.value = false }
                    setOnStopped { _isPlaying.value = false }
                    setOnEndOfMedia {
                        _isPlaying.value = false
                        _currentPositionMs.value = _durationMs.value
                    }
                    currentTimeProperty().addListener { _, _, newTime ->
                        _currentPositionMs.value = newTime.toMillis().toLong()
                    }
                }
                mediaPlayer = player
            } catch (t: Throwable) {
                t.printStackTrace()
                _isPlaying.value = false
            }
        }
    }

    override fun pause() {
        Platform.runLater {
            mediaPlayer?.pause()
            _isPlaying.value = false
        }
    }

    override fun resume() {
        Platform.runLater {
            mediaPlayer?.play()
            _isPlaying.value = true
        }
    }

    override fun stop() {
        Platform.runLater {
            mediaPlayer?.stop()
            _isPlaying.value = false
            _currentPositionMs.value = 0L
        }
    }

    override fun seekTo(positionMs: Long) {
        Platform.runLater {
            mediaPlayer?.seek(Duration.millis(positionMs.toDouble()))
            _currentPositionMs.value = positionMs
        }
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        Platform.runLater {
            mediaPlayer?.volume = clamped.toDouble()
        }
    }

    override fun release() {
        Platform.runLater {
            mediaPlayer?.stop()
            mediaPlayer?.dispose()
            mediaPlayer = null
            _isPlaying.value = false
        }
    }
}
