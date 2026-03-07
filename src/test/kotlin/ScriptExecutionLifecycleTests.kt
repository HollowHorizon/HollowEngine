import de.fabmax.kool.util.Color
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockFrame
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.BlockFrameStackElement
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.scoped
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.BlocksSystem
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptContextElement
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptFile
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.ScriptInstance
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.LaunchPolicy
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDefinitionId
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDefinitionRegistry
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineDefinition
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineKey
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineKeyPart
import sun.misc.Unsafe
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private object ScriptExecutionLifecycleKey : CoroutineContext.Key<CoroutineContext.Element>

private fun lifecycleUnsafe(): Unsafe {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe")
    field.isAccessible = true
    return field.get(null) as Unsafe
}

private fun setLifecycleField(target: Any, name: String, value: Any?) {
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

private fun fakeLifecycleInstance(): ScriptInstance {
    val unsafe = lifecycleUnsafe()
    val system = unsafe.allocateInstance(BlocksSystem::class.java) as BlocksSystem
    setLifecycleField(system, "dirtyListener", {})

    val file = ScriptFile(system, "tests/lifecycle.bc", emptyList())
    val instance = unsafe.allocateInstance(ScriptInstance::class.java) as ScriptInstance
    setLifecycleField(instance, "ownerFile", file)
    return instance
}

private class LifecycleStartBlock : StartBlock() {
    override suspend fun trigger() = Unit
    override fun InputSlotScope.composeContent() = Unit
    override val color: Color get() = Color.WHITE
}

private class RecordingLifecycleBlock(
    private val marker: String,
    private val log: MutableList<String>,
) : StatementBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun execute() {
        log += marker
    }

    override fun InputSlotScope.composeContent() = Unit
}

private class WaitCoordinator {
    private var nextRunId = 0
    private val gates = mutableMapOf<Int, CompletableDeferred<Unit>>()

    fun nextRunId(): Int = ++nextRunId

    fun isWaiting(runId: Int): Boolean = gates.containsKey(runId)

    fun release(runId: Int) {
        val gate = gates[runId] ?: error("No gate for runId=$runId")
        gate.complete(Unit)
    }

    suspend fun await(runId: Int) {
        val gate = CompletableDeferred<Unit>()
        gates[runId] = gate
        try {
            gate.await()
        } finally {
            gates.remove(runId)
        }
    }
}

private class RestorableWaitBlock(
    private val coordinator: WaitCoordinator,
    private val log: MutableList<String>,
) : StatementBlock() {
    override val color: Color get() = Color.WHITE

    override suspend fun execute() {
        val frame = coroutineContext[BlockFrame.Key] ?: error("Block frame not found")
        val runId = if (frame.tag.contains("wait_run_id")) {
            frame.tag.getInt("wait_run_id")
        } else {
            coordinator.nextRunId().also {
                frame.tag.putInt("wait_run_id", it)
                log += "wait-enter:$it"
            }
        }

        coordinator.await(runId)
        log += "wait-exit:$runId"
    }

    override fun InputSlotScope.composeContent() = Unit
}

private fun buildLifecycleChain(log: MutableList<String>, coordinator: WaitCoordinator): LifecycleStartBlock {
    val start = LifecycleStartBlock()
    val beforeA = RecordingLifecycleBlock("before-a", log)
    val beforeB = RecordingLifecycleBlock("before-b", log)
    val wait = RestorableWaitBlock(coordinator, log)
    val after = RecordingLifecycleBlock("after", log)

    start.next = beforeA
    beforeA.parent = start
    beforeA.next = beforeB
    beforeB.parent = beforeA
    beforeB.next = wait
    wait.parent = beforeB
    wait.next = after
    after.parent = wait
    return start
}

private fun registerLifecycleExecution(
    scope: EntityScope,
    key: SerializableCoroutineKey,
    instance: ScriptInstance,
    root: StartBlock,
) {
    scope.registerSerializable(
        SerializableCoroutineDefinition(
            key = key,
            contextFactory = { BlockFrameStackElement(instance) },
            context = ScriptContextElement(instance),
        ) {
            scoped {
                CodeBlockInterpreter<Unit>(root).execute()
            }
        }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScriptExecutionLifecycleTests {
    @Test
    fun `restored execution continues from waiting block without replaying completed chain`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val originalScope = EntityScope(SupervisorJob() + dispatcher)
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(ScriptExecutionLifecycleKey))
        val log = mutableListOf<String>()
        val coordinator = WaitCoordinator()
        val root = buildLifecycleChain(log, coordinator)
        val instance = fakeLifecycleInstance()

        registerLifecycleExecution(originalScope, key, instance, root)
        originalScope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        runCurrent()

        assertEquals(listOf("before-a", "before-b", "wait-enter:1"), log)
        assertTrue(coordinator.isWaiting(1))

        val serialized = CompoundTag()
        originalScope.serialize(serialized)
        originalScope.cancelAll()
        RuntimeDefinitionRegistry.unregister(RuntimeDefinitionId("serializable:$key"))
        runCurrent()

        val restoredScope = EntityScope(SupervisorJob() + dispatcher)
        registerLifecycleExecution(restoredScope, key, instance, root)
        restoredScope.deserialize(serialized)
        runCurrent()

        assertEquals(listOf("before-a", "before-b", "wait-enter:1"), log)
        assertTrue(coordinator.isWaiting(1))

        coordinator.release(1)
        runCurrent()

        assertEquals(listOf("before-a", "before-b", "wait-enter:1", "wait-exit:1", "after"), log)
    }

    @Test
    fun `queued retrigger waits for restored execution to finish before starting next chain`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val originalScope = EntityScope(SupervisorJob() + dispatcher)
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(ScriptExecutionLifecycleKey))
        val log = mutableListOf<String>()
        val coordinator = WaitCoordinator()
        val root = buildLifecycleChain(log, coordinator)
        val instance = fakeLifecycleInstance()

        registerLifecycleExecution(originalScope, key, instance, root)
        originalScope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        runCurrent()
        assertEquals(listOf("before-a", "before-b", "wait-enter:1"), log)

        val serialized = CompoundTag()
        originalScope.serialize(serialized)
        originalScope.cancelAll()
        RuntimeDefinitionRegistry.unregister(RuntimeDefinitionId("serializable:$key"))
        runCurrent()

        val restoredScope = EntityScope(SupervisorJob() + dispatcher)
        registerLifecycleExecution(restoredScope, key, instance, root)
        restoredScope.deserialize(serialized)
        runCurrent()

        restoredScope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        runCurrent()
        assertEquals(listOf("before-a", "before-b", "wait-enter:1"), log)

        coordinator.release(1)
        runCurrent()
        assertEquals(
            listOf("before-a", "before-b", "wait-enter:1", "wait-exit:1", "after", "before-a", "before-b", "wait-enter:2"),
            log
        )
        assertTrue(coordinator.isWaiting(2))

        coordinator.release(2)
        runCurrent()
        assertEquals(
            listOf(
                "before-a",
                "before-b",
                "wait-enter:1",
                "wait-exit:1",
                "after",
                "before-a",
                "before-b",
                "wait-enter:2",
                "wait-exit:2",
                "after",
            ),
            log
        )
    }

    @Test
    fun `ignored retrigger does not start duplicate chain while restored execution is active`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val originalScope = EntityScope(SupervisorJob() + dispatcher)
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(ScriptExecutionLifecycleKey))
        val log = mutableListOf<String>()
        val coordinator = WaitCoordinator()
        val root = buildLifecycleChain(log, coordinator)
        val instance = fakeLifecycleInstance()

        registerLifecycleExecution(originalScope, key, instance, root)
        originalScope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        runCurrent()

        val serialized = CompoundTag()
        originalScope.serialize(serialized)
        originalScope.cancelAll()
        RuntimeDefinitionRegistry.unregister(RuntimeDefinitionId("serializable:$key"))
        runCurrent()

        val restoredScope = EntityScope(SupervisorJob() + dispatcher)
        registerLifecycleExecution(restoredScope, key, instance, root)
        restoredScope.deserialize(serialized)
        runCurrent()

        restoredScope.launchSerializable(key, LaunchPolicy.DROP_NEW)
        runCurrent()
        assertEquals(listOf("before-a", "before-b", "wait-enter:1"), log)

        coordinator.release(1)
        runCurrent()
        assertEquals(listOf("before-a", "before-b", "wait-enter:1", "wait-exit:1", "after"), log)
    }
}
