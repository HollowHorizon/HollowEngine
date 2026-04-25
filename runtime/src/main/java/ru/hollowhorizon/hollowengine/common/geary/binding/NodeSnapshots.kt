package ru.hollowhorizon.hollowengine.common.geary.binding

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.LightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntityNodeSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import java.util.UUID

data class DormantRecord(
    val stableKey: UUID,
    val snapshot: EntitySnapshot,
) {
    val worldChunkX: Int = snapshot.requireWorldChunkX()
    val worldChunkZ: Int = snapshot.requireWorldChunkZ()
}

data class NodeMaterializedRecord(
    val stableKey: UUID,
    val snapshot: EntitySnapshot,
    val hostUuid: UUID?,
) {
    val isEntityBound: Boolean get() = hostUuid != null
    val isWorldBound: Boolean get() = hostUuid == null
}

data class ModelNodeEntry(
    val nodeId: UUID,
    val model: Model,
    val transform: TransformComponent,
)

data class LightNodeEntry(
    val nodeId: UUID,
    val light: LightComponent,
    val transform: TransformComponent,
)

fun EntitySnapshot.stableKeyOrNull(): UUID? = runCatching { stableKey }.getOrNull()

fun EntitySnapshot.requireStableKey(): UUID =
    stableKeyOrNull() ?: error("Entity snapshot is missing stable key.")

fun EntitySnapshot.isEntityBound(): Boolean = hostUuid != null
fun EntitySnapshot.isWorldBound(): Boolean = hostUuid == null
fun EntitySnapshot.hostUuidOrNull(): UUID? = hostUuid

fun EntitySnapshot.requireHostUuid(): UUID = hostUuid ?: error("Entity snapshot is not bound to an entity host.")

fun EntitySnapshot.requireWorldChunkX(): Int =
    worldChunkX ?: worldChunkFromTransform().first

fun EntitySnapshot.requireWorldChunkZ(): Int =
    worldChunkZ ?: worldChunkFromTransform().second

fun EntitySnapshot.worldLocalIdOrRandom(): UUID = worldLocalId ?: UUID.randomUUID()

private fun EntitySnapshot.worldChunkFromTransform(): Pair<Int, Int> {
    val transform = transformOrNull() ?: error("World-bound snapshot requires transform with world position.")
    val chunkPos = ChunkPos(BlockPos(transform.x.toInt(), transform.y.toInt(), transform.z.toInt()))
    return chunkPos.x to chunkPos.z
}

fun EntitySnapshot.nodeByIdOrNull(nodeId: UUID): EntityNodeSnapshot? =
    nodeList().firstOrNull { it.id == nodeId }

fun EntitySnapshot.transformOrNull(): TransformComponent? =
    rootNode().components.filterIsInstance<TransformComponent>().firstOrNull()
        ?: nodeList().asSequence().mapNotNull { node -> node.components.filterIsInstance<TransformComponent>().firstOrNull() }.firstOrNull()

fun EntitySnapshot.modelOrNull(): Model? =
    rootNode().components.filterIsInstance<Model>().firstOrNull()
        ?: nodeList().asSequence().mapNotNull { node -> node.components.filterIsInstance<Model>().firstOrNull() }.firstOrNull()

fun EntitySnapshot.modelNodes(): List<ModelNodeEntry> {
    val result = arrayListOf<ModelNodeEntry>()
    nodeList().forEach { node ->
        val model = node.components.filterIsInstance<Model>().firstOrNull() ?: return@forEach
        val transform = node.components.filterIsInstance<TransformComponent>().firstOrNull() ?: TransformComponent()
        result += ModelNodeEntry(node.id, model, transform)
    }
    return result
}

fun EntitySnapshot.lightNodes(): List<LightNodeEntry> {
    val result = arrayListOf<LightNodeEntry>()
    nodeList().forEach { node ->
        val light = node.components.filterIsInstance<LightComponent>().firstOrNull() ?: return@forEach
        val transform = node.components.filterIsInstance<TransformComponent>().firstOrNull() ?: TransformComponent()
        result += LightNodeEntry(node.id, light, transform)
    }
    return result
}

fun EntitySnapshot.withOrReplace(component: Component, nodeId: UUID? = null): EntitySnapshot {
    val targetNodeId = nodeId
        ?: nodeList().firstOrNull { node -> node.components.any { it::class == component::class } }?.id
        ?: rootNode().id
    val id = ComponentDescriptorRegistry.idFor(component::class)
        ?: error("Component descriptor not found for ${component::class.qualifiedName}")
    val updatedNodes = nodeList().map { node ->
        if (node.id != targetNodeId) node
        else {
            val merged = LinkedHashMap<ResourceLocation, Component>()
            node.components.forEach { existing ->
                val existingId = ComponentDescriptorRegistry.idFor(existing::class)
                    ?: error("Component descriptor not found for ${existing::class.qualifiedName}")
                merged[existingId] = existing
            }
            merged[id] = component
            node.copy(components = merged.values.toList())
        }
    }
    return withNodes(updatedNodes)
}

fun EntitySnapshot.removeComponents(predicate: (Component) -> Boolean, nodeId: UUID? = null): EntitySnapshot {
    val updatedNodes = nodeList().map { node ->
        if (nodeId != null && node.id != nodeId) node
        else node.copy(components = node.components.filterNot(predicate))
    }
    return withNodes(updatedNodes)
}

fun EntitySnapshot.withEntityBinding(hostUuid: UUID): EntitySnapshot =
    copy(hostUuid = hostUuid, worldChunkX = null, worldChunkZ = null, worldLocalId = null)

fun EntitySnapshot.withWorldBinding(position: Vec3, localId: UUID = UUID.randomUUID()): EntitySnapshot {
    val chunkPos = ChunkPos(BlockPos(position.x.toInt(), position.y.toInt(), position.z.toInt()))
    return copy(hostUuid = null, worldChunkX = chunkPos.x, worldChunkZ = chunkPos.z, worldLocalId = localId)
}

fun EntitySnapshot.withWorldChunkBinding(chunkX: Int, chunkZ: Int, localId: UUID = UUID.randomUUID()): EntitySnapshot =
    copy(hostUuid = null, worldChunkX = chunkX, worldChunkZ = chunkZ, worldLocalId = localId)

fun EntitySnapshot.withAddedNode(
    nodeId: UUID = UUID.randomUUID(),
    parentId: UUID = rootNode().id,
    nodeComponents: List<Component> = emptyList(),
): EntitySnapshot {
    val updated = nodeList() + EntityNodeSnapshot(id = nodeId, parentId = parentId, components = nodeComponents)
    return withNodes(updated)
}

fun EntitySnapshot.withRemovedNode(nodeId: UUID): EntitySnapshot {
    if (nodeId == rootNode().id) return this
    val toRemove = linkedSetOf(nodeId)
    var changed = true
    while (changed) {
        changed = false
        nodeList().forEach { node ->
            if (node.parentId in toRemove && node.id !in toRemove) {
                toRemove += node.id
                changed = true
            }
        }
    }
    return withNodes(nodeList().filter { it.id !in toRemove })
}

fun TransformComponent.withWorldPosition(position: Vec3): TransformComponent =
    withTranslation(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
