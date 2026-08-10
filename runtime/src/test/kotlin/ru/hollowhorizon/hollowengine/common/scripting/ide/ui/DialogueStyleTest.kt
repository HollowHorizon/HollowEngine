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
    private val source =
        requireNotNull(javaClass.getResourceAsStream("/assets/hollowengine/ui/styles/dialogue.hss"))
            .bufferedReader()
            .use { it.readText() }

    @Test
    fun `the dialogue stylesheet has no problems the editor would flag`() {
        val problems = hssDiagnostics(source).joinToString("\n") { diagnostic ->
            "line ${diagnostic.range.start.line + 1}: ${diagnostic.message}"
        }

        assertEquals("", problems)
    }

    @Test
    fun `it compiles into rules the engine can apply`() {
        assertTrue(compileHss(source).rules.isNotEmpty())
    }
}
