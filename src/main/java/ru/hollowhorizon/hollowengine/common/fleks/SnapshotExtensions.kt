package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.*

fun World.loadSnapshotAdditive(entity: Entity, snapshot: Snapshot) {
    if (entityService.delayRemoval) {
        throw FleksSnapshotException("Snapshots cannot be loaded while a family iteration is in process")
    }

    if (entity !in entityService) {
        entityService.create(entity.id) { }
    }

    entityService.configureAdditive(entity, snapshot)
}

internal fun EntityService.configureAdditive(entity: Entity, snapshot: Snapshot) {
    val compMask = compMasks[entity.id]
    val components = snapshot.components

    components.forEach { cmp ->
        compMask.set(cmp.type().id)

        val holder = compService.wildcardHolder(cmp.type())
        holder.setWildcard(entity, cmp)
    }

    snapshot.tags.forEach {
        compMask.set(it.id)
        world.tagCache[it.id] = it
    }

    world.allFamilies.forEach { it.onEntityCfgChanged(entity, compMask) }
}