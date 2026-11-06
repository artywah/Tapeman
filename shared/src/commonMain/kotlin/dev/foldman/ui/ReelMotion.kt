package dev.foldman.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import dev.foldman.deck.Transport
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The tape transport as a little physics model. The tape moves at a constant linear speed, so each
 * reel turns at speed / radius: the nearly empty reel spins fast, the full one slowly, and the two
 * trade places as the song plays. The reels spin up and coast down rather than snapping, and the
 * counter is geared to the take-up reel the way a real one is.
 *
 * Angles are in degrees, clockwise positive (Compose's convention). In play, tape runs left to right
 * across the heads, which turns both reels counter-clockwise.
 */
@Stable
class ReelMotion {
    var leftAngle by mutableFloatStateOf(0f)
        private set
    var rightAngle by mutableFloatStateOf(0f)
        private set
    /** Degrees each reel moved in the last frame, for motion blur. */
    var leftDelta by mutableFloatStateOf(0f)
        private set
    var rightDelta by mutableFloatStateOf(0f)
        private set
    /** Tape position the packs are drawn at; eases towards the real one when the song changes. */
    var shownProgress by mutableFloatStateOf(0f)
        private set
    /** Mechanical counter reading, in counter units. */
    var counter by mutableDoubleStateOf(0.0)
        private set
    /** Linear tape speed in mm/s; positive is towards the take-up reel. */
    var tapeSpeed by mutableFloatStateOf(0f)
        private set

    val leftRadius: Float get() = packRadius(1f - shownProgress)
    val rightRadius: Float get() = packRadius(shownProgress)

    fun resetCounter() {
        counter = 0.0
    }

    internal fun step(dt: Float, transport: Transport, progress: Float) {
        val target = when (transport) {
            Transport.PLAYING -> PLAY_SPEED
            Transport.FAST_FORWARDING -> PLAY_SPEED * WIND_VISUAL
            Transport.REWINDING -> -PLAY_SPEED * WIND_VISUAL
            Transport.PAUSED, Transport.STOPPED -> 0f
        }
        // Motors bite quickly at play speed; winding has more mass to get going and to stop.
        val tau = when {
            abs(target) > abs(tapeSpeed) -> if (abs(target) > PLAY_SPEED) 0.45f else 0.09f
            abs(tapeSpeed) > PLAY_SPEED * 1.5f -> 0.28f
            else -> 0.05f
        }
        tapeSpeed += (target - tapeSpeed) * (1f - exp(-dt / tau))
        if (target == 0f && abs(tapeSpeed) < 0.4f) tapeSpeed = 0f

        val gap = progress - shownProgress
        shownProgress = if (abs(gap) < 0.004f) progress else shownProgress + gap * (1f - exp(-dt / 0.35f))

        // ω = v / r, in degrees per second; negative because forward motion is counter-clockwise.
        val dl = -tapeSpeed / leftRadius * RAD_TO_DEG * dt
        val dr = -tapeSpeed / rightRadius * RAD_TO_DEG * dt
        leftAngle = (leftAngle + dl) % 360f
        rightAngle = (rightAngle + dr) % 360f
        leftDelta = dl
        rightDelta = dr
        counter += -dr / 360.0 * COUNTER_RATIO
    }

    internal val isAtRest: Boolean get() = tapeSpeed == 0f

    companion object {
        /** Hub (empty) and full pack radii, in cassette millimetres. */
        const val R_EMPTY = 9.8f
        const val R_FULL = 21.0f
        /** 4.76 cm/s, the Compact Cassette standard. */
        const val PLAY_SPEED = 47.6f
        /** How much faster the reels look while winding (kept below the wagon-wheel limit). */
        const val WIND_VISUAL = 6f
        private const val COUNTER_RATIO = 0.5
        private const val RAD_TO_DEG = 57.29578f

        /** Tape area is conserved, so radius goes with the square root of the fraction wound on. */
        fun packRadius(fraction: Float): Float =
            sqrt(R_EMPTY * R_EMPTY + fraction.coerceIn(0f, 1f) * (R_FULL * R_FULL - R_EMPTY * R_EMPTY))
    }
}

/** Animation state that has to survive the layout changing when the phone folds or unfolds. */
@Stable
class DeckAnimations {
    val reels = ReelMotion()
    /** 1 = cassette out of the bay, 0 = seated. */
    val insert = Animatable(1f)
    /** Short hop when STOP/EJECT is pressed with the tape already stopped. */
    val pop = Animatable(0f)
    var ejectCount by mutableStateOf(0)
}

@Composable
fun rememberDeckAnimations(transport: Transport, progress: Float): DeckAnimations {
    val anim = remember { DeckAnimations() }
    val currentTransport by rememberUpdatedState(transport)
    val currentProgress by rememberUpdatedState(progress)
    LaunchedEffect(anim) {
        var last = 0L
        while (true) {
            val reels = anim.reels
            if (reels.isAtRest && currentTransport.isMoving.not() &&
                abs(reels.shownProgress - currentProgress) < 0.004f
            ) {
                // Nothing turning: sleep until the tape is asked to move or the song changes.
                snapshotFlow { currentTransport.isMoving || abs(reels.shownProgress - currentProgress) >= 0.004f }.first { it }
                last = 0L
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                reels.step(dt, currentTransport, currentProgress)
            }
        }
    }
    return anim
}

val Transport.isMoving: Boolean
    get() = this == Transport.PLAYING || this == Transport.REWINDING || this == Transport.FAST_FORWARDING
