import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import ru.hollowhorizon.hollowengine.common.commands.arguments.ScriptPathArgument
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScriptPathArgumentTests {
    private val argument = ScriptPathArgument.scriptPath()

    @Test
    fun `a local path needs no quotes`() {
        val reader = StringReader("scripts/nodes/example.node.kts")
        val id = argument.parse(reader)

        assertEquals(ScriptRegistry.sandboxNamespace, id.namespace)
        assertEquals("nodes/example.node.kts", id.path)
        assertEquals(reader.totalLength, reader.cursor)
    }

    @Test
    fun `a namespaced path needs no quotes`() {
        val id = argument.parse(StringReader("my-addon:nodes/example.node.kts"))

        assertEquals("my-addon", id.namespace)
        assertEquals("nodes/example.node.kts", id.path)
    }

    @Test
    fun `parsing stops at the space so more arguments can follow`() {
        val reader = StringReader("scripts/nodes/example.node.kts greeting")
        val id = argument.parse(reader)

        assertEquals("nodes/example.node.kts", id.path)
        assertEquals(" greeting", reader.remaining)
    }

    @Test
    fun `quotes still work, for the paths that contain a space`() {
        val id = argument.parse(StringReader("\"scripts/my quests/first.node.kts\""))

        assertEquals("my quests/first.node.kts", id.path)
    }

    @Test
    fun `an empty or malformed path is rejected without consuming it`() {
        listOf("", " ", ":", "my-addon:").forEach { input ->
            val reader = StringReader(input)
            assertFailsWith<CommandSyntaxException>("'$input' should not parse") { argument.parse(reader) }
            assertEquals(0, reader.cursor, "'$input' should leave the cursor where it was")
        }
    }
}
