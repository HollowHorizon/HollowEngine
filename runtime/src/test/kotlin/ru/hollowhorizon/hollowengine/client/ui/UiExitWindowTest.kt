package ru.hollowhorizon.hollowengine.client.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The window has to end on the same frame the exit animation does. It starts when the host first
 * draws itself closing, because that is when the stylesheet's animation starts - measuring from the
 * close request instead leaves the host alive after the animation is over, and one without
 * `forwards`/`both` is back at the resting style by then, in full view of the player.
 */
class UiExitWindowTest {
    @Test
    fun `a host with no exit duration is not worth waiting for`() {
        val window = UiExitWindow(0L)
        assertFalse(window.begin(1_000L), "the caller should close it outright")
        assertFalse(window.isClosing)
    }

    @Test
    fun `the clock runs from the first drawn frame`() {
        val window = UiExitWindow(400L)
        window.begin(1_000L)
        assertTrue(window.isClosing)

        window.markDrawn(1_050L)
        window.markDrawn(1_066L)

        assertFalse(window.isFinished(1_400L), "400ms after the request, but only 350ms of animation")
        assertTrue(window.isFinished(1_450L), "the full duration from the frame the animation started on")
    }

    @Test
    fun `a host that never draws still expires`() {
        val window = UiExitWindow(400L)
        window.begin(1_000L)

        assertFalse(window.isFinished(1_399L))
        assertTrue(window.isFinished(1_400L), "an overlay behind a screen must not hang around forever")
    }
}
