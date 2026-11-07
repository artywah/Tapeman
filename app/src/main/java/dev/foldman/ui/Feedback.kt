package dev.foldman.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Mechanical tactile sound feedback interface (key press clicks, solenoid release thumps).
 */
interface MechSoundFeedback {
    fun keyDown()
    fun keyUp()
    fun autoStop()
}

/**
 * Multiplatform haptic feedback interface (implemented via Android View or no-op on Desktop).
 */
interface DeckHaptics {
    fun tick()
    fun tap()
    fun longPress()
}

val LocalMechSounds = staticCompositionLocalOf<MechSoundFeedback?> { null }
val LocalDeckHaptics = staticCompositionLocalOf<DeckHaptics?> { null }
val LocalHaptics = staticCompositionLocalOf { false }
