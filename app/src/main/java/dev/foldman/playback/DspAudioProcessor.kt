package dev.foldman.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Sony Walkman WM-RX707 hardware audio processing:
 *  - **EX DBB (Extended Dynamic Bass Boost)**: warm analog low-shelf boost (+6.5 dB @ 72 Hz) with soft tape saturation.
 *  - **AVLS (Automatic Volume Limiter System)**: smooth peak envelope limiter capping loud passages at -6 dBFS.
 */
@OptIn(UnstableApi::class)
object DspAudioProcessor : AudioProcessor {
    @Volatile var dbbEnabled: Boolean = false
    @Volatile var avlsEnabled: Boolean = false

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    // Stereo biquad state for EX DBB low-shelf filter
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1L = 0.0; private var x2L = 0.0; private var y1L = 0.0; private var y2L = 0.0
    private var x1R = 0.0; private var x2R = 0.0; private var y1R = 0.0; private var y2R = 0.0

    // AVLS envelope follower gain
    private var limiterGain = 1.0

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        computeLowShelfCoeffs(inputAudioFormat.sampleRate.toDouble(), freqHz = 72.0, gainDb = 6.5)
        return outputAudioFormat
    }

    private fun computeLowShelfCoeffs(sampleRate: Double, freqHz: Double, gainDb: Double) {
        if (sampleRate <= 0) return
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freqHz / sampleRate
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val alpha = sinW0 / 2.0 * sqrt(2.0) // Q = 0.7071
        val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

        val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
        b0 = (a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)) / a0
        b1 = (2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)) / a0
        b2 = (a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)) / a0
        a1 = (-2.0 * ((a - 1.0) + (a + 1.0) * cosW0)) / a0
        a2 = ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha) / a0
    }

    override fun isActive(): Boolean = outputAudioFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val useDbb = dbbEnabled
        val useAvls = avlsEnabled
        if (!useDbb && !useAvls) {
            if (buffer.capacity() < remaining) {
                buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            } else {
                buffer.clear()
            }
            buffer.put(inputBuffer)
            buffer.flip()
            outputBuffer = buffer
            return
        }

        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val inOrder = inputBuffer.order()
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val threshold = 0.48 // ~ -6.4 dBFS ceiling for AVLS
        while (inputBuffer.remaining() >= channels * 2) {
            var sL = inputBuffer.short / 32768.0
            var sR = if (channels > 1) inputBuffer.short / 32768.0 else sL

            if (useDbb) {
                val outL = b0 * sL + b1 * x1L + b2 * x2L - a1 * y1L - a2 * y2L
                x2L = x1L; x1L = sL; y2L = y1L; y1L = outL

                val outR = if (channels > 1) {
                    val r = b0 * sR + b1 * x1R + b2 * x2R - a1 * y1R - a2 * y2R
                    x2R = x1R; x1R = sR; y2R = y1R; y1R = r
                    r
                } else outL

                // Gentle analog tape saturation to keep boosted bass warm and clip-free
                sL = tanh(outL * 0.92)
                sR = tanh(outR * 0.92)
            }

            if (useAvls) {
                val peak = maxOf(abs(sL), abs(sR))
                val targetGain = if (peak > threshold) threshold / peak else 1.0
                val coeff = if (targetGain < limiterGain) 0.08 else 0.0015
                limiterGain += (targetGain - limiterGain) * coeff
                sL *= limiterGain
                sR *= limiterGain
            }

            val outShortL = (sL * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            buffer.putShort(outShortL)
            if (channels > 1) {
                val outShortR = (sR * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                buffer.putShort(outShortR)
            }
            for (c in 2 until channels) {
                val extra = inputBuffer.short
                buffer.putShort(extra)
            }
        }
        while (inputBuffer.hasRemaining()) {
            buffer.put(inputBuffer.get())
        }
        inputBuffer.order(inOrder)
        buffer.flip()
        outputBuffer = buffer
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        x1L = 0.0; x2L = 0.0; y1L = 0.0; y2L = 0.0
        x1R = 0.0; x2R = 0.0; y1R = 0.0; y2R = 0.0
        limiterGain = 1.0
    }

    override fun reset() {
        flush()
        buffer = AudioProcessor.EMPTY_BUFFER
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }
}
