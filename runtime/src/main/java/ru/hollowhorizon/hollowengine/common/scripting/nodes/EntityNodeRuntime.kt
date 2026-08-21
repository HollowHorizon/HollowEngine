package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.job
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import ru.hollowhorizon.hollowengine.common.scripting.state.StateContext

/**
 * Entry point for the nodes attached to entities via `@file:Attach`.
 *
 * The managers themselves live in the entity's `HollowAttachments`, so nodes are created, moved between
 * dimensions, persisted and torn down together with everything else attached to that entity, instead of
 * through a registry of their own. Each node still hangs off a child of the entity's [coroutineScope],
 * so cancelling that scope tears its nodes down.
 */
object EntityNodeRuntime {
    fun attach(entity: Entity, path: String, tag: CompoundTag? = null, context: StateContext? = null): Boolean =
        GearyRuntimeState.attachments(entity).nodes.attach(path, tag, context)

    fun detach(entity: Entity, path: String): Boolean =
        managerOrNull(entity)?.detach(path) ?: false

    fun paths(entity: Entity): Set<String> = managerOrNull(entity)?.paths().orEmpty()

    /** Stops every attached node of [namespace] on every entity, keeping their state. */
    fun suspendNamespace(namespace: String) {
        forEachManager { it.suspendNamespace(namespace) }
    }

    /** Starts them again once the namespace is back. */
    fun resumeNamespace(namespace: String) {
        forEachManager { it.resumeNamespace(namespace) }
    }

    private fun managerOrNull(entity: Entity): EntityNodeManager? =
        GearyRuntimeState.attachmentsOrNull(entity)?.nodesOrNull

    private inline fun forEachManager(action: (EntityNodeManager) -> Unit) {
        GearyRuntimeState.allAttachments().forEach { attachments -> attachments.nodesOrNull?.let(action) }
    }
}

/** Per-entity node store. Mirrors [NodeManager] but binds nodes to the entity's coroutine scope. */
class EntityNodeManager(private val entity: Entity) {
    private val nodes = mutableMapOf<String, RunningNode>()

    /** Attached nodes whose namespace is currently unavailable. See [NodeManager] for the rationale. */
    private val dormant = mutableMapOf<String, CompoundTag>()

    fun attach(path: String, tag: CompoundTag?, context: StateContext?): Boolean {
        val canonicalPath = canonicalNodePath(path)
        if (nodes.containsKey(canonicalPath)) return false
        dormant.remove(canonicalPath)
        val server = entity.server ?: return false

        val (script, executor) = buildNode(
            host = NodeHost.OfEntity(entity),
            parentScope = entity.coroutineScope,
            path = canonicalPath,
            tag = tag,
            receivers = listOf(server, entity),
        ) ?: return false

        nodes[canonicalPath] = RunningNode(script, executor, context)
        context?.let { executor.start(it) }
        return true
    }

    fun detach(path: String): Boolean {
        val canonicalPath = canonicalNodePath(path)
        dormant.remove(canonicalPath)
        val removed = nodes.remove(canonicalPath) ?: return false
        removed.script.coroutineContext.job.cancel()
        return true
    }

    fun paths(): Set<String> = nodes.keys.toSet()

    fun serialize(): CompoundTag {
        val tag = CompoundTag()
        dormant.forEach { (path, nodeTag) -> tag.put(path, nodeTag) }
        nodes.forEach { (path, node) ->
            runCatching { tag.put(path, node.persist(node.script.server)) }
                .onFailure { HollowEngine.LOGGER.error("Error while saving entity node '$path'", it) }
        }
        return tag
    }

    fun deserialize(tag: CompoundTag) {
        tag.allKeys.forEach { path ->
            runCatching {
                val nodeTag = tag.getCompound(path)
                if (ScriptRegistry.source(ScriptRegistry.parse(path).namespace) == null) {
                    dormant[path] = nodeTag
                    return@runCatching
                }
                val extras = nodeTag.getCompound("extras")
                val context = (nodeTag.get("states") as? CompoundTag)?.let { StateContext.deserialize(it) }
                attach(path, extras, context)
            }.onFailure {
                HollowEngine.LOGGER.error("Error while deserializing entity node '$path'", it)
            }
        }
    }

    internal fun suspendNamespace(namespace: String) {
        nodes.filterKeys { path -> ScriptRegistry.parse(path).namespace == namespace }
            .forEach { (path, node) ->
                runCatching { dormant[path] = node.persist(node.script.server) }
                    .onFailure { HollowEngine.LOGGER.error("Error while suspending entity node '$path'", it) }
                nodes.remove(path)
                node.script.coroutineContext.job.cancel()
            }
    }

    internal fun resumeNamespace(namespace: String) {
        dormant.filterKeys { path -> ScriptRegistry.parse(path).namespace == namespace }
            .forEach { (path, nodeTag) ->
                dormant.remove(path)
                runCatching {
                    val context = (nodeTag.get("states") as? CompoundTag)?.let { StateContext.deserialize(it) }
                    attach(path, nodeTag.getCompound("extras"), context)
                }.onFailure { HollowEngine.LOGGER.error("Error while resuming entity node '$path'", it) }
            }
    }
}
