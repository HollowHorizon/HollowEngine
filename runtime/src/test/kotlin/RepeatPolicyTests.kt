import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.OwnerKey
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.buildBranchKey
import ru.hollowhorizon.hollowengine.common.coroutines.LaunchPolicy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

private class RepeatPolicyStartBlock : StartBlock() {
    override suspend fun trigger() = Unit
    override fun InputSlotScope.composeContent() = Unit
    override val color: Color get() = Color.WHITE
}

class RepeatPolicyTests {
    @Test
    fun `branch key contains owner script start and group`() {
        val block = RepeatPolicyStartBlock()
        val owner = OwnerKey.Entity(UUID.fromString("00000000-0000-0000-0000-000000000111"))
        block.branchGroupKey = "quest-1"
        block.repeatPolicy = LaunchPolicy.ENQUEUE

        val key = block.buildBranchKey("scripts/test.bc", owner)

        assertEquals(owner, key.owner)
        assertEquals("scripts/test.bc", key.scriptPath)
        assertEquals(block.uuid, key.startBlockId)
        assertEquals("quest-1", key.groupKey)
        assertEquals(LaunchPolicy.ENQUEUE, block.repeatPolicy)
    }
}
