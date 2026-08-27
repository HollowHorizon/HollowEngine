import ru.hollowhorizon.hollowengine.common.dialogue.lang.StoryCompiler
import ru.hollowhorizon.hollowengine.common.dialogue.lang.StoryLineKind
import ru.hollowhorizon.hollowengine.common.dialogue.text.FormattedTextParser
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FormattedDialogueDemoTest {
    @Test
    fun `packaged dialogue demos are valid stories with valid markup`() {
        listOf(
            "/scripts/formatted_dialogue.story",
            "/scripts/examples/vitalik_npc_dialogue.story",
        ).forEach(::assertValidStory)
    }

    private fun assertValidStory(resourcePath: String) {
        val source = assertNotNull(javaClass.getResourceAsStream(resourcePath))
            .bufferedReader()
            .use { it.readText() }
        val storyPath = resourcePath.removePrefix("/")
        val compiled = StoryCompiler.compile("hollowengine-debug-command:$storyPath", source)
        assertNotNull(compiled.program, compiled.diagnostics.joinToString { it.message })

        val templates = compiled.cst.lines.mapNotNull { line ->
            when (val kind = line.kind) {
                is StoryLineKind.Dialogue -> kind.text
                is StoryLineKind.Choice -> kind.text
                else -> null
            }
        }
        val diagnostics = templates.flatMap { template ->
            FormattedTextParser.parse(template.literalText()).diagnostics
        }
        assertTrue(diagnostics.isEmpty(), diagnostics.joinToString { it.message })
    }
}
