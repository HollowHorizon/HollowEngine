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

    fun linkWithMinecraft(level: Level, mcEntity: Entity): EntityId {
        val entity = getOrCreateById(mcEntity.id)
        level.geary.apply {
            entity.toGeary().set(mcEntity)
        }
        return entity.toULong()
    }

    fun remove(mcEntityId: Int) {
        val entity = idMap.remove(mcEntityId).toULong()
        if (read.exists(entity)) {
            remove.remove(entity)
        }
    }

    fun changeId(level: Level, oldId: Int, newId: Int) {
        level.geary.apply {
            val old = idMap.remove(oldId)
            val existing = idMap[newId]
            idMap[newId] = old
            if (existing != 0L) {
                old.toGeary().extend(existing.toGeary())
            }
        }
    }
}