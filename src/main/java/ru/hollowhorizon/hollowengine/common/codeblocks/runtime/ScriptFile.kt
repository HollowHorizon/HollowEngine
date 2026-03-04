package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.LocalVariableDeclaration
import ru.hollowhorizon.hollowengine.common.codeblocks.createContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.EventListener
import java.util.UUID
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

    val instances = CopyOnWriteArrayList<ScriptInstance>()
    val functions = allBlocks.filterIsInstance<CustomBlock>().associateBy { it.function }

    var isEnabled: Boolean = true
        private set

    private val listeners = CopyOnWriteArrayList<ListenerBinding>()

    fun setEnabled(value: Boolean) {
        if (isEnabled == value) return
        isEnabled = value
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
        instances.toList().forEach { it.stop() }
        instances.clear()
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

    internal fun unregisterInstance(instance: ScriptInstance) {
        instances.remove(instance)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <E : Event> registerEventListener(trigger: EventDrivenStartBlock<E>) {
        val listener = object : EventListener<E> {
            override fun onEvent(event: E) {
                if (!isEnabled) return
                if (!trigger.shouldHandle(event)) return

                val entity = trigger.resolveScopeEntity(event) ?: return
                launchNewInstance(trigger as StartBlock, entity.uuid, event)
            }
        }

        EventBus.registerNoInline(trigger.eventType as Class<Event>, listener as EventListener<Event>)
        listeners += ListenerBinding(trigger.eventType as Class<Event>, listener as EventListener<Event>)
    }

    private fun launchNewInstance(rootBlock: StartBlock, ownerEntityId: UUID, triggerEvent: Event): ScriptInstance {
        val instance = ScriptInstance(this, rootBlock, ownerEntityId, triggerEvent = triggerEvent)

        declaredLocalVariables.forEach { (name, type) ->
            if (!instance.localVariables.contains(name)) {
                instance.localVariables[name] = createContainer(type)
            }
        }

        instances.add(instance)
        instance.start()
        return instance
    }

    private fun launchLegacyInstance(rootBlock: StartBlock): ScriptInstance {
        val instance = ScriptInstance(this, rootBlock, ownerEntityId = null)

        declaredLocalVariables.forEach { (name, type) ->
            if (!instance.localVariables.contains(name)) {
                instance.localVariables[name] = createContainer(type)
            }
        }

        instances.add(instance)
        instance.start()
        return instance
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

    private data class ListenerBinding(
        val eventType: Class<Event>,
        val listener: EventListener<Event>,
    )
}
