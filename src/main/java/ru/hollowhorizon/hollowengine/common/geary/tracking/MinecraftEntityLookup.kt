package ru.hollowhorizon.hollowengine.common.geary.tracking

import com.mineinabyss.geary.datatypes.EntityId
import com.mineinabyss.geary.engine.EntityProvider
import com.mineinabyss.geary.engine.EntityReadOperations
import com.mineinabyss.geary.engine.archetypes.EntityRemove
import it.unimi.dsi.fastutil.ints.Int2LongArrayMap
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.geary.api.geary

class MinecraftEntityLookup(
    val entityProvider: EntityProvider,
    val read: EntityReadOperations,
    val remove: EntityRemove,
) {
    private val idMap = Int2LongArrayMap()

    fun getOrCreateById(mcEntityId: Int): Long {
        val id = idMap.getOrPut(mcEntityId) {
            entityProvider.create().toLong()
        }
        return id
    }

    fun createDetached(level: Level, mcEntity: Entity): EntityId {
        val entity = entityProvider.create().toLong()
        level.geary.apply {
            entity.toGeary().set(mcEntity)
        }
        return entity.toULong()
    }

    fun bind(level: Level, mcEntityId: Int, entity: Long, mcEntity: Entity, previousMcEntityId: Int = mcEntityId): EntityId {
        if (previousMcEntityId != mcEntityId && idMap.get(previousMcEntityId) == entity) {
            idMap.remove(previousMcEntityId)
        }

        val existing = idMap.get(mcEntityId)
        if (existing != 0L && existing != entity && read.exists(existing.toULong())) {
            level.geary.apply {
                entity.toGeary().extend(existing.toGeary())
            }
            remove.remove(existing.toULong())
        }

        idMap[mcEntityId] = entity
        level.geary.apply {
            entity.toGeary().set(mcEntity)
        }
        return entity.toULong()
    }

    fun remove(mcEntityId: Int): Boolean {
        if (!idMap.containsKey(mcEntityId)) return false
        val entity = idMap.remove(mcEntityId).toULong()
        if (read.exists(entity)) {
            remove.remove(entity)
        }
        return true
    }
}
