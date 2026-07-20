package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.job
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.scripting.nodes.EntityNodeRuntime.ATTACHMENTS_KEY
import ru.hollowhorizon.hollowengine.common.scripting.nodes.EntityNodeRuntime.load
import ru.hollowhorizon.hollowengine.common.scripting.nodes.EntityNodeRuntime.save
import ru.hollowhorizon.hollowengine.common.scripting.state.StateContext
import java.util.*

/**
 * Runs and persists nodes attached to entities via `@file:Attach`. Each node is bound to a child of the
 * entity's [coroutineScope] (a `SerializableCoroutineScope` that Geary already saves/cancels with the
 * entity), so cancelling the entity scope tears its nodes down automatically.
 *
 * Persistence rides on the existing entity NBT: [save] / [load] are called from
 * `GearyRuntimeState.saveEntity` / `loadEntity`, storing attached node data under [ATTACHMENTS_KEY].
 */
object EntityNodeRuntime {
    private const val ATTACHMENTS_KEY = "NodeAttachments"

    private val managers = Collections.synchronizedMap(WeakHashMap<Entity, EntityNodeManager>())

    fun attach(entity: Entity, path: String, tag: CompoundTag? = null, context: StateContext? = null): Boolean =
        manager(entity).attach(path, tag, context)

    fun detach(entity: Entity, path: String): Boolean =
        managers[entity]?.detach(path) ?: false

    fun paths(entity: Entity): Set<String> = managers[entity]?.paths().orEmpty()

    fun save(entity: Entity, tag: CompoundTag) {
        val data = managers[entity]?.serialize()
        if (data == null || data.isEmpty) tag.remove(ATTACHMENTS_KEY) else tag.put(ATTACHMENTS_KEY, data)
    }

    fun load(entity: Entity, tag: CompoundTag) {
        if (!tag.contains(ATTACHMENTS_KEY, Tag.TAG_COMPOUND.toInt())) return
        manager(entity).deserialize(tag.getCompound(ATTACHMENTS_KEY))
    }

    fun remove(entity: Entity) {
        managers.remove(entity)
    }

    private fun manager(entity: Entity) = managers.getOrPut(entity) { EntityNodeManager(entity) }
}

/** Per-entity node store. Mirrors [NodeManager] but binds nodes to the entity's coroutine scope. */
class EntityNodeManager(private val entity: Entity) {
    private val nodes = mutableMapOf<String, RunningNode>()

    fun attach(path: String, tag: CompoundTag?, context: StateContext?): Boolean {
        if (nodes.containsKey(path)) return false
        val server = entity.server ?: return false

        val (script, executor) = buildNode(
            host = NodeHost.OfEntity(entity),
            parentScope = entity.coroutineScope,
            path = path,
            tag = tag,
            receivers = listOf(server, entity),
        ) ?: return false

        nodes[path] = RunningNode(script, executor, context)
        context?.let { executor.start(it) }
        return true
    }

    fun detach(path: String): Boolean {
        val removed = nodes.remove(path) ?: return false
        removed.script.coroutineContext.job.cancel()
        return true
    }

    fun paths(): Set<String> = nodes.keys.toSet()

    fun serialize(): CompoundTag {
        val tag = CompoundTag()
        nodes.forEach { (path, node) ->
            runCatching {
                val context = SerializationContext(node.script.server, CompoundTag())
                node.script.onSaveHandlers.forEach { it(context) }
                val nodeTag = CompoundTag()
                nodeTag.put("extras", context.tag)
                node.context?.let { nodeTag.put("states", it.serialize()) }
                tag.put(path, nodeTag)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while saving entity node '$path'", it)
            }
        }
        return tag
    }

    fun deserialize(tag: CompoundTag) {
        tag.allKeys.forEach { path ->
            runCatching {
                val nodeTag = tag.getCompound(path)
                val extras = nodeTag.getCompound("extras")
                val context = (nodeTag.get("states") as? CompoundTag)?.let { StateContext.deserialize(it) }
                attach(path, extras, context)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while deserializing entity node '$path'", it)
            }
        }
    }
}
