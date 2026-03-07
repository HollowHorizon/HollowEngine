import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.LaunchPolicy
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDefinitionId
import ru.hollowhorizon.hollowengine.common.coroutines.RuntimeDefinitionRegistry
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineContextElement
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineDefinition
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineKey
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineKeyPart
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

private class TestLaunchContext(var id: Int = 0) : AbstractCoroutineContextElement(Key), SerializableCoroutineContextElement {
    companion object Key : CoroutineContext.Key<TestLaunchContext>

    override fun save(tag: CompoundTag) {
        tag.putInt("id", id)
    }

    override fun load(tag: CompoundTag) {
        id = tag.getInt("id")
    }
}

private object EntityScopeTestKey : CoroutineContext.Key<CoroutineContext.Element>

class EntityScopeTests {
    @Test
    fun `drop new policy keeps active execution`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = EntityScope(SupervisorJob() + dispatcher)
        val started = mutableListOf<Int>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var nextId = 0
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(EntityScopeTestKey))

        scope.registerSerializable(
            SerializableCoroutineDefinition(
                key = key,
                contextFactory = { TestLaunchContext(++nextId) },
                context = EmptyCoroutineContext,
                start = CoroutineStart.DEFAULT,
            ) {
                val ctx = currentCoroutineContext()[TestLaunchContext.Key]!!
                started += ctx.id
                val gate = CompletableDeferred<Unit>()
                gates += gate
                gate.await()
            }
        )

        val first = scope.launchSerializable(key, LaunchPolicy.DROP_NEW)
        runCurrent()
        val second = scope.launchSerializable(key, LaunchPolicy.DROP_NEW)
        runCurrent()

        assertSame(first, second)
        assertEquals(listOf(1), started)
        gates.removeFirst().complete(Unit)
        runCurrent()
    }

    @Test
    fun `cancel old policy cancels running execution and starts new one`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = EntityScope(SupervisorJob() + dispatcher)
        val started = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var nextId = 0
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(EntityScopeTestKey))

        scope.registerSerializable(
            SerializableCoroutineDefinition(
                key = key,
                contextFactory = { TestLaunchContext(++nextId) },
                context = EmptyCoroutineContext,
            ) {
                val ctx = currentCoroutineContext()[TestLaunchContext.Key]!!
                started += ctx.id
                val gate = CompletableDeferred<Unit>()
                gates += gate
                try {
                    gate.await()
                } catch (_: CancellationException) {
                    cancelled += ctx.id
                    throw CancellationException()
                }
            }
        )

        scope.launchSerializable(key, LaunchPolicy.CANCEL_OLD)
        runCurrent()
        scope.launchSerializable(key, LaunchPolicy.CANCEL_OLD)
        runCurrent()

        assertEquals(listOf(1, 2), started)
        assertEquals(listOf(1), cancelled)
        gates.removeFirst().complete(Unit)
        runCurrent()
    }

    @Test
    fun `enqueue policy runs queued execution after active one completes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = EntityScope(SupervisorJob() + dispatcher)
        val started = mutableListOf<Int>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var nextId = 0
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(EntityScopeTestKey))

        scope.registerSerializable(
            SerializableCoroutineDefinition(
                key = key,
                contextFactory = { TestLaunchContext(++nextId) },
                context = EmptyCoroutineContext,
            ) {
                val ctx = currentCoroutineContext()[TestLaunchContext.Key]!!
                started += ctx.id
                val gate = CompletableDeferred<Unit>()
                gates += gate
                gate.await()
            }
        )

        scope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        scope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        runCurrent()
        assertEquals(listOf(1), started)

        gates.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), started)

        gates.removeFirst().complete(Unit)
        runCurrent()
    }

    @Test
    fun `serialized running and queued executions are restored in order`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val originalScope = EntityScope(SupervisorJob() + dispatcher)
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(EntityScopeTestKey))
        val originalGates = ArrayDeque<CompletableDeferred<Unit>>()
        var nextId = 0

        originalScope.registerSerializable(
            SerializableCoroutineDefinition(
                key = key,
                contextFactory = { TestLaunchContext(++nextId) },
                context = EmptyCoroutineContext,
            ) {
                val gate = CompletableDeferred<Unit>()
                originalGates += gate
                gate.await()
            }
        )

        originalScope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        originalScope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        runCurrent()

        val serialized = CompoundTag()
        originalScope.serialize(serialized)
        originalScope.cancelAll()
        RuntimeDefinitionRegistry.unregister(RuntimeDefinitionId("serializable:$key"))
        runCurrent()

        val restoredScope = EntityScope(SupervisorJob() + dispatcher)
        val restoredOrder = mutableListOf<Int>()
        val restoredGates = ArrayDeque<CompletableDeferred<Unit>>()
        restoredScope.registerSerializable(
            SerializableCoroutineDefinition(
                key = key,
                contextFactory = { TestLaunchContext(0) },
                context = EmptyCoroutineContext,
            ) {
                val ctx = currentCoroutineContext()[TestLaunchContext.Key]!!
                restoredOrder += ctx.id
                val gate = CompletableDeferred<Unit>()
                restoredGates += gate
                gate.await()
            }
        )
        restoredScope.deserialize(serialized)
        runCurrent()

        assertEquals(listOf(1), restoredOrder)
        restoredGates.removeFirst().complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), restoredOrder)
        restoredGates.removeFirst().complete(Unit)
        runCurrent()
    }

    @Test
    fun `cancel serializable removes queued work and stops active execution`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = EntityScope(SupervisorJob() + dispatcher)
        val started = mutableListOf<Int>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var nextId = 0
        val key = SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(EntityScopeTestKey))

        scope.registerSerializable(
            SerializableCoroutineDefinition(
                key = key,
                contextFactory = { TestLaunchContext(++nextId) },
                context = EmptyCoroutineContext,
            ) {
                val ctx = currentCoroutineContext()[TestLaunchContext.Key]!!
                started += ctx.id
                val gate = CompletableDeferred<Unit>()
                gates += gate
                gate.await()
            }
        )

        scope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        scope.launchSerializable(key, LaunchPolicy.ENQUEUE)
        runCurrent()
        scope.cancelSerializable(key)
        runCurrent()

        assertEquals(listOf(1), started)
        assertFalse(scope.hasActiveExecutions())
    }
}
