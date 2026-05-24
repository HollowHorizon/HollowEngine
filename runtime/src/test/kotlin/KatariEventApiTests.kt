import com.sunnychung.lib.multiplatform.kotlite.katari.ChoiceOptionSnapshot
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariBindings
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariInstance
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariNarrativeProgram
import com.sunnychung.lib.multiplatform.kotlite.katari.KatariState
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindings
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeHost
import com.sunnychung.lib.multiplatform.kotlite.katari.TaskState
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter
import com.sunnychung.lib.multiplatform.kotlite.model.ExtensionProperty
import com.sunnychung.lib.multiplatform.kotlite.model.BooleanValue
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionResponse
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallContext
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallDispatchContext
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallResult
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallable
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeHostValue
import com.sunnychung.lib.multiplatform.kotlite.model.NullValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.SourcePosition
import com.sunnychung.lib.multiplatform.kotlite.model.StringValue
import com.sunnychung.lib.multiplatform.kotlite.model.TypeParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariEventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariEventHandlerSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariEventSubscriptions
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariEventType
import ru.hollowhorizon.hollowengine.common.scripting.katari.registerKatariEventBindings
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class KatariEventApiTests {
    @Test
    fun `active handler keeps script alive and processes events`() = runTest {
        TestEvent.clear()
        val events = mutableListOf<String>()
        val bindings = testBindings(events)
        val instance = katariInstance(
            bindings = bindings,
            code = """
                val handler = on<TestEvent> { event ->
                    "handled ${'$'}{event.name}"
                }
            """.trimIndent(),
            scope = this,
        )
        val join = async { instance.join() }

        instance.start()
        advanceUntilIdle()

        assertFalse(join.isCompleted)

        TestEvent.post(TestEvent("A"))
        advanceUntilIdle()

        assertEquals(listOf("handled A"), events)
        assertFalse(join.isCompleted)

        instance.cancel()
        join.cancel()
        TestEvent.clear()
    }

    @Test
    fun `restored handler resumes independent unfinished event tasks without duplicates`() = runTest {
        TestEvent.clear()
        val events = mutableListOf<String>()
        val gate = GateCallable()
        val bindings = testBindings(events, gate)
        val program = KatariNarrativeProgram(
            filename = "events.ktr",
            code = """
                val handler = on<TestEvent> { event ->
                    gate(event.name)
                    "done ${'$'}{event.name}"
                }
            """.trimIndent(),
            bindings = bindings,
        )
        val codec = bindings.snapshotCodec
        val instance = KatariInstance(
            program = program,
            initialState = KatariState(
                programVersion = program.version,
                tasks = listOf(TaskState(id = program.entryTaskId)),
                globals = bindings.globals,
            ),
            executionEnvironment = bindings.executionEnvironment,
            snapshotCodec = codec,
            coroutineScope = this,
        )

        instance.start()
        advanceUntilIdle()
        TestEvent.post(TestEvent("A"))
        advanceUntilIdle()
        TestEvent.post(TestEvent("B"))
        advanceUntilIdle()

        val snapshot = instance.serializeState()
        instance.cancel()

        val restored = KatariInstance(
            program = program,
            initialState = codec.restore(snapshot, TestRestoreContext).copy(globals = bindings.globals),
            executionEnvironment = bindings.executionEnvironment,
            snapshotCodec = codec,
            coroutineScope = this,
        )
        val join = async { restored.join() }

        restored.start()
        advanceUntilIdle()

        gate.resume("A")
        advanceUntilIdle()
        assertEquals(listOf("done A"), events)
        assertFalse(join.isCompleted)

        TestEvent.post(TestEvent("C"))
        advanceUntilIdle()
        gate.resume("C")
        advanceUntilIdle()
        assertEquals(listOf("done A", "done C"), events)

        gate.resume("B")
        advanceUntilIdle()
        assertEquals(listOf("done A", "done C", "done B"), events)
        assertFalse(join.isCompleted)

        restored.cancel()
        join.cancel()
        TestEvent.clear()
    }

    @Test
    fun `tracked event awaits unsubscribe when run is cleared`() = runTest {
        TestEvent.clear()
        val events = mutableListOf<String>()
        val bindings = testBindings(events, runId = "run-1")
        val instance = katariInstance(
            bindings = bindings,
            code = """
                val event = await<TestEvent>()
                "handled ${'$'}{event.name}"
            """.trimIndent(),
            scope = this,
        )

        instance.start()
        advanceUntilIdle()

        assertEquals(1, TestEvent.listeners.size)

        KatariEventSubscriptions.clear("run-1", "test")
        advanceUntilIdle()

        assertEquals(0, TestEvent.listeners.size)

        instance.cancel()
        TestEvent.clear()
    }

    @Test
    fun `await supports dotted event type arguments`() = runTest {
        NestedTestEvent.clear()
        val events = mutableListOf<String>()
        val bindings = testBindings(events, includeNestedEvent = true)
        val instance = katariInstance(
            bindings = bindings,
            code = """
                val event = await<TestEvent.Nested>()
                "handled ${'$'}{event.name}"
            """.trimIndent(),
            scope = this,
        )

        instance.start()
        advanceUntilIdle()

        NestedTestEvent.post(NestedTestEvent("nested"))
        advanceUntilIdle()

        assertEquals(listOf("handled nested"), events)

        instance.cancel()
        NestedTestEvent.clear()
    }


    private fun katariInstance(
        bindings: KatariBindings,
        code: String,
        scope: CoroutineScope,
    ): KatariInstance {
        val program = KatariNarrativeProgram("events.ktr", code, bindings)
        val codec = bindings.snapshotCodec
        return KatariInstance(
            program = program,
            initialState = KatariState(
                programVersion = program.version,
                tasks = listOf(TaskState(id = program.entryTaskId)),
                globals = bindings.globals,
            ),
            executionEnvironment = bindings.executionEnvironment,
            snapshotCodec = codec,
            coroutineScope = scope,
        )
    }

    private fun testBindings(
        events: MutableList<String>,
        gate: GateCallable? = null,
        runId: String? = null,
        includeNestedEvent: Boolean = false,
    ) = NarrativeBindings {
        registerMinimalEventTypes()
        registerTestEventType()
        if (includeNestedEvent) registerNestedTestEventType()
        val eventTypes = buildList {
            add(KatariEventType("TestEvent", TestEvent))
            if (includeNestedEvent) add(KatariEventType("TestEvent.Nested", NestedTestEvent))
        }
        registerKatariEventBindings(eventTypes, runId = runId)
        registerBuiltinFunctions(recordingHost(events))
        gate?.let { register(it) }
    }

    private fun NarrativeBindingsBuilder.registerMinimalEventTypes() {
        registerHostType(Event::class, "Event")
        registerHostType(
            KatariEventHandler::class,
            "EventHandler",
            emptyList(),
            KatariEventHandlerSnapshot::class,
            KatariEventHandlerSnapshot.serializer(),
            serialize = { KatariEventHandlerSnapshot(it.active) },
            deserialize = { snapshot, _ -> KatariEventHandler(snapshot.active) },
        )
        immediateFunction("katariEventHandler", returnType = "EventHandler") { _, context ->
            KatariGeneratedBindingRuntime.toRuntimeValue(KatariEventHandler(), "EventHandler", context.symbolTable)
        }
        registerKotliteExtensionProperty(
            ExtensionProperty(
                declaredName = "active",
                receiver = "EventHandler",
                type = "Boolean",
                getter = { interpreter, receiver, _ ->
                    val handler = (receiver as NarrativeHostValue).value as KatariEventHandler
                    BooleanValue(handler.active, interpreter.symbolTable())
                },
            ),
        )
        KatariGeneratedBindingRuntime.registerHostType(Event::class, "Event", emptyList())
        KatariGeneratedBindingRuntime.registerHostType(KatariEventHandler::class, "EventHandler", emptyList())
    }

    private fun NarrativeBindingsBuilder.registerTestEventType() {
        registerHostType(
            TestEvent::class,
            "TestEvent",
            listOf("Event"),
            TestEventSnapshot::class,
            TestEventSnapshot.serializer(),
            serialize = { TestEventSnapshot(it.name) },
            deserialize = { snapshot, _ -> TestEvent(snapshot.name) },
        )
        registerKotliteExtensionProperty(
            ExtensionProperty(
                declaredName = "name",
                receiver = "TestEvent",
                type = "String",
                getter = { interpreter, receiver, _ ->
                    val event = (receiver as NarrativeHostValue).value as TestEvent
                    StringValue(event.name, interpreter.symbolTable())
                },
            ),
        )
        KatariGeneratedBindingRuntime.registerHostType(TestEvent::class, "TestEvent", listOf("Event"))
    }

    private fun NarrativeBindingsBuilder.registerNestedTestEventType() {
        registerHostType(
            NestedTestEvent::class,
            "TestEvent.Nested",
            listOf("Event"),
            TestEventSnapshot::class,
            TestEventSnapshot.serializer(),
            serialize = { TestEventSnapshot(it.name) },
            deserialize = { snapshot, _ -> NestedTestEvent(snapshot.name) },
        )
        registerKotliteExtensionProperty(
            ExtensionProperty(
                declaredName = "name",
                receiver = "TestEvent.Nested",
                type = "String",
                getter = { interpreter, receiver, _ ->
                    val event = (receiver as NarrativeHostValue).value as NestedTestEvent
                    StringValue(event.name, interpreter.symbolTable())
                },
            ),
        )
        KatariGeneratedBindingRuntime.registerHostType(NestedTestEvent::class, "TestEvent.Nested", listOf("Event"))
    }

    private fun recordingHost(events: MutableList<String>) = object : NarrativeHost {
        override fun narrate(text: String, resume: () -> Unit) {
            events += text
            resume()
        }

        override fun choose(options: List<ChoiceOptionSnapshot>, resume: (String) -> Unit) {
            error("unused")
        }

        override fun readLine(question: String, resume: (String) -> Unit) {
            error("unused")
        }
    }
}

class TestEvent(val name: String) : Event {
    companion object : EventHandler<TestEvent>()
}

class NestedTestEvent(val name: String) : Event {
    companion object : EventHandler<NestedTestEvent>()
}

@Serializable
@SerialName("hollowengine:test_event")
data class TestEventSnapshot(val name: String) : ValueSnapshot()

object TestRestoreContext : ValueRestoreContext

class GateCallable : NarrativeCallable {
    private val pending = linkedMapOf<String, (FunctionResponse?) -> Unit>()

    override val id: String = "gate"
    override val receiverType: String? = null
    override val returnType: String = "Unit"
    override val typeParameters: List<TypeParameter> = emptyList()
    override val valueParameters: List<CustomFunctionParameter> = listOf(CustomFunctionParameter("name", "String"))

    override suspend fun startCall(arguments: List<RuntimeValue>, context: NarrativeCallContext): NarrativeCallResult {
        return NarrativeCallResult.Suspended
    }

    override suspend fun resumeCall(
        arguments: List<RuntimeValue>,
        response: FunctionResponse?,
        context: NarrativeCallContext,
    ): NarrativeCallResult {
        return NarrativeCallResult.Returned(NullValue)
    }

    override fun dispatch(
        arguments: List<RuntimeValue>,
        context: NarrativeCallDispatchContext,
        resume: (FunctionResponse?) -> Unit,
    ) {
        val name = (arguments.single() as StringValue).value
        pending[name] = resume
    }

    fun resume(name: String) {
        pending.remove(name)?.invoke(null)
    }
}
