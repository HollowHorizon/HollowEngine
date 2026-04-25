package ru.hollowhorizon.hollowengine.common.geary.binding

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.LightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.LevelSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.Snapshot
import java.util.UUID

val ROOT_COMPONENT_ID: UUID = UUID(0L, 1L)

data class DormantRecord(
    val id: UUID,
    val snapshot: LevelSnapshot,
) {
    val worldChunkX: Int = snapshot.requireWorldChunkX()
    val worldChunkZ: Int = snapshot.requireWorldChunkZ()
}

data class NodeMaterializedRecord(
    val snapshotId: UUID,
    val snapshot: Snapshot,
    val hostEntity: Entity?,
) {
    val hostEntityUuid: UUID? get() = hostEntity?.uuid
    val isEntityBound: Boolean get() = hostEntity != null
}

data class SnapshotComponentView(
    val id: UUID = ROOT_COMPONENT_ID,
    val components: List<Component>,
)

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

fun LevelSnapshot.requireWorldChunkX(): Int = worldChunkFromTransform().first

fun LevelSnapshot.requireWorldChunkZ(): Int = worldChunkFromTransform().second

private fun Snapshot.worldChunkFromTransform(): Pair<Int, Int> {
    val transform = transformOrNull() ?: error("Level snapshot requires transform with world position.")
    val chunkPos = ChunkPos(BlockPos(transform.x.toInt(), transform.y.toInt(), transform.z.toInt()))
    return chunkPos.x to chunkPos.z
}

fun Snapshot.snapshotIdOrNull(): UUID? = when (this) {
    is EntitySnapshot -> entity?.uuid
    is LevelSnapshot -> id
}

fun Snapshot.hostEntityUuidOrNull(): UUID? = (this as? EntitySnapshot)?.entity?.uuid

fun Snapshot.nodeByIdOrNull(nodeId: UUID): SnapshotComponentView? =
    if (nodeId == ROOT_COMPONENT_ID) SnapshotComponentView(components = components) else null

fun Snapshot.transformOrNull(): TransformComponent? =
    components.filterIsInstance<TransformComponent>().firstOrNull()

fun Snapshot.modelOrNull(): Model? =
    components.filterIsInstance<Model>().firstOrNull()

fun Snapshot.modelNodes(): List<ModelNodeEntry> {
    val model = components.filterIsInstance<Model>().firstOrNull() ?: return emptyList()
    val transform = components.filterIsInstance<TransformComponent>().firstOrNull() ?: TransformComponent()
    return listOf(ModelNodeEntry(ROOT_COMPONENT_ID, model, transform))
}

fun Snapshot.lightNodes(): List<LightNodeEntry> {
    val light = components.filterIsInstance<LightComponent>().firstOrNull() ?: return emptyList()
    val transform = components.filterIsInstance<TransformComponent>().firstOrNull() ?: TransformComponent()
    return listOf(LightNodeEntry(ROOT_COMPONENT_ID, light, transform))
}

fun <T : Snapshot> T.withOrReplace(component: Component, nodeId: UUID? = null): T {
    if (nodeId != null && nodeId != ROOT_COMPONENT_ID) return this
    val id = ComponentDescriptorRegistry.idFor(component::class)
        ?: error("Component descriptor not found for ${component::class.qualifiedName}")
    val merged = LinkedHashMap<ResourceLocation, Component>()
    components.forEach { existing ->
        val existingId = ComponentDescriptorRegistry.idFor(existing::class)
            ?: error("Component descriptor not found for ${existing::class.qualifiedName}")
        merged[existingId] = existing
    }
    merged[id] = component
    return withComponents(merged.values.toList())
}

fun <T : Snapshot> T.removeComponents(predicate: (Component) -> Boolean, nodeId: UUID? = null): T {
    if (nodeId != null && nodeId != ROOT_COMPONENT_ID) return this
    return withComponents(components.filterNot(predicate))
}

@Suppress("UNCHECKED_CAST")
fun <T : Snapshot> T.withComponents(updatedComponents: List<Component>): T =
    when (this) {
        is EntitySnapshot -> copy(components = updatedComponents).also { it.entity = entity }
        is LevelSnapshot -> copy(components = updatedComponents)
    } as T

fun LevelSnapshot.withWorldBinding(position: Vec3): LevelSnapshot =
    withOrReplace((transformOrNull() ?: TransformComponent()).withWorldPosition(position))
