package dev.foldman.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object MechSynth {
    const val SAMPLE_RATE = 44100

    /** Crisp 2ms microswitch snap + 14ms delayed solenoid engagement thump. */
    fun synthLogicDown(): ShortArray {
        val seconds = 0.055f
        val n = (SAMPLE_RATE * seconds).toInt()
        val rnd = Random(3)
        var prevNoise = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
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

    fun synth(seconds: Float, thump: Float, ring: Float, click: Float, gain: Float): ShortArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        val rnd = Random(3)
        var prevNoise = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            val white = rnd.nextDouble(-1.0, 1.0)
            val noise = (white - prevNoise) * 0.5
            prevNoise = white
            val s = click * noise * exp(-t / 0.0022) +
                0.9 * sin(2 * PI * thump * t) * exp(-t / 0.014) +
                0.25 * sin(2 * PI * ring * t) * exp(-t / 0.005)
            (s * gain * 32767 * 0.6).toInt().coerceIn(-32767, 32767).toShort()
        }
    }

    /** Helper to convert 16-bit PCM ShortArray to little-endian ByteArray */
    fun pcmToBytes(pcm: ShortArray): ByteArray {
        val bytes = ByteArray(pcm.size * 2)
        for (i in pcm.indices) {
            val v = pcm[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v ushr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
