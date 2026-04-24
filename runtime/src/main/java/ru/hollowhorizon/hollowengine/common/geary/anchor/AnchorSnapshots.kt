package ru.hollowhorizon.hollowengine.common.geary.anchor

import ru.hollowhorizon.hollowengine.common.geary.api.Component
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.withComponent
import java.util.*
import kotlin.collections.toList

data class DormantRecord(
    val stableKey: UUID,
    val snapshot: EntitySnapshot,
) {
    val anchor: AnchorComponent = snapshot.requireAnchor()
}

data class MaterializedRecord(
    val stableKey: UUID,
    val snapshot: EntitySnapshot,
    val anchor: AnchorComponent,
)

fun EntitySnapshot.stableKeyOrNull(): UUID? = stableKey

fun EntitySnapshot.requireStableKey(): UUID =
    stableKeyOrNull() ?: error("Entity snapshot is missing stable key.")

fun EntitySnapshot.anchorOrNull(): AnchorComponent? = anchor

fun EntitySnapshot.requireAnchor(): AnchorComponent =
    anchorOrNull() ?: error("Entity snapshot is missing anchor.")

fun EntitySnapshot.transformOrNull(): TransformComponent? =
    components.filterIsInstance<TransformComponent>().firstOrNull()

fun EntitySnapshot.modelOrNull(): Model? =
    components.filterIsInstance<Model>().firstOrNull()

fun EntitySnapshot.primaryAnchorOrNull(): PrimaryAnchorObject? =
    if ((anchor as? EntityAnchor)?.primary == true) PrimaryAnchorObject() else null

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

fun EntitySnapshot.withIdentity(anchor: AnchorComponent): EntitySnapshot {
    return withComponent<AnchorComponent>(anchor)
}

fun worldAnchorFor(position: Vec3, localId: UUID = UUID.randomUUID()): WorldAnchor {
    val chunkPos = ChunkPos(BlockPos(position.x.toInt(), position.y.toInt(), position.z.toInt()))
    return WorldAnchor(chunkPos.x, chunkPos.z, localId)
}

fun TransformComponent.withWorldPosition(position: Vec3): TransformComponent =
    withTranslation(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
