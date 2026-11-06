package dev.foldman.desktop.audio

import dev.foldman.audio.MechSynth
import dev.foldman.ui.MechSoundFeedback
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/**
 * Mechanical WM-RX707 noises for Desktop using standard JVM javax.sound.sampled.
 */
class DesktopMechSounds : MechSoundFeedback {
    var enabled: Boolean = true

    private val format = AudioFormat(
        MechSynth.SAMPLE_RATE.toFloat(),
        16, // bit depth
        1,  // mono
        true, // signed
        false // little-endian
    )

    private val latch = createClip(MechSynth.synthLogicDown())
    private val release = createClip(MechSynth.synth(0.022f, thump = 240f, ring = 4200f, click = 0.55f, gain = 0.28f))
    private val thunk = createClip(MechSynth.synth(0.085f, thump = 72f, ring = 1450f, click = 0.65f, gain = 0.68f))

    override fun keyDown() { if (enabled) play(latch) }
    override fun keyUp() { if (enabled) play(release) }
    override fun autoStop() { if (enabled) play(thunk) }

    fun play() { keyDown() }
    fun pause() { keyUp() }
    fun stop() { autoStop() }
    fun rewind() { keyDown() }
    fun fastForward() { keyDown() }
    fun switchFlip() { keyUp() }
    fun solenoid() { autoStop() }

    private fun play(clip: Clip?) {
        clip ?: return
        runCatching {
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }
    }

    private fun createClip(pcm: ShortArray): Clip? = runCatching {
        val bytes = MechSynth.pcmToBytes(pcm)
        val clip = AudioSystem.getClip()
        clip.open(format, bytes, 0, bytes.size)
        clip
    }.getOrNull()

    fun release() {
        listOf(latch, release, thunk).forEach { it?.close() }
    }
}
