package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

/**
 * Configuration constants for the text area.
 */
object TextAreaConfig {
    // Completion settings
    const val MAX_COMPLETION_ITEMS = 10
    const val COMPLETION_ITEM_HEIGHT = 24

    // Indentation
    const val INDENT_SIZE = 4
    const val INDENT_GUIDE_OFFSET = 0.5f
    const val INDENT_GUIDE_ACTIVE_ALPHA = 0.8f
    const val INDENT_GUIDE_INACTIVE_ALPHA = 0.3f

    // Squiggly line (error underline) rendering
    const val SQUIGGLY_STEP = 5
    const val SQUIGGLY_AMPLITUDE = 5
    const val SQUIGGLY_LINE_WIDTH = 3f

    // Scroll settings
    const val SCROLL_WHEEL_X_MULTIPLIER = -20f
    const val SCROLL_WHEEL_Y_MULTIPLIER = -50f
}