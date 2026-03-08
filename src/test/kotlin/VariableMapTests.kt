import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.codeblocks.createContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableMap
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VariableMapTests {
    @Test
    fun `variable map restores serialized primitive values with metadata`() = runTest {
        val original = VariableMap()
        val number = createContainer(typeOf<Int>())
        number.set(42)
        original["answer"] = number
        val text = createContainer(typeOf<String>())
        text.set("hello")
        original["text"] = text

        val tag = CompoundTag()
        original.serialize(tag)

        val restored = VariableMap()
        restored.deserialize(tag)

        assertEquals(42, restored["answer"]?.get(typeOf<Int>()))
        assertEquals("hello", restored["text"]?.get(typeOf<String>()))
    }

    @Test
    fun `entity scope serializes scoped variables`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val original = EntityScope(SupervisorJob() + dispatcher)
        val flag = createContainer(typeOf<Boolean>())
        flag.set(true)
        original.variables["flag"] = flag

        val tag = CompoundTag()
        original.serialize(tag)

        val restored = EntityScope(SupervisorJob() + dispatcher)
        restored.deserialize(tag)

        val restoredFlag = restored.variables["flag"]
        assertNotNull(restoredFlag)
        assertEquals(true, restoredFlag.get(typeOf<Boolean>()))
    }
}
