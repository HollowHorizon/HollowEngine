package ru.hollowhorizon.hollowengine.client.ui.screen

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The demo stylesheet is only compiled when the screen opens, so a typo in it would otherwise
 * surface as a broken tab in game. Compiling it here turns that into a build failure.
 */
class DemoStylesheetTest {
    @Test
    fun `the demo stylesheet compiles`() {
        assertTrue(DemoStyles.rules.isNotEmpty(), "the stylesheet produced no rules")
    }

    @Test
    fun `the text lab's classes are all styled`() {
        val selectors = DemoStyles.rules.map { it.selector.toString() }.toSet()
        val required = listOf(
            "textlab-catalog", "textlab-effect-row", "textlab-effect-name", "textlab-params",
            "textlab-effect-preview", "textlab-field", "textlab-label", "textlab-input",
            "textlab-sample-field", "textlab-stage", "textlab-handle-h", "textlab-table",
            "textlab-row", "textlab-head", "textlab-cell-name", "textlab-cell", "textlab-mixed",
        )
        val missing = required.filter { name -> selectors.none { it.contains(name) } }
        assertTrue(missing.isEmpty(), "unstyled text-lab classes: $missing")
    }
}
