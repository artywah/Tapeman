package dev.foldman.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthesised Sony WM-RX707 mechanism noises:
 *  - [keyDown]: crisp feather-touch electronic logic microswitch click followed by a subtle solenoid pull-in thump.
 *  - [keyUp]: light tactile microswitch release tick.
 *  - [autoStop]: mechanical solenoid latch release thunk.
 */
class MechSounds {
    private val rate = 44100
    private val latch = track(synthLogicDown())
    private val release = track(synth(0.022f, thump = 240f, ring = 4200f, click = 0.55f, gain = 0.28f))
    private val thunk = track(synth(0.085f, thump = 72f, ring = 1450f, click = 0.65f, gain = 0.68f))

    fun keyDown() = play(latch)
    fun keyUp() = play(release)
    fun autoStop() = play(thunk)

    private fun play(t: AudioTrack?) {
        t ?: return
        runCatching {
            t.stop()
            t.reloadStaticData()
            t.play()
        }
    }

    /** Crisp 2ms microswitch snap + 14ms delayed solenoid engagement thump. */
    private fun synthLogicDown(): ShortArray {
        val seconds = 0.055f
        val n = (rate * seconds).toInt()
        val rnd = Random(3)
        var prevNoise = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val white = rnd.nextDouble(-1.0, 1.0)
            val noise = (white - prevNoise) * 0.5
            prevNoise = white
            // Crisp tactile dome click at t=0
            val microClick = 0.95 * noise * exp(-t / 0.0014) +
                0.45 * sin(2 * PI * 3600.0 * t) * exp(-t / 0.0025)
            // Subtle solenoid transport engagement at t=14ms
            val dt = (t - 0.014).coerceAtLeast(0.0)
            val solenoid = if (t >= 0.014) {
                0.75 * sin(2 * PI * 105.0 * dt) * exp(-dt / 0.011) +
                    0.22 * noise * exp(-dt / 0.002)
            } else 0.0
            ((microClick + solenoid) * 0.55 * 32767 * 0.65).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun synth(seconds: Float, thump: Float, ring: Float, click: Float, gain: Float): ShortArray {
        val n = (rate * seconds).toInt()
        val rnd = Random(3)
        var prevNoise = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val white = rnd.nextDouble(-1.0, 1.0)
            val noise = (white - prevNoise) * 0.5
            prevNoise = white
            val s = click * noise * exp(-t / 0.0022) +
                0.9 * sin(2 * PI * thump * t) * exp(-t / 0.014) +
                0.25 * sin(2 * PI * ring * t) * exp(-t / 0.005)
            (s * gain * 32767 * 0.6).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    private fun track(pcm: ShortArray): AudioTrack? = runCatching {
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }.getOrNull()

    fun release() {
        listOf(latch, release, thunk).forEach { it?.release() }
    }
}

val LocalMechSounds = staticCompositionLocalOf<MechSounds?> { null }
