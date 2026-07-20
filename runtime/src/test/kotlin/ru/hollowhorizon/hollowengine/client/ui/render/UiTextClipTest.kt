package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.style.UiTextOverflow
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiTextClipTest {
    @Test
    fun `fitting hidden text does not create a clip barrier`() {
        assertFalse(requiresTextClip(UiTextOverflow.HIDDEN, 80f, 20f, 100f, 30f, 0f, 0f))
    }

    @Test
    fun `overflowing or scrolled text keeps its clip`() {
        assertTrue(requiresTextClip(UiTextOverflow.HIDDEN, 120f, 20f, 100f, 30f, 0f, 0f))
        assertTrue(requiresTextClip(UiTextOverflow.HIDDEN, 80f, 40f, 100f, 30f, 0f, 0f))
        assertTrue(requiresTextClip(UiTextOverflow.HIDDEN, 80f, 20f, 100f, 30f, 1f, 0f))
    }

    @Test
    fun `show overflow never clips`() {
        assertFalse(requiresTextClip(UiTextOverflow.SHOW, 120f, 40f, 100f, 30f, 1f, 1f))
    }
}
