@file:JvmName("GearyHelper")

package ru.hollowhorizon.hollowengine.common.geary.api

import com.mineinabyss.geary.datatypes.Entity
import com.mineinabyss.geary.datatypes.EntityId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.get
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.applySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.geary.tracking.MinecraftEntityLookup

interface GearyProvider {
    val `hollowengine$geary`: Geary
}

interface EntityProvider {
    var `hollowengine$entity`: Long
}

val Level.geary: Geary
    get() = GearyRuntimeState.geary(this)

const val UNINITIALIZED_ENTITY_ID: Long = -1L

private fun create(level: Level, entity: MCEntity): EntityId {
    val created = level.geary.get<MinecraftEntityLookup>().createDetached(level, entity)
    GearyRuntimeState.setEntityId(entity, created.toLong())
    if (level.getEntity(entity.id) == entity) {
        return level.geary.get<MinecraftEntityLookup>().bind(level, entity.id, created.toLong(), entity)
    }
    return created
}

fun ensureEntity(level: Level, entity: MCEntity): EntityId {
    val existing = GearyRuntimeState.entityId(entity)
    if (existing != UNINITIALIZED_ENTITY_ID) return existing.toULong()
    return create(level, entity)
}

val MCEntity.entityId: Long
    get() = ensureEntity(level(), this).toLong()

val MCEntity.entity: Entity
    get() = with(level().geary) { entityId.toGeary() }

fun bind(level: Level, entity: MCEntity, entityId: Int = entity.id, previousEntityId: Int = entity.id): EntityId {
    val gearyEntity = ensureEntity(level, entity)
    return level.geary.get<MinecraftEntityLookup>().bind(level, entityId, gearyEntity.toLong(), entity, previousEntityId)
}

fun bindIfInitialized(level: Level, entity: MCEntity): EntityId? {
    val current = GearyRuntimeState.entityId(entity)
    if (current == UNINITIALIZED_ENTITY_ID) return null

    val bound = level.geary.get<MinecraftEntityLookup>().bind(level, entity.id, current, entity)
    GearyRuntimeState.setEntityId(entity, bound.toLong())
    return bound
}

fun move(old: Level, new: Level, entity: Long, mcEntity: MCEntity): EntityId {
    val snapshot = with(old.geary) { snapshotOf(entity.toGeary()) }
    with(new.geary) {
        val newEntityId = create(new, mcEntity)
        applySnapshot(newEntityId.toGeary(), snapshot)
        return newEntityId
    }
}

fun removeEntity(level: Level, entity: Int, gearyEntity: Long = UNINITIALIZED_ENTITY_ID) {
    if (level.geary.get<MinecraftEntityLookup>().remove(entity)) return
    if (gearyEntity != UNINITIALIZED_ENTITY_ID) {
        level.geary.entityRemoveProvider.remove(gearyEntity.toULong())
    }
}

@SubscribeEvent
fun onEntityLoaded(event: EntityLoadedEvent) {
    bindIfInitialized(event.entity.level(), event.entity)
}
