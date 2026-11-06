package dev.foldman.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent audio playback backend (Media3/ExoPlayer on Android,
 * JavaFX Media / VLC on Desktop).
 */
interface AudioBackend {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val volume: StateFlow<Float>

    fun play(url: String, headers: Map<String, String> = emptyMap())
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun release()
}
