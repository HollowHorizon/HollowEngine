package ru.hollowhorizon.hollowengine.client.ui.widgets

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompletionTailLayoutTest {
    @Test
    fun `tail starts at the right edge before the first measurement`() {
        assertTrue(completionTailFitsSeparately(viewportWidth = 0f, leadingWidth = 0f, tailWidth = 0f))
    }

    @Test
    fun `tail stays separate when leading content and gap fit`() {
        assertTrue(completionTailFitsSeparately(viewportWidth = 200f, leadingWidth = 140f, tailWidth = 54f))
    }

    @Test
    fun `tail joins marquee when combined content does not fit`() {
        assertFalse(completionTailFitsSeparately(viewportWidth = 199f, leadingWidth = 140f, tailWidth = 54f))
    }
}
