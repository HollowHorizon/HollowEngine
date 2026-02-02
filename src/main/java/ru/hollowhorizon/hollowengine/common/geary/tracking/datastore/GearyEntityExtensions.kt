package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.components.relations.InstanceOf
import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.Entity
import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.geary.helpers.component
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.serialization.components.Persists
import com.mineinabyss.geary.serialization.getAllPersisting
import com.mineinabyss.geary.serialization.setPersisting
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.sync.SyncableComponents
import ru.hollowhorizon.hollowengine.common.geary.sync.Syncs
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity

context(world: Geary)
fun Long.encodeComponentsTo(tag: CompoundTag) = with(world) {
    with(toGeary()) {
        val persisting = getAllPersisting()
        if (persisting.isEmpty() && getRelations<InstanceOf?, Any?>().isEmpty()) return
        persisting.forEach {
            getRelation<Persists>(component(it::class))?.hash = it.hashCode()
        }
        tag.encodeComponents(persisting, type)
    }
}

fun Long.loadComponentsFrom(entity: MCEntity, tag: CompoundTag) {
    with(entity.level().geary) {
        with(toGeary()) {
            loadComponentsFrom(tag.decodeComponents())
        }
    }
}

fun GearyEntity.loadComponentsFrom(decodedEntityData: DecodedEntityData) {
    val (components, type) = decodedEntityData
    setAllSyncablePersisting(components)
    with(world) {
        type.forEach { extend(it.toGeary()) }
    }
}

fun Entity.setAllSyncablePersisting(
    components: Collection<Component>,
    override: Boolean = true,
    noEvent: Boolean = false,
) {
    components.forEach {
        if (override || !has(it::class)) {
            val syncs = world.run { componentId(it::class).toGeary().has<Syncs>() }
            if (syncs) {
                setRelation(
                    world.getAddon(SyncableComponents).syncs,
                    world.componentId(it::class),
                    Syncs, noEvent
                )
            }
            setPersisting(it, it::class, noEvent)
        }
    }
}