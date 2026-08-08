package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The stylesheets the engine ships are the best available sample of real HSS: if the
 * schema drifts away from what the compiler accepts, they start reporting diagnostics.
 */
class BundledStylesheetDiagnosticsTest {
    private val stylesheets = listOf(
        "docking.hss",
        "ide.hss",
        "image-editor.hss",
        "model-editor.hss",
        "sounds-editor.hss",
        "widgets.hss",
    )

    @Test
    fun `bundled stylesheets report no diagnostics`() {
        for (name in stylesheets) {
            val source = readStylesheet(name)
            val diagnostics = HssScriptingAnalyzer.diagnostic(name, source)
            assertEquals(emptyList(), diagnostics, "$name: $diagnostics")
        }
    }

    @Test
    fun `highlighting a bundled stylesheet keeps its text intact`() {
        for (name in stylesheets) {
            val source = readStylesheet(name)
            val highlighted = HssScriptingAnalyzer.highlight(name, source, 0)
                .joinToString("\n") { line -> line.spans.joinToString("") { it.first } }
            assertEquals(source.replace("\r\n", "\n"), highlighted, name)
        }
    }

    private fun readStylesheet(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/assets/hollowengine/ui/styles/$name")) { name }
            .bufferedReader()
            .use { it.readText() }
            .replace("\r\n", "\n")
}
