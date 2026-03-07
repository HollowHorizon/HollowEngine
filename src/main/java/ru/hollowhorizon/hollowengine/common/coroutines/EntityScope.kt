package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.*
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.entity.Entity
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


private object EmptySerializableContextElement :
    AbstractCoroutineContextElement(Key),
    SerializableCoroutineContextElement {
    object Key : CoroutineContext.Key<EmptySerializableContextElement>

    override fun save(tag: CompoundTag) = Unit
}

data class SerializableCoroutineDefinition(
    val key: SerializableCoroutineKey,
    val contextFactory: () -> SerializableCoroutineContextElement = { EmptySerializableContextElement },
    val context: CoroutineContext = EmptyCoroutineContext,
    val start: CoroutineStart = CoroutineStart.DEFAULT,
    val block: suspend CoroutineScope.() -> Unit,
)

class EntityScope(override val coroutineContext: CoroutineContext) : SerializableCoroutineScope {
    constructor(entity: Entity) : this(SupervisorJob() + (entity.server?.dispatcher ?: Minecraft.getInstance().dispatcher))

    private val lock = Any()
    private val definitions = ConcurrentHashMap<SerializableCoroutineKey, SerializableCoroutineDefinition>()
    private val activeExecutions = ConcurrentHashMap<SerializableCoroutineKey, ExecutionRecord>()
    private val queuedExecutions = ConcurrentHashMap<SerializableCoroutineKey, ArrayDeque<LaunchRequest>>()
    private val pendingRestore = ConcurrentHashMap<SerializableCoroutineKey, ArrayDeque<SerializedExecution>>()

    override fun serialize(tag: CompoundTag) {
        val executions = ListTag()
        synchronized(lock) {
            activeExecutions.values.forEach { execution ->
                executions.add(execution.toTag(SerializedState.RUNNING))
            }

            queuedExecutions.values.forEach { queue ->
                queue.forEach { request ->
                    executions.add(request.toTag(SerializedState.QUEUED))
                }
            }
        }

        tag.put("executions", executions)
    }

    override fun deserialize(tag: CompoundTag) {
        val serialized = tag.getList("executions", 10)
            .mapNotNull { entry ->
                (entry as? CompoundTag)?.let(SerializedExecution::fromTag)
            }

        synchronized(lock) {
            activeExecutions.values.forEach { it.job.cancel() }
            activeExecutions.clear()
            queuedExecutions.clear()
            pendingRestore.clear()

            serialized.forEach { execution ->
                pendingRestore
                    .getOrPut(execution.key, ::ArrayDeque)
                    .addLast(execution)
            }
        }

        restorePendingDefinitions()
    }

    override fun toString(): String = "CoroutineScope(coroutineContext=$coroutineContext)"

    fun registerSerializable(definition: SerializableCoroutineDefinition) {
        definitions[definition.key] = definition
        restorePending(definition.key)
    }

    fun registerSerializable(
        key: SerializableCoroutineKey,
        contextFactory: () -> SerializableCoroutineContextElement = { EmptySerializableContextElement },
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        registerSerializable(SerializableCoroutineDefinition(key, contextFactory, context, start, block))
    }

    fun hasActiveExecutions(): Boolean {
        return synchronized(lock) {
            activeExecutions.isNotEmpty() || queuedExecutions.values.any { it.isNotEmpty() }
        }
    }

    fun hasSerializableExecution(key: SerializableCoroutineKey): Boolean {
        return synchronized(lock) {
            activeExecutions.containsKey(key) ||
                queuedExecutions[key]?.isNotEmpty() == true ||
                pendingRestore[key]?.isNotEmpty() == true
        }
    }

    fun cancelAll() {
        synchronized(lock) {
            activeExecutions.values.forEach { it.job.cancel() }
            activeExecutions.clear()
            queuedExecutions.clear()
        }
    }

    fun cancelSerializable(key: SerializableCoroutineKey) {
        synchronized(lock) {
            activeExecutions.remove(key)?.job?.cancel()
            queuedExecutions.remove(key)
            pendingRestore.remove(key)
        }
    }

    fun launchSerializable(
        key: SerializableCoroutineKey,
        policy: LaunchPolicy = LaunchPolicy.CANCEL_OLD,
    ): Job {
        val definition = definitions[key]
            ?: error("Serializable coroutine is not registered for key=$key")

        val request = LaunchRequest(definition, definition.contextFactory())
        val active = synchronized(lock) { activeExecutions[key] }
        if (active == null) return startExecution(request)

        return when (policy) {
            LaunchPolicy.CANCEL_OLD -> {
                active.job.cancel()
                startExecution(request)
            }

            LaunchPolicy.DROP_NEW -> active.job

            LaunchPolicy.ENQUEUE -> {
                synchronized(lock) {
                    queuedExecutions.getOrPut(key, ::ArrayDeque).addLast(request)
                }
                active.job
            }
        }
    }

    fun launchSerializable(
        key: SerializableCoroutineKey,
        policy: LaunchPolicy = LaunchPolicy.CANCEL_OLD,
        contextFactory: () -> SerializableCoroutineContextElement = { EmptySerializableContextElement },
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        registerSerializable(key, contextFactory, context, start, block)
        return launchSerializable(key, policy)
    }

    private fun restorePendingDefinitions() {
        definitions.keys.forEach(::restorePending)
    }

    private fun restorePending(key: SerializableCoroutineKey) {
        val definition = definitions[key] ?: return
        val entries = synchronized(lock) { pendingRestore.remove(key)?.toList().orEmpty() }
        if (entries.isEmpty()) return

        entries.forEach { serialized ->
            val contextElement = definition.contextFactory().apply { load(serialized.contextTag) }
            when (serialized.state) {
                SerializedState.RUNNING -> startExecution(LaunchRequest(definition, contextElement))
                SerializedState.QUEUED -> enqueueRequest(LaunchRequest(definition, contextElement))
            }
        }
    }

    private fun enqueueRequest(request: LaunchRequest) {
        synchronized(lock) {
            val hasRunning = activeExecutions.containsKey(request.definition.key)
            if (!hasRunning) {
                startExecution(request)
                return
            }

            queuedExecutions
                .getOrPut(request.definition.key, ::ArrayDeque)
                .addLast(request)
        }
    }

    private fun startExecution(request: LaunchRequest): Job {
        val definition = request.definition
        val fullContext = definition.context + request.contextElement

        val job = launch(fullContext, definition.start, definition.block)
        job.invokeOnCompletion { onExecutionFinished(definition.key, job) }

        synchronized(lock) {
            activeExecutions[definition.key] = ExecutionRecord(definition.key, request.contextElement, job)
        }
        return job
    }

    private fun onExecutionFinished(key: SerializableCoroutineKey, finishedJob: Job) {
        val next: LaunchRequest?
        synchronized(lock) {
            val active = activeExecutions[key]
            if (active?.job != finishedJob) return
            activeExecutions.remove(key)

            val queue = queuedExecutions[key]
            next = queue?.pollFirst()
            if (queue != null && queue.isEmpty()) queuedExecutions.remove(key)
        }
        next?.let(::startExecution)
    }

    private enum class SerializedState { RUNNING, QUEUED }

    private data class SerializedExecution(
        val key: SerializableCoroutineKey,
        val state: SerializedState,
        val contextTag: CompoundTag,
    ) {
        companion object {
            fun fromTag(tag: CompoundTag): SerializedExecution? {
                val key = SerializableCoroutineKey.fromTag(tag) ?: return null
                val state = runCatching { SerializedState.valueOf(tag.getString("state")) }.getOrNull() ?: return null
                val context = tag.getCompound("context")
                return SerializedExecution(key, state, context)
            }
        }
    }

    private data class LaunchRequest(
        val definition: SerializableCoroutineDefinition,
        val contextElement: SerializableCoroutineContextElement,
    ) {
        fun toTag(state: SerializedState): CompoundTag {
            val contextTag = CompoundTag()
            contextElement.save(contextTag)
            return CompoundTag().apply {
                definition.key.save(this)
                putString("state", state.name)
                put("context", contextTag)
            }
        }
    }

    private data class ExecutionRecord(
        val key: SerializableCoroutineKey,
        val contextElement: SerializableCoroutineContextElement,
        val job: Job,
    ) {
        fun toTag(state: SerializedState): CompoundTag {
            val contextTag = CompoundTag()
            contextElement.save(contextTag)
            return CompoundTag().apply {
                key.save(this)
                putString("state", state.name)
                put("context", contextTag)
            }
        }
    }
}



