package ru.hollowhorizon.hollowengine.common.geary.tracking

import com.mineinabyss.geary.datatypes.GearyEntity
import com.mineinabyss.geary.helpers.entity
import com.mineinabyss.geary.modules.Geary
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap
import ru.hollowhorizon.hollowengine.common.geary.catchOp
import ru.hollowhorizon.hollowengine.common.geary.tracking.components.AddedToWorld

class MCEntity2Geary(val forceMainThread: Boolean = true) {
    private val entityMap = Int2LongOpenHashMap().apply { defaultReturnValue(-1) }

    context(world: Geary)
    operator fun get(bukkitEntity: MCEntity): GearyEntity? = synchronized(entityMap) {
        val id = entityMap.get(bukkitEntity.id)
        if (id == -1L) return null
        return with(world) { id.toGeary() }
    }

    context(world: Geary)
    operator fun get(entityId: Int): GearyEntity? = synchronized(entityMap) {
        val id = entityMap.get(entityId)
        if (id == -1L) return null
        return with(world) { id.toGeary() }
    }

    operator fun set(bukkit: MCEntity, entity: GearyEntity) = synchronized(entityMap) {
        entityMap[bukkit.id] = entity.id.toLong()
    }

    operator fun contains(entityId: Int): Boolean = synchronized(entityMap) { entityMap.containsKey(entityId) }

    fun remove(entityId: Int) = synchronized(entityMap) {
        entityMap.remove(entityId)
    }

    context(world: Geary)
    fun getOrCreate(entity: MCEntity): GearyEntity = synchronized(entityMap) {
        return get(entity) ?: run {
            if (forceMainThread) entity.server?.catchOp("Async geary entity creation for id ${entity.id}, type ${entity.type.descriptionId}")
            synchronized(entityMap) {
                world.entity { set(entity) }.also { fireAddToWorldEvent(entity, it) }
            }
        }
    }

    context(world: Geary)
    fun fireAddToWorldEvent(bukkit: MCEntity, entity: GearyEntity) = synchronized(entityMap) {
        entity.add<AddedToWorld>()
        val entityBinds = world.getAddon(EntityTracking).entityTypeBinds[bukkit.type.descriptionId]
        entityBinds.forEach { bind ->
            entity.extend(bind)
        }
    }

    fun fireRemoveFromWorldEvent(bukkit: MCEntity, entity: GearyEntity) = synchronized(entityMap) {
        with(entity.world) {
            entity.remove<AddedToWorld>()
        }
    }
}