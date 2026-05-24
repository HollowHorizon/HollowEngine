package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionDefinition
import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionBodyFormat
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionModifier
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionResponse
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallContext
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallDispatchContext
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallResult
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallable
import com.sunnychung.lib.multiplatform.kotlite.model.NullValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.SourcePosition
import com.sunnychung.lib.multiplatform.kotlite.model.TypeParameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedKatariErrorResponse
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedRuntimeValueResponse
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptIgnore
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@ScriptBinding("EventHandler")
class KatariEventHandler @ScriptIgnore constructor(active: Boolean = true) {
    var active: Boolean = active
        private set

    fun start() {
        active = true
    }

    fun stop() {
        active = false
    }
}

@Serializable
@SerialName("hollowengine:katari/event_handler")
@ScriptType("EventHandler")
data class KatariEventHandlerSnapshot(
    val active: Boolean,
) : ValueSnapshot(), ScriptSnapshot<KatariEventHandler> {
    override suspend fun restore(context: ValueRestoreContext): KatariEventHandler {
        return KatariEventHandler(active)
    }

    companion object : ScriptSnapshotFactory<KatariEventHandler, KatariEventHandlerSnapshot> {
        override fun capture(value: KatariEventHandler): KatariEventHandlerSnapshot {
            return KatariEventHandlerSnapshot(value.active)
        }
    }
}

@ScriptBinding("katariEventHandler")
fun createKatariEventHandler(): KatariEventHandler {
    return KatariEventHandler()
}

@ScriptBinding("player")
val ServerChatEvent.scriptPlayer: Player
    get() = player

@ScriptBinding("message")
var ServerChatEvent.scriptMessage: String
    get() = message.string
    set(value) {
        message = value.literal
    }

@ScriptBinding("username")
val ServerChatEvent.scriptUsername: String
    get() = username

@ScriptBinding("canceled")
var Event.scriptCanceled: Boolean
    get() = (this as? Cancellable)?.isCanceled ?: false
    set(value) {
        (this as? Cancellable)?.isCanceled = value
    }

fun NarrativeBindingsBuilder.registerKatariEventBindings(eventTypes: Iterable<KatariEventType<out Event>> = KatariMinecraftEventTypes) {
    val registry = KatariEventRegistry(eventTypes)
    register(KatariRawAwaitEventCallable(registry))
    register(KatariAwaitEventCallable)
    register(KatariOnEventCallable)
}

private class KatariRawAwaitEventCallable(
    private val registry: KatariEventRegistry,
) : NarrativeCallable {
    override val id: String = "katariAwaitEvent"
    override val receiverType: String? = null
    override val returnType: String = "T"
    override val typeParameters: List<TypeParameter> = listOf(TypeParameter("T", "Event"))
    override val valueParameters: List<CustomFunctionParameter> = emptyList()

    override suspend fun startCall(arguments: List<RuntimeValue>, context: NarrativeCallContext): NarrativeCallResult {
        return NarrativeCallResult.Suspended
    }

    override suspend fun resumeCall(
        arguments: List<RuntimeValue>,
        response: FunctionResponse?,
        context: NarrativeCallContext,
    ): NarrativeCallResult {
        return when (response) {
            is GeneratedRuntimeValueResponse -> NarrativeCallResult.Returned(response.value)
            is GeneratedKatariErrorResponse -> error(response.message)
            else -> NarrativeCallResult.Returned(NullValue)
        }
    }

    override fun dispatch(
        arguments: List<RuntimeValue>,
        context: NarrativeCallDispatchContext,
        resume: (FunctionResponse?) -> Unit,
    ) {
        val typeId = context.typeArguments["T"]?.name
            ?: return resume(GeneratedKatariErrorResponse("await cannot resolve the requested event type"))
        val binding = registry[typeId]
            ?: return resume(GeneratedKatariErrorResponse("Unsupported Katari event type `$typeId`"))

        binding.registerAwait(context, resume)
    }
}

private data object KatariAwaitEventCallable : NarrativeCallable {
    override val id: String = "await"
    override val receiverType: String? = null
    override val returnType: String = "T"
    override val typeParameters: List<TypeParameter> = listOf(TypeParameter("T", "Event"))
    override val valueParameters: List<CustomFunctionParameter> = listOf(
        CustomFunctionParameter("filter", "(T) -> Boolean", "{ true }"),
    )
    override val semanticFunctionDefinition: CustomFunctionDefinition = CustomFunctionDefinition(
        position = SourcePosition.BUILTIN,
        receiverType = receiverType,
        functionName = id,
        returnType = returnType,
        typeParameters = typeParameters,
        parameterTypes = valueParameters,
        modifiers = setOf(FunctionModifier.inline),
        inlineFunctionBody = """
            {
                var event = katariAwaitEvent<T>()
                while (!filter(event)) {
                    event = katariAwaitEvent<T>()
                }
                return event
            }
        """.trimIndent(),
        inlineFunctionBodyFormat = FunctionBodyFormat.Block,
        executable = { _, _, _, _ -> error("Inline Katari binding `await` must be compiled before execution") },
    )

    override suspend fun startCall(arguments: List<RuntimeValue>, context: NarrativeCallContext): NarrativeCallResult {
        error("Inline Katari binding `await` must be compiled before execution")
    }

    override suspend fun resumeCall(
        arguments: List<RuntimeValue>,
        response: FunctionResponse?,
        context: NarrativeCallContext,
    ): NarrativeCallResult {
        error("Inline Katari binding `await` cannot be resumed")
    }

    override fun dispatch(
        arguments: List<RuntimeValue>,
        context: NarrativeCallDispatchContext,
        resume: (FunctionResponse?) -> Unit,
    ) {
        error("Inline Katari binding `await` cannot be dispatched")
    }
}

private data object KatariOnEventCallable : NarrativeCallable {
    override val id: String = "on"
    override val receiverType: String? = null
    override val returnType: String = "EventHandler"
    override val typeParameters: List<TypeParameter> = listOf(TypeParameter("T", "Event"))
    override val valueParameters: List<CustomFunctionParameter> = listOf(
        CustomFunctionParameter("block", "(T) -> Unit"),
    )
    override val semanticFunctionDefinition: CustomFunctionDefinition = CustomFunctionDefinition(
        position = SourcePosition.BUILTIN,
        receiverType = receiverType,
        functionName = id,
        returnType = returnType,
        typeParameters = typeParameters,
        parameterTypes = valueParameters,
        modifiers = setOf(FunctionModifier.inline),
        inlineFunctionBody = """
            {
                val handler = katariEventHandler()
                val listener = async {
                    while (true) {
                        val event = await<T> { handler.active }
                        if (handler.active) {
                            val task = async {
                                block(event)
                            }
                            task.start()
                        }
                    }
                }
                listener.start()
                return handler
            }
        """.trimIndent(),
        inlineFunctionBodyFormat = FunctionBodyFormat.Block,
        executable = { _, _, _, _ -> error("Inline Katari binding `on` must be compiled before execution") },
    )

    override suspend fun startCall(arguments: List<RuntimeValue>, context: NarrativeCallContext): NarrativeCallResult {
        error("Inline Katari binding `on` must be compiled before execution")
    }

    override suspend fun resumeCall(
        arguments: List<RuntimeValue>,
        response: FunctionResponse?,
        context: NarrativeCallContext,
    ): NarrativeCallResult {
        error("Inline Katari binding `on` cannot be resumed")
    }

    override fun dispatch(
        arguments: List<RuntimeValue>,
        context: NarrativeCallDispatchContext,
        resume: (FunctionResponse?) -> Unit,
    ) {
        error("Inline Katari binding `on` cannot be dispatched")
    }
}

val KatariMinecraftEventTypes = generatedKatariEventTypes() + listOf(
    KatariEventType("ServerChatEvent", ServerChatEvent),
)

class KatariEventType<T : Event>(
    val typeId: String,
    internal val handler: EventHandler<T>,
)

private class KatariEventRegistry(eventTypes: Iterable<KatariEventType<out Event>>) {
    private val entries = eventTypes.map { KatariEventBinding(it.typeId, it.handler) }.associateBy { it.typeId }

    operator fun get(typeId: String): KatariEventBinding<out Event>? {
        return entries[typeId]
    }
}

private class KatariEventBinding<T : Event>(
    val typeId: String,
    private val handler: EventHandler<T>,
) {
    fun registerAwait(
        context: NarrativeCallDispatchContext,
        resume: (FunctionResponse?) -> Unit,
    ) {
        val isDone = AtomicBoolean(false)
        val listener = object : EventListener<T> {
            override val priority: Int = 0

            override fun invoke(event: T) {
                if (isDone.compareAndSet(false, true)) {
                    handler.unregister(this)
                    val eventValue = event.toRuntimeValue(typeId, context)
                    resume(GeneratedRuntimeValueResponse(eventValue))
                }
            }
        }
        handler.register(listener)
    }
}

private fun Event.toRuntimeValue(typeId: String, context: NarrativeCallDispatchContext): RuntimeValue {
    return KatariGeneratedBindingRuntime.toRuntimeValue(this, typeId, context.symbolTable)
}

@Serializable
@SerialName("hollowengine:katari/event")
@ScriptType("Event")
data class EventSnapshot(
    val type: String,
) : ValueSnapshot(), ScriptSnapshot<Event> {
    override suspend fun restore(context: ValueRestoreContext): Event {
        error("Generic Katari event snapshots cannot be restored; store concrete event types instead")
    }

    companion object : ScriptSnapshotFactory<Event, EventSnapshot> {
        override fun capture(value: Event): EventSnapshot {
            return EventSnapshot(value::class.java.name)
        }
    }
}

@Serializable
@SerialName("hollowengine:katari/server_chat_event")
@ScriptType("ServerChatEvent", Event::class)
data class ServerChatEventSnapshot(
    val player: @Serializable(ForStringUUID::class) UUID,
    val message: String,
    val canceled: Boolean,
) : ValueSnapshot(), ScriptSnapshot<ServerChatEvent> {
    override suspend fun restore(context: ValueRestoreContext): ServerChatEvent {
        val event = ServerChatEvent(restoreServerPlayer(context, player), Component.literal(message))
        event.isCanceled = canceled
        return event
    }

    companion object : ScriptSnapshotFactory<ServerChatEvent, ServerChatEventSnapshot> {
        override fun capture(value: ServerChatEvent): ServerChatEventSnapshot {
            return ServerChatEventSnapshot(value.player.uuid, value.message.string, value.isCanceled)
        }
    }
}

private suspend fun restorePlayer(context: ValueRestoreContext, uuid: UUID): Player {
    val server = (context as KatariRestoreContext).server
    server.playerList.getPlayer(uuid)?.let { return it }
    return PlayerEvent.Join.await { it.player.uuid == uuid }.player
}

private suspend fun restoreServerPlayer(context: ValueRestoreContext, uuid: UUID): ServerPlayer {
    return restorePlayer(context, uuid) as? ServerPlayer
        ?: error("Restored player `$uuid` is not a server player")
}
