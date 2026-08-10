package ru.hollowhorizon.hollowengine.common.ui

/**
 * The GUI scale a scripted screen draws at.
 *
 * A screen designed around a fixed layout does not want to follow the player's own setting: at
 * scale 4 a dialogue window meant to span half the screen ends up filling it. Declaring a scale
 * makes the surface resolve its own logical size, leaving the rest of the game alone.
 */
sealed interface UiGuiScale {
    /** Follow the player's video setting. */
    data object Inherit : UiGuiScale

    /** Vanilla's "Auto": the largest whole factor that still fits the window. */
    data object Auto : UiGuiScale

    /** A specific factor, clamped down the same way vanilla clamps its own setting. */
    data class Fixed(val factor: Int) : UiGuiScale {
        init {
            require(factor >= 1) { "A GUI scale factor must be at least 1" }
        }
    }

    companion object {
        /** `0` means auto, matching how vanilla stores the option. */
        fun of(factor: Int): UiGuiScale = if (factor <= 0) Auto else Fixed(factor)
    }
}
