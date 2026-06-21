package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.hollowhorizon.hollowengine.client.ui.UiInlayHint
import ru.hollowhorizon.hollowengine.client.ui.UiTextDiagnostic

class HollowIdeEditorAdaptersTest {
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
