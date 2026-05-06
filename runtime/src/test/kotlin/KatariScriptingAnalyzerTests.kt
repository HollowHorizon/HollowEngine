import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariScriptingAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `completions include katari context symbols`() {
        val completions = KatariScriptingAnalyzer.completions("test.ktr", "wa", 2)

        assertTrue(completions.any { it.name == "waitDay" || it.name == "wait" })
        assertFalse(completions.any { it.name == "when" || it.name == "with" })
    }

    @Test
    fun `completions include local variables`() {
        val text = "val result = 1\nres"
        val completions = KatariScriptingAnalyzer.completions("locals.ktr", text, text.length)

        assertTrue(completions.any { it.name == "result" && it.tag == CompletionItemTag.LOCAL_VARIABLE })
    }

    @Test
    fun `highlight marks full declared variable name`() {
        val lines = KatariScriptingAnalyzer.highlight("test.ktr", "val result = 1", 0)
        val tokens = lines.flatMap { line -> line.spans.map { it.first to it.second.color } }

        assertTrue(tokens.any { it.first == "result" && it.second == TokenType.PROPERTY_IDENTIFIER })
        assertFalse(tokens.any { it.first == "re" && it.second == TokenType.PROPERTY_IDENTIFIER })
    }

    @Test
    fun `inlay hint starts after declared variable name`() {
        val line = KatariScriptingAnalyzer.highlight("test.ktr", "val result = 1", 0).single()

        assertTrue(line.hints.any { it.index == "val result".length })
    }

    @Test
    fun `highlight marks string template braces`() {
        val line = KatariScriptingAnalyzer.lightweightHighlightLine("test.ktr", "\"Hello \${player.name}\"")
        val tokens = line.spans.map { it.first to it.second.color }

        assertTrue(tokens.any { it.first == "\${" && it.second == TokenType.KEYWORD })
        assertTrue(tokens.any { it.first == "player" && it.second == TokenType.VARIABLE })
        assertTrue(tokens.any { it.first == "name" && it.second == TokenType.FIELD })
        assertTrue(tokens.any { it.first == "}" && it.second == TokenType.KEYWORD })
    }

    @Test
    fun `highlight marks named arguments`() {
        val line = KatariScriptingAnalyzer.highlight("test.ktr", "waitTime(timeOfDay = 1000)", 0).single()
        val tokens = line.spans.map { it.first to it.second.color }

        assertTrue(tokens.any { it.first == "timeOfDay" && it.second == TokenType.VALUE_ARGUMENT_NAME })
    }

    @Test
    fun `highlight marks local variable usages as variables`() {
        val text = "val entity = npc(pos(0.0, 0.0, 0.0))\nentity.move(pos(1.0, 0.0, 0.0))"
        val lines = KatariScriptingAnalyzer.highlight("test.ktr", text, text.indexOf("entity.move"))
        val tokens = lines.flatMap { line -> line.spans.map { it.first to it.second.color } }

        assertTrue(tokens.any { it.first == "entity" && it.second == TokenType.VARIABLE })
    }

    @Test
    fun `highlight marks matching local variable usages at caret`() {
        val text = "val entity = npc(pos(0.0, 0.0, 0.0))\nentity.move(pos(1.0, 0.0, 0.0))"
        val lines = KatariScriptingAnalyzer.highlight("test.ktr", text, text.indexOf("entity.move") + 2)
        val highlighted = lines.flatMap { line -> line.spans.filter { it.first == "entity" && it.second.highlight } }

        assertTrue(highlighted.size >= 2)
    }

    @Test
    fun `member completions include inherited receiver properties`() {
        val completions = KatariScriptingAnalyzer.completions("test.ktr", "player.na", "player.na".length)

        assertTrue(
            completions.any { it.name == "name" && it.tag == CompletionItemTag.PROPERTY },
            completions.joinToString { "${it.name}:${it.tag}" },
        )
    }

    @Test
    fun `top level completions include enum types`() {
        val completions = KatariScriptingAnalyzer.completions("test.ktr", "AnimationPlay", "AnimationPlay".length)

        assertTrue(completions.any { it.name == "AnimationPlayMode" && it.tag == CompletionItemTag.CLASS })
    }

    @Test
    fun `enum receiver completions include entries`() {
        val completions = KatariScriptingAnalyzer.completions("test.ktr", "AnimationPlayMode.Lo", "AnimationPlayMode.Lo".length)

        assertTrue(completions.any { it.name == "Loop" && it.tag == CompletionItemTag.PROPERTY })
    }

    @Test
    fun `diagnostic accepts editor context globals`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic("test.ktr", "player.name")

        assertEquals(emptyList(), diagnostics)
    }
}
