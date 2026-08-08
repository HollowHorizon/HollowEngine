package ru.hollowhorizon.hollowengine.client.ui.widgets

/**
 * Inlays reserve the room the layout measured for them, so a test that cares about that
 * room says how big the hint drew instead of relying on a guessed size.
 */
internal fun measuredInlays(vararg hints: UiInlayHint, width: Float, height: Float): EditableFieldInlayMetrics =
    EditableFieldInlayMetrics().apply {
        for (hint in hints) record(hint, width, height)
    }
