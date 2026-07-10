package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlayHint
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextDiagnostic
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextHighlight
import ru.hollowhorizon.hollowengine.client.ui.widgets.withColor
import ru.hollowhorizon.hollowengine.common.scripting.ide.OccurrenceRange

class HollowIdeEditorAdaptersTest {
    private val style = UiInlineStyle()

    @Test
    fun editedLineDropsHighlightsWhileOtherLinesKeepThem() {
        val original = "aa\nbb\ncc"
        val edited = "aa\nbbb\ncc"
        val highlights = listOf(
            UiTextHighlight(0, 2, style),
            UiTextHighlight(3, 5, style),
            UiTextHighlight(6, 8, style),
        )

        val merged = mergeHighlightsForEditedText(original, edited, highlights)

        assertEquals(listOf(UiTextHighlight(0, 2, style), UiTextHighlight(7, 9, style)), merged)
    }

    @Test
    fun insertedNewlineKeepsHighlightsOnFollowingLines() {
        val original = "node {\n    val a = 1\n}\nval b = 2"
        val edited = "node {\n    val a = 1\n}\n\nval b = 2"
        val bStart = original.indexOf("val b")
        val highlights = listOf(
            UiTextHighlight(0, 4, style),
            UiTextHighlight(11, 14, style),
            UiTextHighlight(bStart, bStart + 3, style),
        )

        val merged = mergeHighlightsForEditedText(original, edited, highlights)

        assertEquals(
            listOf(
                UiTextHighlight(0, 4, style),
                UiTextHighlight(11, 14, style),
                UiTextHighlight(bStart + 1, bStart + 4, style),
            ),
            merged,
        )
    }

    @Test
    fun removedLineShiftsFollowingHighlightsBack() {
        val original = "aa\n\nbb"
        val edited = "aa\nbb"
        val highlights = listOf(
            UiTextHighlight(0, 2, style),
            UiTextHighlight(4, 6, style),
        )

        val merged = mergeHighlightsForEditedText(original, edited, highlights)

        assertEquals(listOf(UiTextHighlight(0, 2, style), UiTextHighlight(3, 5, style)), merged)
    }

    @Test
    fun occurrenceOverlaySplitsSpansAndFillsGaps() {
        val colored = UiInlineStyle().withColor(UiColor(1f, 0f, 0f, 1f))
        val highlights = listOf(UiTextHighlight(0, 6, colored))

        val overlaid = overlayOccurrences(10, highlights, listOf(OccurrenceRange(4, 8)))

        assertEquals(3, overlaid.size)
        assertEquals(UiTextHighlight(0, 4, colored), overlaid[0])
        assertEquals(4, overlaid[1].start)
        assertEquals(6, overlaid[1].end)
        assertEquals(UiColor(1f, 0f, 0f, 0.24f), overlaid[1].style.background)
        assertEquals(6, overlaid[2].start)
        assertEquals(8, overlaid[2].end)
        assertNotNull(overlaid[2].style.background)
    }

    @Test
    fun occurrenceOverlayWithoutRangesReturnsSameHighlights() {
        val highlights = listOf(UiTextHighlight(0, 3, style))

        assertSame(highlights, overlayOccurrences(10, highlights, emptyList()))
    }

    @Test
    fun shiftsInlayHintAfterNewlineInsertedAtStart() {
        val hints = listOf(UiInlayHint(offset = 5, text = ": Int"))

        val shifted = shiftInlayHintsForEditedText(
            originalText = "val a = 1",
            editedText = "\nval a = 1",
            inlayHints = hints,
        )

        assertEquals(listOf(UiInlayHint(offset = 6, text = ": Int")), shifted)
    }

    @Test
    fun shiftsInlayHintWhenTextIsInsertedAtAnchor() {
        val hints = listOf(UiInlayHint(offset = 5, text = ": Int"))

        val shifted = shiftInlayHintsForEditedText(
            originalText = "val a = 1",
            editedText = "val abc = 1",
            inlayHints = hints,
        )

        assertEquals(listOf(UiInlayHint(offset = 7, text = ": Int")), shifted)
    }

    @Test
    fun removesInlayHintInsideChangedRange() {
        val hints = listOf(UiInlayHint(offset = 6, text = ": Int"))

        val shifted = shiftInlayHintsForEditedText(
            originalText = "val abc = 1",
            editedText = "val a = 1",
            inlayHints = hints,
        )

        assertEquals(emptyList(), shifted)
    }
    @Test
    fun shiftsDiagnosticsWithoutDroppingThemDuringAnalysis() {
        val diagnostics = listOf(
            UiTextDiagnostic(
                start = 8,
                end = 15,
                message = "Unresolved reference",
                line = 1,
                column = 9,
            ),
        )

        val shifted = shiftDiagnosticsForEditedText(
            originalText = "val a = missing",
            editedText = "\nval a = missing",
            diagnostics = diagnostics,
        )

        assertEquals(1, shifted.size)
        assertEquals(9, shifted.single().start)
        assertEquals(2, shifted.single().line)
        assertEquals(9, shifted.single().column)
    }

    @Test
    fun openFileModelNormalizesLineEndings() {
        val file = HollowIdeOpenFile("test.kts", "first\r\nsecond\rthird")

        assertEquals("first\nsecond\nthird", file.text)
    }
}
