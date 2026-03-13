import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.DoubleTag
import kotlin.test.assertIs
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableMap
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.decodeNumericTag
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VariableMapTests {
    @Test
    fun `variable map restores serialized raw tags`() = runTest {
        val original = VariableMap()
        original.setTag("answer", CompoundTag().apply { putInt("value", 42) })
        original.setRawTag("text", net.minecraft.nbt.StringTag.valueOf("hello"))

        val tag = CompoundTag()
        original.serialize(tag)

        val restored = VariableMap()
        restored.deserialize(tag)

        assertEquals(42, restored.getTag("answer")?.getInt("value"))
        assertEquals("hello", restored.getRawTag("text")?.asString)
    }

    @Test
    fun `variable map exposes combined compound tag`() = runTest {
        val map = VariableMap()
        map.setRawTag("flag", IntTag.valueOf(1))

        val combined = map.asCompoundTag()

        assertEquals(1, combined.getCompound("flag").getInt(VariableMap.VALUE_KEY))
    }

    @Test
    fun `variable map reads legacy raw values without wrapper`() = runTest {
        val legacy = CompoundTag().apply {
            put("number", DoubleTag.valueOf(4.5))
            put("compound", CompoundTag().apply { putString("name", "npc") })
        }

        val restored = VariableMap()
        restored.deserialize(legacy)

        val number = assertIs<DoubleTag>(restored.getRawTag("number"))
        assertEquals(4.5, number.asDouble)
        assertEquals("npc", restored.getTag("compound")?.getString("name"))
    }

    @Test
    fun `numeric tags decode as boxed numbers`() = runTest {
        val value = decodeNumericTag(DoubleTag.valueOf(4.5))

        assertIs<Double>(value)
        assertEquals(4.5, value)
    }

    @Test
    fun `entity scope serializes scoped compound tag variables`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val original = EntityScope(SupervisorJob() + dispatcher)
        original.variables.setTag("flag", CompoundTag().apply { putBoolean("value", true) })

        val tag = CompoundTag()
        original.serialize(tag)

        val restored = EntityScope(SupervisorJob() + dispatcher)
        restored.deserialize(tag)

        val restoredFlag = restored.variables.getTag("flag")
        assertNotNull(restoredFlag)
        assertEquals(true, restoredFlag.getBoolean("value"))
    }
}
