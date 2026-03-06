package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import kotlinx.coroutines.withContext
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnEventBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.LocalVariableDeclaration
import ru.hollowhorizon.hollowengine.common.codeblocks.createContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.CodeBlockInterpreter
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.EventListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ScriptFile(
    val system: BlocksSystem,
    val path: String,
    val allBlocks: List<BlockModel>,
) {
    private val declaredLocalVariables = allBlocks.flatMap { it.walk() }
        .filterIsInstance<LocalVariableDeclaration>()
        .filter { it.variableName.isNotBlank() }
        .associate { it.variableName to it.expressionType }

    val instances = CopyOnWriteArrayList<ScriptInstance>()
    val functions = allBlocks.filterIsInstance<CustomBlock>().associateBy { it.function }

    var isEnabled: Boolean = true
        private set

    private val listeners = CopyOnWriteArrayList<ListenerBinding>()
    private val queuedLaunches = ConcurrentHashMap<BranchKey, ArrayDeque<PendingLaunch>>()
    private val offlineLaunches = ConcurrentHashMap<OwnerKey, ArrayDeque<PendingLaunch>>()

    fun setEnabled(value: Boolean) {
        if (isEnabled == value) return
        isEnabled = value
        system.markDirty()
        if (!value) {
            stopAll()
        } else {
            startAllTriggers()
        }
    }

    @Deprecated("Use setEnabled(true). Trigger execution is now event-driven and bound to EntityScope.")
    fun startAllTriggers() {
        unregisterEventListeners()
        allBlocks.filterIsInstance<StartBlock>().forEach { trigger ->
            if (trigger is EventDrivenStartBlock<*>) {
                registerEventListener(trigger)
            } else {
                launchLegacyInstance(trigger)
            }
        }
    }

    @Deprecated("Use setEnabled(false).")
    fun stopAll() {
        unregisterEventListeners()
        queuedLaunches.clear()
        offlineLaunches.clear()
        instances.toList().forEach { it.stop() }
        instances.clear()
        system.markDirty()
    }

    fun serialize(tag: CompoundTag) {
        tag.putBoolean("enabled", isEnabled)

        val instancesList = ListTag()
        instances.forEach { instance ->
            instancesList.add(CompoundTag().apply(instance::serialize))
        }
        tag.put("instances", instancesList)
    }

    fun deserialize(tag: CompoundTag) {
        isEnabled = if (tag.contains("enabled")) tag.getBoolean("enabled") else true
        instances.clear()

        val instancesList = tag.getList("instances", 10)
        instancesList.forEach { entry ->
            val instTag = entry as? CompoundTag ?: return@forEach
            val rootId = instTag.getUUID("rootBlockId")
            val root = allBlocks.find { b -> b.uuid == rootId } as? StartBlock ?: return@forEach
            val ownerEntityId = if (instTag.contains("ownerEntityId")) instTag.getUUID("ownerEntityId") else null
            val instanceId = if (instTag.contains("instanceId")) instTag.getUUID("instanceId") else UUID.randomUUID()

            val instance = ScriptInstance(
                ownerFile = this,
                rootBlock = root,
                ownerEntityId = ownerEntityId,
                instanceId = instanceId,
            )
            declaredLocalVariables.forEach { (name, type) ->
                instance.localVariables[name] = createContainer(type)
            }
            instance.deserialize(instTag)
            instances.add(instance)
            instance.resume()
        }

        if (isEnabled) {
            startAllTriggers()
        } else {
            unregisterEventListeners()
        }
    }

    internal fun resolveEntityScope(entityId: UUID): EntityScope? {
        val entity = findEntityById(entityId) ?: return null
        return entity.coroutineScope as? EntityScope
    }

    internal fun onInstanceCompleted(instance: ScriptInstance) {
        instances.remove(instance)
        system.markDirty()
        dequeueNext(instance.branchKey)
    }

    internal fun onInstanceUnavailable(instance: ScriptInstance) {
        instances.remove(instance)
        enqueueOffline(instance.ownerKey, PendingLaunch(instance.rootBlock, instance.ownerKey, EmptyCoroutineContext))
        system.markDirty()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <E : Event> registerEventListener(trigger: EventDrivenStartBlock<E>) {
        val listener = object : EventListener<E> {
            override fun onEvent(event: E) {
                if (!isEnabled) return
                if (!trigger.shouldHandle(event)) return

                val entity = trigger.resolveScopeEntity(event) ?: return
                resumeInstancesForOwner(entity.uuid)
                launchConfiguredInstance(
                    rootBlock = trigger as StartBlock,
                    ownerKey = entity.uuid.toOwnerKey(),
                    triggerContext = ScriptEventContextElement(event),
                )
            }
        }

        EventBus.registerNoInline(trigger.eventType as Class<Event>, listener as EventListener<Event>)
        listeners += ListenerBinding(trigger.eventType as Class<Event>, listener as EventListener<Event>)
    }

    fun launchSignal(signal: ScriptSignal) {
        if (!isEnabled) return
        matchingSignalHandlers(signal).forEach { handler ->
            launchConfiguredInstance(
                rootBlock = handler,
                ownerKey = signal.owner,
                triggerContext = ScriptSignalContextElement(signal),
            )
        }
    }

    suspend fun callSignal(signal: ScriptSignal) {
        if (!isEnabled) return
        matchingSignalHandlers(signal).forEach { handler ->
            val body = handler.next ?: return@forEach
            withContext(ScriptSignalContextElement(signal)) {
                CodeBlockInterpreter<Unit>(body).execute()
            }
        }
    }

    fun getActiveBranchSnapshots(): List<ActiveBranchSnapshot> {
        val grouped = instances.groupBy { it.branchKey }
        return grouped.map { (key, branchInstances) ->
            val active = branchInstances.first()
            val queueLength = queuedLaunches[key]?.size ?: 0
            val state = if (active.ownerEntityId != null && resolveEntityScope(active.ownerEntityId) == null) {
                BranchState.FROZEN
            } else {
                BranchState.RUNNING
            }
            ActiveBranchSnapshot(key, active.rootBlock.repeatPolicy, state, null, queueLength)
        }
    }

    private fun launchConfiguredInstance(
        rootBlock: StartBlock,
        ownerKey: OwnerKey,
        triggerContext: CoroutineContext,
    ) {
        val branchKey = rootBlock.buildBranchKey(path, ownerKey)
        val active = instances.filter { it.branchKey == branchKey }
        val pending = PendingLaunch(rootBlock, ownerKey, triggerContext)

        when (rootBlock.repeatPolicy) {
            RepeatPolicy.PARALLEL -> launchInstanceNow(pending)
            RepeatPolicy.IGNORE -> if (active.isEmpty()) launchOrQueueOffline(pending)
            RepeatPolicy.RESTART -> {
                queuedLaunches.remove(branchKey)
                active.forEach { it.stop() }
                launchOrQueueOffline(pending)
            }
            RepeatPolicy.QUEUE -> {
                if (active.isEmpty()) {
                    launchOrQueueOffline(pending)
                } else {
                    queuedLaunches.getOrPut(branchKey, ::ArrayDeque).addLast(pending)
                    system.markDirty()
                }
            }
        }
    }

    private fun launchOrQueueOffline(pending: PendingLaunch) {
        val ownerEntityId = pending.ownerKey.entityIdOrNull()
        if (ownerEntityId != null && resolveEntityScope(ownerEntityId) == null) {
            enqueueOffline(pending.ownerKey, pending)
            system.markDirty()
            return
        }
        launchInstanceNow(pending)
    }

    private fun launchInstanceNow(pending: PendingLaunch): ScriptInstance {
        val instance = ScriptInstance(
            ownerFile = this,
            rootBlock = pending.rootBlock,
            ownerEntityId = pending.ownerKey.entityIdOrNull(),
            triggerContext = pending.triggerContext,
        )

        declaredLocalVariables.forEach { (name, type) ->
            if (!instance.localVariables.contains(name)) {
                instance.localVariables[name] = createContainer(type)
            }
        }

        instances.add(instance)
        instance.start()
        system.markDirty()
        return instance
    }

    private fun launchLegacyInstance(rootBlock: StartBlock): ScriptInstance {
        return launchInstanceNow(PendingLaunch(rootBlock, OwnerKey.Global, EmptyCoroutineContext))
    }

    private fun unregisterEventListeners() {
        listeners.forEach { binding ->
            EventBus.unregisterNoInline(binding.eventType, binding.listener)
        }
        listeners.clear()
    }

    private fun findEntityById(entityId: UUID): Entity? {
        val server = system.owner
        server.playerList.players.firstOrNull { it.uuid == entityId }?.let { return it }
        server.allLevels.forEach { level ->
            level.getEntity(entityId)?.let { return it }
        }
        return null
    }

    private fun matchingSignalHandlers(signal: ScriptSignal): List<OnEventBlock> {
        return allBlocks
            .filterIsInstance<OnEventBlock>()
            .filter { it.signalScope == signal.scope && it.signalName == signal.name }
    }

    private fun dequeueNext(branchKey: BranchKey) {
        val queue = queuedLaunches[branchKey] ?: return
        val next = queue.removeFirstOrNull()
        if (queue.isEmpty()) {
            queuedLaunches.remove(branchKey)
        }
        next?.let(::launchOrQueueOffline)
    }

    private fun enqueueOffline(ownerKey: OwnerKey, pending: PendingLaunch) {
        offlineLaunches.getOrPut(ownerKey, ::ArrayDeque).addLast(pending)
    }

    private fun resumeInstancesForOwner(entityId: UUID) {
        val ownerKey = entityId.toOwnerKey()
        instances.filter { it.ownerKey == ownerKey }.forEach { it.resume() }
        val queue = offlineLaunches.remove(ownerKey) ?: return
        queue.forEach(::launchInstanceNow)
    }

    private data class ListenerBinding(
        val eventType: Class<Event>,
        val listener: EventListener<Event>,
    )

    private data class PendingLaunch(
        val rootBlock: StartBlock,
        val ownerKey: OwnerKey,
        val triggerContext: CoroutineContext,
    )
}
