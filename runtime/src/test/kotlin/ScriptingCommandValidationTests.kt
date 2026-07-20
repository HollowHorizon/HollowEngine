import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.common.commands.isNodeScriptPath
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptingCommandValidationTests {
    @Test
    fun `initial state accepts only node scripts`() {
        assertTrue(isNodeScriptPath("scripts/dialogue.node.kts"))
        assertFalse(isNodeScriptPath("scripts/dialogue.kts"))
        assertFalse(isNodeScriptPath("scripts/dialogue.reload.kts"))
        assertFalse(isNodeScriptPath("scripts/dialogue.node.kts.backup"))
    }
}
