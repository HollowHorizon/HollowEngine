package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.serialization.setPersisting
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSyncPolicy
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.applySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.geary.sync.SyncableComponents
import ru.hollowhorizon.hollowengine.common.geary.sync.Syncs

private const val SNAPSHOT_KEY = "snapshot"

context(world: Geary)
fun Long.encodeComponentsTo(tag: CompoundTag) {
    val snapshot = with(world) { snapshotOf(this@encodeComponentsTo.toGeary()) }
    if (snapshot.components.isEmpty() && snapshot.prefabRefs.isEmpty()) return
    tag.put(SNAPSHOT_KEY, EntitySerialization.serializeToNbt(snapshot))
}

fun Long.loadComponentsFrom(entity: MCEntity, tag: CompoundTag) {
    with(entity.level().geary) {
        val encoded = tag.get(SNAPSHOT_KEY) ?: tag
        applySnapshot(entity.entity, EntitySerialization.deserializeFromNbt(encoded))
    }
}

fun GearyEntity.loadComponentsFrom(snapshot: EntitySnapshot) {
    world.applySnapshot(this, snapshot)
}

fun GearyEntity.loadComponentsFrom(decodedEntityData: DecodedEntityData) {
    world.applySnapshot(this, EntitySnapshot(components = decodedEntityData.persistingComponents.filterIsInstance<Component>()))
}

fun GearyEntity.setAllSyncablePersisting(
    components: Collection<Component>,
    override: Boolean = true,
    noEvent: Boolean = false,
) {
    components.forEach {
        if (override || !has(it::class)) {
            val descriptor = EntitySerialization.descriptorFor(it) ?: return@forEach
            if (descriptor.syncPolicy == ComponentSyncPolicy.SYNC) {
                setRelation(
                    world.getAddon(SyncableComponents).syncs,
                    world.componentId(it::class),
                    Syncs,
                    noEvent
                )
            }
            setPersisting(it, it::class, noEvent)
        }
    }
}
