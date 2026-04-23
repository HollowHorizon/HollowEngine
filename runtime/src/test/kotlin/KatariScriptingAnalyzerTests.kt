import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariScriptingAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KatariScriptingAnalyzerTests {
    @Test
    fun `highlight marks katari keywords strings and numbers`() {
        val lines = KatariScriptingAnalyzer.highlight(
            "test.ktr",
            """
                checkpoint start
                "Hello"
                wait(40)
            """.trimIndent(),
            0,
        )

        val tokens = lines.flatMap { line -> line.spans.map { it.first to it.second.color } }
        assertTrue(tokens.any { it.first == "checkpoint" && it.second == TokenType.KEYWORD })
        assertTrue(tokens.any { it.first == "\"Hello\"" && it.second == TokenType.STRING })
        assertTrue(tokens.any { it.first == "40" && it.second == TokenType.NUMERIC_LITERAL })
        assertTrue(tokens.any { it.first == "wait" && it.second == TokenType.FUNCTION })
    }

    @Test
    fun `diagnostic reports invalid katari syntax`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic("broken.ktr", "val answer =")

        assertTrue(diagnostics.isNotEmpty())
        assertTrue(diagnostics.any { it.severity.isError() })
    }

    @Test
    fun `completions stay disabled until katari has context aware provider`() {
        val completions = KatariScriptingAnalyzer.completions("test.ktr", "wa", 2)

        assertEquals(emptyList(), completions)
    }
}
