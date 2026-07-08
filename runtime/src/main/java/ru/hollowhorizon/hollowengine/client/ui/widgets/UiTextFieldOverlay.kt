package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.text.caretRect
import ru.hollowhorizon.hollowengine.client.ui.text.selectionRects

/**
 * Content-space rectangles for a text field's overlays: one caret bar per range and a run of
 * highlight rectangles per selection. Both are pure functions of the laid-out text plus the edit
 * state, so the caret does not shift the glyphs and the post-layout pass can turn them into overlay
 * nodes drawn over (caret) and under (selection) the spans.
 */
data class TextFieldOverlayGeometry(
    val carets: List<UiRect>,
    val selections: List<UiRect>,
)

fun textFieldOverlayGeometry(
    layout: UiTextLayout,
    caretRanges: List<UiTextCaret>,
    fontSize: Float,
    fontFamily: String? = null,
    caretThickness: Float = 1f,
): TextFieldOverlayGeometry {
    val carets = caretRanges.map { layout.caretRect(it.position, fontSize, fontFamily, caretThickness) }
    val selections = caretRanges
        .filter { it.hasSelection }
        .flatMap { range ->
            layout.selectionRects(range.selectionStart, range.selectionEnd, fontSize, fontFamily, fillLineGaps = true)
        }
    return TextFieldOverlayGeometry(carets, selections)
}
