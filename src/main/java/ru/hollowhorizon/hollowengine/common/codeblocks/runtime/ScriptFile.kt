package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnEventBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.LocalVariableDeclaration
import ru.hollowhorizon.hollowengine.common.codeblocks.createContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.scoped
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import ru.hollowhorizon.hollowengine.common.coroutines.*
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.EventListener
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

class ScriptFile(
    val system: BlocksSystem,
    val path: String,
    val allBlocks: List<BlockModel>,
) {
    private val declaredLocalVariables = allBlocks.flatMap { it.walk() }
        .filterIsInstance<LocalVariableDeclaration>()
        .filter { it.variableName.isNotBlank() }
        .associate { it.variableName to it.expressionType }

    val functions = allBlocks.filterIsInstance<CustomBlock>().associateBy { it.function }

    var isEnabled: Boolean = true
        private set

    private val listeners = CopyOnWriteArrayList<ListenerBinding>()
    private val definitionIds = linkedMapOf<UUID, RuntimeDefinitionId>()

    fun setEnabled(value: Boolean, persist: Boolean = true, update: Boolean = true) {
        if (isEnabled == value) return
        isEnabled = value
        if (update) {
            if (value) {
                startRouting()
            } else {
                stopRouting()
                system.cancelDefinitions(definitionPrefix())
            }
        }
        if (persist) system.markDirty()
    }

    internal fun loadEnabledState(value: Boolean) {
        isEnabled = value
    }

    fun registerRuntimeDefinitions() {
        unregisterRuntimeDefinitions()
        allBlocks.filterIsInstance<StartBlock>().filterNot { it is CustomBlock }.forEach(::registerRuntimeDefinition)
    }

    fun unregisterRuntimeDefinitions() {
        RuntimeDefinitionRegistry.unregisterByPrefix(definitionPrefix())
        definitionIds.clear()
    }

    fun serialize(tag: CompoundTag) {
        tag.putBoolean("enabled", isEnabled)
    }

    fun deserialize(tag: CompoundTag) {
        isEnabled = if (tag.contains("enabled")) tag.getBoolean("enabled") else true
    }

    fun startRouting() {
        unregisterEventListeners()
        if (!isEnabled) return

        allBlocks.filterIsInstance<StartBlock>().filterNot { it is CustomBlock }.forEach { trigger ->
            if (trigger is EventDrivenStartBlock<*>) {
                registerEventListener(trigger)
            } else {
                launchServerTrigger(trigger)
            }
        }
    }

    fun stopRouting() {
        unregisterEventListeners()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <E : Event> registerEventListener(trigger: EventDrivenStartBlock<E>) {
        val listener = object : EventListener<E> {
            override fun onEvent(event: E) {
                if (!isEnabled) return
                if (!trigger.shouldHandle(event)) return

                val entity = trigger.resolveScopeEntity(event) ?: return
                val ownerKey = entity.uuid.toOwnerKey()
                val ownerScope = system.ownerScope(ownerKey) ?: return
                submit(ownerScope, trigger as StartBlock, ownerKey, trigger.repeatPolicy) { state ->
                    state.initialize(ownerKey, transientContext = ScriptEventContextElement(event))
                }
            }
        }

        EventBus.registerNoInline(trigger.eventType as Class<Event>, listener as EventListener<Event>)
        listeners += ListenerBinding(trigger.eventType as Class<Event>, listener as EventListener<Event>)
    }

    fun launchSignal(signal: ScriptSignal) {
        if (!isEnabled) return
        matchingSignalHandlers(signal).forEach { handler ->
            val ownerScope = system.ownerScope(signal.owner) ?: return@forEach
            submit(ownerScope, handler, signal.owner, handler.repeatPolicy) { state ->
                state.initialize(signal.owner, signal)
            }
        }
    }

    suspend fun callSignal(signal: ScriptSignal) {
        if (!isEnabled) return
        matchingSignalHandlers(signal).forEach { handler ->
            val definition = ensureRuntimeDefinition(handler)
            val state = CodeBlockExecutionState(this, handler, definition.id).apply {
                initialize(signal.owner, signal)
            }
            populateLocalVariables(state.instance)
            coroutineScope {
                withContext(definition.context + state.buildCoroutineContext()) {
                    definition.block(this@coroutineScope, state)
                }
            }
        }
    }

    internal fun populateLocalVariables(instance: ScriptInstance) {
        declaredLocalVariables.forEach { (name, type) ->
            if (name !in instance.localVariables) {
                instance.localVariables[name] = createContainer(type)
            }
        }
    }

    private fun launchServerTrigger(rootBlock: StartBlock) {
        submit(system.serverScope, rootBlock, OwnerKey.Global, rootBlock.repeatPolicy) { state ->
            state.initialize(OwnerKey.Global)
        }
    }

    private fun submit(
        ownerScope: OwnerScope,
        rootBlock: StartBlock,
        ownerKey: OwnerKey,
        launchPolicy: LaunchPolicy,
        configureState: (CodeBlockExecutionState) -> Unit,
    ) {
        val definition = ensureRuntimeDefinition(rootBlock)
        val branchKey = rootBlock.buildBranchKey(path, ownerKey).asRuntimeBranchKey()
        ownerScope.submit(definition.id, branchKey, launchPolicy) { state ->
            val execution = state as CodeBlockExecutionState
            execution.initialize(ownerKey)
            populateLocalVariables(execution.instance)
            configureState(execution)
            execution.instance.installCancel { ownerScope.cancelBranch(branchKey) }
        }
    }

    private fun unregisterEventListeners() {
        listeners.forEach { binding ->
            EventBus.unregisterNoInline(binding.eventType, binding.listener)
        }
        listeners.clear()
    }

    private fun matchingSignalHandlers(signal: ScriptSignal): List<OnEventBlock> {
        return allBlocks
            .filterIsInstance<OnEventBlock>()
            .filter { it.signalScope == signal.scope && it.signalName == signal.name }
    }

    private fun ensureRuntimeDefinition(startBlock: StartBlock): RuntimeDefinition {
        val definitionId = definitionIdFor(startBlock)
        definitionIds[startBlock.uuid] = definitionId
        return RuntimeDefinitionRegistry.resolve(definitionId) ?: registerRuntimeDefinition(startBlock)
    }

    private fun registerRuntimeDefinition(startBlock: StartBlock): RuntimeDefinition {
        val definitionId = definitionIdFor(startBlock)
        val definition = RuntimeDefinition(
            id = definitionId,
            contextFactory = { CodeBlockExecutionState(this, startBlock, definitionId) },
        ) { state ->
            val execution = state as CodeBlockExecutionState
            try {
                scoped {
                    CodeBlockInterpreter<Unit>(startBlock).execute()
                }
            } catch (_: SkipScriptEventExecution) {
                // Routed event did not match this branch.
            } catch (_: CancellationException) {
                // Expected for repeat policies and scope lifecycle.
            } catch (t: Throwable) {
                HollowCore.LOGGER.error(
                    "Script {} failed at block {}",
                    path,
                    execution.instance.currentBlockId(),
                    t,
                )
                throw t
            } finally {
                execution.instance.detachStack()
                execution.instance.updateCurrentBlockId(null)
                system.markDirty()
            }
        }
        RuntimeDefinitionRegistry.register(definition)
        definitionIds[startBlock.uuid] = definitionId
        return definition
    }

    private fun definitionIdFor(startBlock: StartBlock): RuntimeDefinitionId {
        return definitionIds[startBlock.uuid] ?: RuntimeDefinitionId("codeblocks:$path#${startBlock.uuid}")
    }

    private fun definitionPrefix(): String = "codeblocks:$path#"

    private data class ListenerBinding(
        val eventType: Class<Event>,
        val listener: EventListener<Event>,
    )
}
