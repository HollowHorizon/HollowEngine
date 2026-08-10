package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.style.compileHss
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dialogue window is styled entirely from a stylesheet, so a typo in a property name would
 * silently drop part of the look with nothing to catch it at runtime. The IDE's own analyzer knows
 * every property and value form, which makes it the right thing to hold the shipped sheet against.
 */
class DialogueStyleTest {
    private fun sheet(path: String) = requireNotNull(javaClass.getResourceAsStream(path))
        .bufferedReader()
        .use { it.readText() }

    private fun problems(source: String) = hssDiagnostics(source).joinToString("\n") { diagnostic ->
        "line ${diagnostic.range.start.line + 1}: ${diagnostic.message}"
    }

    @Test
    fun `the dialogue stylesheet has no problems the editor would flag`() {
        val source = sheet("/assets/hollowengine/ui/styles/dialogue.hss")

        assertEquals("", problems(source))
        assertTrue(compileHss(source).rules.isNotEmpty(), "it compiles into rules the engine can apply")
    }

    @Test
    fun `the overlay dialogue example is as clean as the built-in one`() {
        val source = sheet("/assets/hollowengine/ui/examples/dialogue_overlay.hss")

        assertEquals("", problems(source))
        assertTrue(compileHss(source).rules.isNotEmpty(), "it compiles into rules the engine can apply")
    }
}
