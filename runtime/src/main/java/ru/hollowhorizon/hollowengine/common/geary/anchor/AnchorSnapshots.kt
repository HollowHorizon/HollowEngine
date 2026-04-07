package ru.hollowhorizon.hollowengine.common.geary.anchor

import com.mineinabyss.geary.datatypes.Component
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import java.util.UUID

data class DormantRecord(
    val stableKey: UUID,
    val snapshot: EntitySnapshot,
) {
    val anchor: AnchorComponent = snapshot.requireAnchor()
}

data class MaterializedRecord(
    val stableKey: UUID,
    val runtimeId: Long,
    val anchor: AnchorComponent,
)

fun EntitySnapshot.stableKeyOrNull(): UUID? =
    components.filterIsInstance<StableKeyComponent>().firstOrNull()?.value

fun EntitySnapshot.requireStableKey(): UUID =
    stableKeyOrNull() ?: error("Entity snapshot is missing StableKeyComponent.")

fun EntitySnapshot.anchorOrNull(): AnchorComponent? {
    val entityAnchor = components.filterIsInstance<EntityAnchor>().firstOrNull()
    if (entityAnchor != null) return entityAnchor
    return components.filterIsInstance<WorldAnchor>().firstOrNull()
}

fun EntitySnapshot.requireAnchor(): AnchorComponent =
    anchorOrNull() ?: error("Entity snapshot is missing an anchor component.")

fun EntitySnapshot.transformOrNull(): TransformComponent? =
    components.filterIsInstance<TransformComponent>().firstOrNull()

fun EntitySnapshot.modelOrNull(): Model? =
    components.filterIsInstance<Model>().firstOrNull()

fun EntitySnapshot.primaryAnchorOrNull(): PrimaryAnchorObject? =
    components.filterIsInstance<PrimaryAnchorObject>().firstOrNull()

fun EntitySnapshot.withOrReplace(component: Component): EntitySnapshot {
    val id = ComponentDescriptorRegistry.idFor(component::class)
        ?: error("Component descriptor not found for ${component::class.qualifiedName}")
    val merged = LinkedHashMap<ResourceLocation, Component>()
    components.forEach { existing ->
        val existingId = ComponentDescriptorRegistry.idFor(existing::class)
            ?: error("Component descriptor not found for ${existing::class.qualifiedName}")
        merged[existingId] = existing
    }
    merged[id] = component
    return copy(components = merged.values.toList())
}

fun EntitySnapshot.removeComponents(predicate: (Component) -> Boolean): EntitySnapshot =
    copy(components = components.filterNot(predicate))

fun EntitySnapshot.withIdentity(anchor: AnchorComponent, stableKey: UUID): EntitySnapshot {
    return this
        .removeComponents {
            it is StableKeyComponent || it is EntityAnchor || it is WorldAnchor || it is PrimaryAnchorObject
        }
        .withOrReplace(StableKeyComponent(stableKey))
        .withOrReplace(anchor)
}

fun worldAnchorFor(position: Vec3, localId: UUID = UUID.randomUUID()): WorldAnchor {
    val chunkPos = ChunkPos(BlockPos(position.x.toInt(), position.y.toInt(), position.z.toInt()))
    return WorldAnchor(chunkPos.x, chunkPos.z, localId)
}

fun TransformComponent.withWorldPosition(position: Vec3): TransformComponent = copy(
    x = position.x.toFloat(),
    y = position.y.toFloat(),
    z = position.z.toFloat(),
)
