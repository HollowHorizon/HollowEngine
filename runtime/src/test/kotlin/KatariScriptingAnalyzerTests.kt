import com.sunnychung.lib.multiplatform.kotlite.katari.analyzeKatariNarrativeProgram
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
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
    fun `member completions include generic saved variable bindings`() {
        val completions = KatariScriptingAnalyzer.completions("test.ktr", "server.getOr", "server.getOr".length)
        val completion = completions.firstOrNull { it.name == "getOrCreate" } as? CompletionItem.Declaration
            ?: error(completions.joinToString { "${it.name}:${it.tag}" })

        assertEquals("getOrCreate()", completion.insert)
        assertTrue(completion.middle?.startsWith("<T>(") == true, completion.middle.orEmpty())
        assertFalse(completion.insert.contains(" = "))
    }

    @Test
    fun `member completions open after explicit receiver access`() {
        val completions = KatariScriptingAnalyzer.completions("test.ktr", "server.", "server.".length)

        assertTrue(completions.any { it.name == "getOrCreate" && it.tag == CompletionItemTag.FUNCTION })
        assertTrue(completions.any { it.name == "overworld" && it.tag == CompletionItemTag.PROPERTY })
    }

    @Test
    fun `completions stay closed without a meaningful prefix`() {
        assertEquals(emptyList(), KatariScriptingAnalyzer.completions("test.ktr", "", 0))
        assertEquals(emptyList(), KatariScriptingAnalyzer.completions("test.ktr", "val value = ", "val value = ".length))
        assertEquals(emptyList(), KatariScriptingAnalyzer.completions("test.ktr", "\"pla", "\"pla".length))
        assertEquals(emptyList(), KatariScriptingAnalyzer.completions("test.ktr", "// pla", "// pla".length))
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
        val diagnostics = KatariScriptingAnalyzer.diagnostic("test.ktr", "player.name\noverworld.time")

        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun `diagnostic accepts generic saved variables`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic(
            "saved.ktr",
            """
                val name = server.getOrCreate<String>("my_data") { "default" }
                server.set<String>("my_data", name)
                val exists = server.has("my_data")
            """.trimIndent(),
        )

        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun `compiler accepts inline generic saved variable initializer`() {
        val analysis = analyzeKatariNarrativeProgram(
            "saved.ktr",
            """
                val data = server.getOrCreate<Int>("value") { 0 }
                "Value: ${'$'}{data}"
            """.trimIndent(),
            KatariScriptingAnalyzer.bindings,
        )

        assertTrue(analysis.program != null)
    }

    @Test
    fun `diagnostic accepts world dimension api`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic(
            "world.ktr",
            """
                val nether = server.dimensionOrThrow("minecraft:the_nether")
                nether.setTime(18000)
                nether.setWeather(KatariWeather.Rain)
                val hit = player.raycast(32.0)
                if (hit.hasBlock) nether.destroyBlock(pos(0.0, 64.0, 0.0))
            """.trimIndent(),
        )

        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun `diagnostic accepts entity query api`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic(
            "entities.ktr",
            """
                val npcs = overworld.entities<NpcEntity>("hollowengine:npc_entity")
                val npc = overworld.entityOrThrow<NpcEntity>(
                    "00000000-0000-0000-0000-000000000000",
                    "hollowengine:npc_entity"
                )
                val box = overworld.entitiesIn<NpcEntity>(
                    pos(0.0, 0.0, 0.0),
                    pos(8.0, 8.0, 8.0),
                    "hollowengine:npc_entity"
                )
                val near = overworld.entitiesNear<NpcEntity>(
                    pos(0.0, 64.0, 0.0),
                    16.0,
                    "hollowengine:npc_entity"
                )
                npc.name
                npcs.first().name
                box.first().name
                near.first().name
            """.trimIndent(),
        )

        assertEquals(emptyList(), diagnostics)
    }
}
