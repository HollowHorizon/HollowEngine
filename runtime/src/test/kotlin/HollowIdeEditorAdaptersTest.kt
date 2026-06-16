package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.hollowhorizon.hollowengine.client.ui.UiInlayHint

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
}
