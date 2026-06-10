package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableMap
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

object OwnerScopeRegistry {
    private val scopes = ConcurrentLinkedQueue<WeakReference<OwnerScope>>()

    fun register(scope: OwnerScope) {
        cleanup()
        scopes += WeakReference(scope)
    }

    fun scopes(): List<OwnerScope> {
        val alive = mutableListOf<OwnerScope>()
        val stale = mutableListOf<WeakReference<OwnerScope>>()
        scopes.forEach { reference ->
            val scope = reference.get()
            if (scope == null) stale += reference else alive += scope
        }
        stale.forEach(scopes::remove)
        return alive
    }

    private fun cleanup() {
        scopes.removeIf { it.get() == null }
    }
}

abstract class OwnerScope(
    override val coroutineContext: CoroutineContext,
    private val onDirty: (() -> Unit)? = null,
) : SerializableCoroutineScope {
    val variables = VariableMap(onDirty)
    private val lock = Any()
    private val activeExecutions = ConcurrentHashMap<String, ActiveExecution>()
    private val queuedExecutions = ConcurrentHashMap<String, ArrayDeque<StoredExecution>>()
    private val pendingRestore = ConcurrentHashMap<String, ArrayDeque<SerializedExecution>>()

    init {
        OwnerScopeRegistry.register(this)
    }

    override fun serialize(tag: CompoundTag) {
        val executions = ListTag()
        synchronized(lock) {
            activeExecutions.values.forEach { executions.add(it.toTag(SerializedState.RUNNING)) }
            queuedExecutions.values.forEach { queue -> queue.forEach { executions.add(it.toTag(SerializedState.QUEUED)) } }
            pendingRestore.values.forEach { queue -> queue.forEach { executions.add(it.toTag()) } }
        }
        tag.put("executions", executions)
        tag.put("variables", CompoundTag().also(variables::serialize))
    }

    override fun deserialize(tag: CompoundTag) {
        val serialized = tag.getList("executions", 10)
            .mapNotNull { entry -> (entry as? CompoundTag)?.let(SerializedExecution::fromTag) }

        synchronized(lock) {
            activeExecutions.values.forEach { it.job.cancel() }
            activeExecutions.clear()
            queuedExecutions.clear()
            pendingRestore.clear()
            serialized.forEach { pendingRestore.getOrPut(it.branchKey, ::ArrayDeque).addLast(it) }
        }

        variables.deserialize(tag.getCompound("variables"))
        restorePending()
    }

    fun submit(
        definitionId: RuntimeDefinitionId,
        branchKey: String,
        launchPolicy: LaunchPolicy,
        configureState: (RuntimeExecutionState) -> Unit = {},
    ): Job {
        val active = synchronized(lock) { activeExecutions[branchKey] }
        val stored = createStoredExecution(definitionId, branchKey, configureState)

        if (active == null) return startStoredExecution(stored)

        return when (launchPolicy) {
            LaunchPolicy.CANCEL_OLD -> {
                synchronized(lock) { queuedExecutions.remove(branchKey) }
                active.job.cancel()
                startStoredExecution(stored)
            }
            LaunchPolicy.DROP_NEW -> active.job
            LaunchPolicy.ENQUEUE -> {
                synchronized(lock) { queuedExecutions.getOrPut(branchKey, ::ArrayDeque).addLast(stored) }
                markDirty()
                active.job
            }
        }
    }

    fun cancelBranch(branchKey: String) {
        synchronized(lock) {
            activeExecutions.remove(branchKey)?.job?.cancel()
            queuedExecutions.remove(branchKey)
            pendingRestore.remove(branchKey)
        }
        markDirty()
    }

    fun cancelDefinitions(prefix: String) {
        val branches = synchronized(lock) {
            activeExecutions.filterValues { it.definitionId.value.startsWith(prefix) }.keys.toList() +
                queuedExecutions.filterValues { queue -> queue.any { it.definitionId.value.startsWith(prefix) } }.keys +
                pendingRestore.filterValues { queue -> queue.any { it.definitionId.value.startsWith(prefix) } }.keys
        }
        branches.distinct().forEach(::cancelBranch)
    }

    fun cancelAll() {
        synchronized(lock) {
            activeExecutions.values.forEach { it.job.cancel() }
            activeExecutions.clear()
            queuedExecutions.clear()
            pendingRestore.clear()
        }
        markDirty()
    }

    fun hasActiveExecutions(): Boolean = synchronized(lock) {
        activeExecutions.isNotEmpty() || queuedExecutions.values.any { it.isNotEmpty() }
    }

    fun hasExecution(branchKey: String): Boolean = synchronized(lock) {
        activeExecutions.containsKey(branchKey) ||
            queuedExecutions[branchKey]?.isNotEmpty() == true ||
            pendingRestore[branchKey]?.isNotEmpty() == true
    }

    fun restorePending() {
        val ready = synchronized(lock) {
            val readyEntries = mutableListOf<SerializedExecution>()
            val iterator = pendingRestore.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val remaining = ArrayDeque<SerializedExecution>()
                entry.value.forEach { serialized ->
                    if (RuntimeDefinitionRegistry.contains(serialized.definitionId)) {
                        readyEntries += serialized
                    } else {
                        remaining += serialized
                    }
                }
                if (remaining.isEmpty()) iterator.remove() else entry.setValue(remaining)
            }
            readyEntries
        }

        ready.sortedBy { it.state.ordinal }.forEach {
            when (it.state) {
                SerializedState.RUNNING -> startSerializedExecution(it)
                SerializedState.QUEUED -> enqueueSerializedExecution(it)
            }
        }
    }

    fun registerSerializable(definition: SerializableCoroutineDefinition) {
        RuntimeDefinitionRegistry.register(
            RuntimeDefinition(
                id = definition.key.id(),
                contextFactory = RuntimeExecutionContextFactory {
                    SerializableDefinitionState(definition.contextFactory(), definition.context)
                },
                start = definition.start,
            ) {
                definition.block(this)
            }
        )
        restorePending()
    }

    fun registerSerializable(
        key: SerializableCoroutineKey,
        contextFactory: () -> SerializableCoroutineContextElement,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        registerSerializable(SerializableCoroutineDefinition(key, contextFactory, context, start, block))
    }

    fun launchSerializable(key: SerializableCoroutineKey, policy: LaunchPolicy = LaunchPolicy.CANCEL_OLD): Job {
        return submit(key.id(), key.branchKey(), policy)
    }

    fun launchSerializable(
        key: SerializableCoroutineKey,
        policy: LaunchPolicy = LaunchPolicy.CANCEL_OLD,
        contextFactory: () -> SerializableCoroutineContextElement,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ): Job {
        registerSerializable(key, contextFactory, context, start, block)
        return launchSerializable(key, policy)
    }

    fun cancelSerializable(key: SerializableCoroutineKey) {
        cancelBranch(key.branchKey())
    }

    fun hasSerializableExecution(key: SerializableCoroutineKey): Boolean = hasExecution(key.branchKey())

    private fun enqueueSerializedExecution(serialized: SerializedExecution) {
        synchronized(lock) {
            if (!activeExecutions.containsKey(serialized.branchKey)) {
                startSerializedExecution(serialized)
                return
            }
            queuedExecutions.getOrPut(serialized.branchKey, ::ArrayDeque)
                .addLast(StoredExecution(serialized.definitionId, serialized.branchKey, contextTag = serialized.contextTag.copy()))
        }
        markDirty()
    }

    private fun startSerializedExecution(serialized: SerializedExecution): Job {
        return startStoredExecution(
            StoredExecution(
                definitionId = serialized.definitionId,
                branchKey = serialized.branchKey,
                contextTag = serialized.contextTag.copy(),
            )
        )
    }

    private fun createStoredExecution(
        definitionId: RuntimeDefinitionId,
        branchKey: String,
        configureState: (RuntimeExecutionState) -> Unit,
    ): StoredExecution {
        val definition = RuntimeDefinitionRegistry.resolve(definitionId)
            ?: error("Runtime definition not found: $definitionId")
        val state = definition.contextFactory.create().also(configureState)
        return StoredExecution(definitionId, branchKey, preparedState = state)
    }

    private fun startStoredExecution(stored: StoredExecution): Job {
        val definition = RuntimeDefinitionRegistry.resolve(stored.definitionId)
        if (definition == null) {
            synchronized(lock) {
                pendingRestore.getOrPut(stored.branchKey, ::ArrayDeque)
                    .addLast(SerializedExecution(stored.definitionId, stored.branchKey, SerializedState.RUNNING, stored.snapshot()))
            }
            markDirty()
            return launch(start = CoroutineStart.LAZY) {}
        }

        val state = stored.preparedState ?: definition.contextFactory.create().also { runtimeState ->
            stored.contextTag?.copy()?.let(runtimeState::load)
        }
        state.installCancel { cancelBranch(stored.branchKey) }
        val context = definition.context + state.buildCoroutineContext()
        val job = launch(context, definition.start) {
            definition.block(this, state)
        }
        job.invokeOnCompletion { onExecutionFinished(stored.branchKey, job) }

        synchronized(lock) {
            activeExecutions[stored.branchKey] = ActiveExecution(stored.definitionId, stored.branchKey, state, job)
        }
        markDirty()
        return job
    }

    private fun onExecutionFinished(branchKey: String, finishedJob: Job) {
        val next: StoredExecution?
        synchronized(lock) {
            val active = activeExecutions[branchKey]
            if (active?.job != finishedJob) return
            activeExecutions.remove(branchKey)
            val queue = queuedExecutions[branchKey]
            next = if (queue != null && queue.isNotEmpty()) queue.removeFirst() else null
            if (queue != null && queue.isEmpty()) queuedExecutions.remove(branchKey)
        }
        markDirty()
        next?.let(::startStoredExecution)
    }

    fun markDirty() {
        onDirty?.invoke()
    }

    private data class StoredExecution(
        val definitionId: RuntimeDefinitionId,
        val branchKey: String,
        val preparedState: RuntimeExecutionState? = null,
        val contextTag: CompoundTag? = null,
    ) {
        fun snapshot(): CompoundTag {
            preparedState?.let { state ->
                return CompoundTag().also(state::save)
            }
            return contextTag?.copy() ?: CompoundTag()
        }

        fun toTag(state: SerializedState): CompoundTag = CompoundTag().apply {
            putString("definition_id", definitionId.value)
            putString("branch_key", branchKey)
            putString("state", state.name)
            put("context", snapshot())
        }
    }

    private data class ActiveExecution(
        val definitionId: RuntimeDefinitionId,
        val branchKey: String,
        val state: RuntimeExecutionState,
        val job: Job,
    ) {
        fun toTag(status: SerializedState): CompoundTag {
            val contextTag = CompoundTag().also(state::save)
            return StoredExecution(definitionId, branchKey, contextTag = contextTag).toTag(status)
        }
    }

    private enum class SerializedState {
        RUNNING,
        QUEUED,
    }

    private data class SerializedExecution(
        val definitionId: RuntimeDefinitionId,
        val branchKey: String,
        val state: SerializedState,
        val contextTag: CompoundTag,
    ) {
        companion object {
            fun fromTag(tag: CompoundTag): SerializedExecution? {
                val definitionId = tag.getString("definition_id").takeIf { it.isNotBlank() }?.let(::RuntimeDefinitionId) ?: return null
                val branchKey = tag.getString("branch_key").takeIf { it.isNotBlank() } ?: return null
                val state = runCatching { SerializedState.valueOf(tag.getString("state")) }.getOrNull() ?: return null
                return SerializedExecution(definitionId, branchKey, state, tag.getCompound("context"))
            }
        }

        fun toTag(): CompoundTag = CompoundTag().apply {
            putString("definition_id", definitionId.value)
            putString("branch_key", branchKey)
            putString("state", state.name)
            put("context", contextTag.copy())
        }
    }
}

private object EmptySerializableContextElement : AbstractCoroutineContextElement(Key), SerializableCoroutineContextElement {
    object Key : CoroutineContext.Key<EmptySerializableContextElement>
    override fun save(tag: CompoundTag) = Unit
}

private class SerializableDefinitionState(
    private val contextElement: SerializableCoroutineContextElement,
    private val baseContext: CoroutineContext,
) : RuntimeExecutionState {
    override fun save(tag: CompoundTag) {
        contextElement.save(tag)
    }

    override fun load(tag: CompoundTag) {
        contextElement.load(tag)
    }

    override fun buildCoroutineContext(): CoroutineContext = baseContext + contextElement
}

private fun SerializableCoroutineKey.id(): RuntimeDefinitionId = RuntimeDefinitionId("serializable:$this")
private fun SerializableCoroutineKey.branchKey(): String = toString()
