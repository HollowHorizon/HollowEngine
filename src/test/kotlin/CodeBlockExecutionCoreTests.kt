import de.fabmax.kool.util.Color
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.StringConcatBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.ToStringBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.ExpressionBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.scoped
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.BlocksSystem
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptContextElement
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptFile
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptInstance
import sun.misc.Unsafe
import kotlin.test.*

private fun unsafe(): Unsafe {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    return field.get(null) as Unsafe
}

private fun setField(target: Any, name: String, value: Any?) {
    var type: Class<*>? = target.javaClass
    while (type != null) {
        runCatching {
            val field = type.getDeclaredField(name)
            field.isAccessible = true
            field.set(target, value)
            return
        }
        type = type.superclass
    }
    error("Field $name not found on ${target.javaClass.name}")
}

private fun fakeScriptInstance(): ScriptInstance {
    val unsafe = unsafe()
    val system = unsafe.allocateInstance(BlocksSystem::class.java) as BlocksSystem
    setField(system, "dirtyListener", {})

    val file = ScriptFile(system, "tests/runtime.bc", emptyList())
    val instance = unsafe.allocateInstance(ScriptInstance::class.java) as ScriptInstance
    setField(instance, "ownerFile", file)
    return instance
}

private fun stackWithFrames(vararg frameTags: CompoundTag): BlockFrameStackElement {
    val stack = BlockFrameStackElement(fakeScriptInstance())
    if (frameTags.isNotEmpty()) {
        val tag = CompoundTag()
        val frames = ListTag()
        frameTags.forEach(frames::add)
        tag.put("frames", frames)
        stack.load(tag)
    }
    return stack
}

private fun stackWithFrame(frameTag: CompoundTag? = null): BlockFrameStackElement {
    return if (frameTag == null) stackWithFrames() else stackWithFrames(frameTag)
}

private suspend fun <T> withRuntimeContext(stack: BlockFrameStackElement, action: suspend () -> T): T {
    return withContext(ScriptContextElement(stack.instance) + stack) {
        action()
    }
}

private suspend fun executeStatement(root: StatementBlock, stack: BlockFrameStackElement = stackWithFrame()) {
    withRuntimeContext(stack) {
        scoped {
            CodeBlockInterpreter<Unit>(root).execute()
        }
    }
}

@Suppress("UNCHECKED_CAST")
private suspend fun <T : Any> executeExpression(
    expression: ExpressionBlock,
    stack: BlockFrameStackElement = stackWithFrame(),
): T {
    return withRuntimeContext(stack) {
        scoped {
            ExpressionBlockInterpreter<T>(expression).execute()
        }
    }
}

private class RecordingStatementBlock(
    private val action: () -> Unit = {},
) : StatementBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun execute() = action()
    override fun InputSlotScope.composeContent() = Unit
}

private class ThrowingStatementBlock(
    private val throwableMessage: String = "boom",
) : StatementBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun execute() {
        error(throwableMessage)
    }

    override fun InputSlotScope.composeContent() = Unit
}

private class TestValueBlock<T : Any>(
    private val stored: T,
    private val type: ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType,
) : ExpressionBlock() {
    override val color: Color get() = Color.WHITE
    override val expressionType = type
    override suspend fun execute(): Any = stored
    override fun InputSlotScope.composeContent() = Unit
}

private class SequenceBoolBlock(values: List<Boolean>) : ExpressionBlock() {
    private val values = ArrayDeque(values)
    override val color: Color get() = Color.WHITE
    override val expressionType = typeOf<Boolean>()
    override suspend fun execute(): Any = values.removeFirst()
    override fun InputSlotScope.composeContent() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class CodeBlockExecutionCoreTests {
    @Test
    fun `remember caches value and forget clears it`() = runTest {
        val frame = BlockFrame()

        withContext(frame) {
            assertEquals(1, remember("counter") { 1 })
            assertEquals(1, remember("counter") { 2 })
            assertTrue(forget("counter"))
            assertFalse(frame.tag.contains("counter"))
            assertEquals(3, remember("counter") { 3 })
        }
    }

    @Test
    fun `code block interpreter executes linear chain in order`() = runTest {
        val calls = mutableListOf<String>()
        val first = RecordingStatementBlock { calls += "first" }
        val second = RecordingStatementBlock { calls += "second" }
        val third = RecordingStatementBlock { calls += "third" }
        first.next = second
        second.parent = first
        second.next = third
        third.parent = second

        executeStatement(first)

        assertEquals(listOf("first", "second", "third"), calls)
    }

    @Test
    fun `code block interpreter resumes from saved uuid`() = runTest {
        val calls = mutableListOf<String>()
        val first = RecordingStatementBlock { calls += "first" }
        val second = RecordingStatementBlock { calls += "second" }
        val third = RecordingStatementBlock { calls += "third" }
        first.next = second
        second.parent = first
        second.next = third
        third.parent = second
        val frameTag = CompoundTag().apply { putUUID("uuid", second.uuid) }

        executeStatement(first, stackWithFrame(frameTag))

        assertEquals(listOf("second", "third"), calls)
    }

    @Test
    fun `code block interpreter stores current block before exception`() = runTest {
        val first = RecordingStatementBlock()
        val failing = ThrowingStatementBlock("expected")
        first.next = failing
        failing.parent = first
        val frameTag = CompoundTag()

        val error = assertFailsWith<IllegalStateException> {
            executeStatement(first, stackWithFrame(frameTag))
        }

        assertTrue(error.message!!.contains("expected"))
        assertEquals(failing.uuid, frameTag.getUUID("uuid"))
    }

    @Test
    fun `frame stack save and load restores nested frame values`() = runTest {
        val stack = stackWithFrame()
        val saved = CompoundTag()

        withRuntimeContext(stack) {
            stack.withScopedContext {
                remember("value") { 42 }
                stack.save(saved)
            }
        }

        val restored = stackWithFrame().also { it.load(saved) }
        val restoredValue = withRuntimeContext(restored) {
            restored.withScopedContext {
                remember("value") { 0 }
            }
        }

        assertEquals(42, restoredValue)
    }

    @Test
    fun `if block executes body only when condition is true`() = runTest {
        val hits = mutableListOf<String>()
        val block = IfBlock().apply {
            attachInput("condition", TestValueBlock(true, typeOf<Boolean>()))
            attachInput("then", RecordingStatementBlock { hits += "then" })
        }

        executeStatement(block)
        assertEquals(listOf("then"), hits)
    }

    @Test
    fun `if else block selects false branch`() = runTest {
        val hits = mutableListOf<String>()
        val block = IfElseBlock().apply {
            attachInput("condition", TestValueBlock(false, typeOf<Boolean>()))
            attachInput("then", RecordingStatementBlock { hits += "then" })
            attachInput("else", RecordingStatementBlock { hits += "else" })
        }

        executeStatement(block)
        assertEquals(listOf("else"), hits)
    }

    @Test
    fun `repeat block executes body exact number of times`() = runTest {
        var hits = 0
        val block = RepeatBlock().apply {
            attachInput("times", TestValueBlock(3.0, typeOf<Number>()))
            attachInput("body", RecordingStatementBlock { hits++ })
        }

        executeStatement(block)
        assertEquals(3, hits)
    }

    @Test
    fun `repeat block resumes from saved index without re-running completed iterations`() = runTest {
        var hits = 0
        val block = RepeatBlock().apply {
            attachInput("times", TestValueBlock(3.0, typeOf<Number>()))
            attachInput("body", RecordingStatementBlock { hits++ })
        }
        val frameTag = CompoundTag().apply {
            putInt("times", 3)
            putInt("index", 1)
        }

        executeStatement(block, stackWithFrames(CompoundTag(), frameTag))
        assertEquals(2, hits)
    }

    @Test
    fun `while block reevaluates condition until false`() = runTest {
        var hits = 0
        val block = WhileBlock().apply {
            attachInput("cond", SequenceBoolBlock(listOf(true, true, false)))
            attachInput("body", RecordingStatementBlock { hits++ })
        }

        executeStatement(block)
        assertEquals(2, hits)
    }

    @Test
    fun `delay block waits for time and cancellation prevents next blocks`() = runTest {
        val hits = mutableListOf<String>()
        val delayBlock = DelayBlock().apply {
            attachInput("time", TestValueBlock(5.0, typeOf<Number>()))
        }
        val after = RecordingStatementBlock { hits += "after" }
        delayBlock.next = after
        after.parent = delayBlock

        val job = launch {
            executeStatement(delayBlock)
        }

        runCurrent()
        assertEquals(emptyList(), hits)

        advanceTimeBy(4_900)
        runCurrent()
        assertEquals(emptyList(), hits)

        job.cancel()
        runCurrent()
        assertEquals(emptyList(), hits)
    }

    @Test
    fun `math block handles all operations`() = runTest {
        val values = mapOf(
            MathOp.ADD to 7.0,
            MathOp.SUB to 1.0,
            MathOp.MUL to 12.0,
            MathOp.DIV to (4.0 / 3.0),
        )

        values.forEach { (op, expected) ->
            val block = MathBlock(op).apply {
                attachInput("a", TestValueBlock(4.0, typeOf<Number>()))
                attachInput("b", TestValueBlock(3.0, typeOf<Number>()))
            }
            val result: Number = executeExpression(block)
            assertEquals(expected, result.toDouble(), 1e-9)
        }
    }

    @Test
    fun `compare block handles all comparisons`() = runTest {
        val cases = mapOf(
            CompareOp.EQUALS to true,
            CompareOp.NOT_EQUALS to false,
            CompareOp.GREATER to false,
            CompareOp.LESS to false,
            CompareOp.GREATER_EQUALS to true,
            CompareOp.LESS_EQUALS to true,
        )

        cases.forEach { (op, expected) ->
            val block = CompareBlock(op).apply {
                attachInput("a", TestValueBlock(5.0, typeOf<Number>()))
                attachInput("b", TestValueBlock(5.0, typeOf<Number>()))
            }
            val result: Boolean = executeExpression(block)
            assertEquals(expected, result)
        }
    }

    @Test
    fun `logic and test blocks return selected values`() = runTest {
        val andBlock = LogicBlock(LogicOp.AND).apply {
            attachInput("a", TestValueBlock(true, typeOf<Boolean>()))
            attachInput("b", TestValueBlock(false, typeOf<Boolean>()))
        }
        val orBlock = LogicBlock(LogicOp.OR).apply {
            attachInput("a", TestValueBlock(true, typeOf<Boolean>()))
            attachInput("b", TestValueBlock(false, typeOf<Boolean>()))
        }
        val notBlock = NotBlock().apply {
            attachInput("value", TestValueBlock(false, typeOf<Boolean>()))
        }
        val testBlock = TestBlock().apply {
            attachInput("test", TestValueBlock(true, typeOf<Boolean>()))
            attachInput("then", TestValueBlock("left", typeOf<String>()))
            attachInput("else", TestValueBlock("right", typeOf<String>()))
        }

        val andResult: Boolean = executeExpression(andBlock)
        val orResult: Boolean = executeExpression(orBlock)
        val notResult: Boolean = executeExpression(notBlock)
        val selected: String = executeExpression(testBlock)

        assertFalse(andResult)
        assertTrue(orResult)
        assertTrue(notResult)
        assertEquals("left", selected)
        assertEquals(typeOf<String>().toString(), testBlock.expressionType.toString())
    }

    @Test
    fun `string utility blocks convert and concatenate values`() = runTest {
        val concat = StringConcatBlock().apply {
            attachInput("parts_0", TestValueBlock("npc-", typeOf<String>()))
            attachInput("parts_1", TestValueBlock(7, typeOf<Int>()))
            attachInput("parts_2", TestValueBlock(true, typeOf<Boolean>()))
        }
        val stringify = ToStringBlock().apply {
            attachInput("value", TestValueBlock(12.5, typeOf<Double>()))
        }

        val concatResult: String = executeExpression(concat)
        val stringResult: String = executeExpression(stringify)

        assertEquals("npc-7true", concatResult)
        assertEquals("12.5", stringResult)
    }
    @Test
    fun `literal blocks return stored values`() = runTest {
        val number = NumberBlock(12.5)
        val string = StringValueBlock("hello")
        val bool = BoolBlock(false)

        val numberResult: Number = executeExpression(number)
        val stringResult: String = executeExpression(string)
        val boolResult: Boolean = executeExpression(bool)

        assertEquals(12.5, numberResult.toDouble())
        assertEquals("hello", stringResult)
        assertFalse(boolResult)
        assertEquals(typeOf<Number>().toString(), number.expressionTypeOrNull.toString())
        assertEquals(typeOf<String>().toString(), string.expressionTypeOrNull.toString())
        assertEquals(typeOf<Boolean>().toString(), bool.expressionTypeOrNull.toString())
        assertTrue(AnyType.accepts(number.expressionTypeOrNull!!))
    }
}

