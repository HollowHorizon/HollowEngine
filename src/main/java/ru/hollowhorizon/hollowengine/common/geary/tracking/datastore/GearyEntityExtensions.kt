package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.serialization.setPersisting
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSyncPolicy
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.geary.sync.SyncableComponents
import ru.hollowhorizon.hollowengine.common.geary.sync.Syncs
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity

private const val SNAPSHOT_KEY = "snapshot"

context(world: Geary)
fun Long.encodeComponentsTo(tag: CompoundTag) {
    val snapshot = with(world) { snapshotOf(this@encodeComponentsTo.toGeary()) }
    if (snapshot.components.isEmpty() && snapshot.prefabRefs.isEmpty()) return
    tag.put(SNAPSHOT_KEY, EntitySerialization.serializeToNbt(snapshot))
}

fun loadComponentsFrom(entity: MCEntity, tag: CompoundTag) {
    val encoded = tag.get(SNAPSHOT_KEY) ?: tag
    EntitySerialization.tryDeserializeInto(entity, encoded, "entity ${entity.id} NBT snapshot")
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
