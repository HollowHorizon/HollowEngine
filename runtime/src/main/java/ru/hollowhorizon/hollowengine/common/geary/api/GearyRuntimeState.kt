package ru.hollowhorizon.hollowengine.common.geary.api

import com.mineinabyss.geary.modules.Geary
import kotlinx.coroutines.cancel
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.OwnerScopeRestoredEvent
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineScope
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.geary.GearyPlatform
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.encodeComponentsTo
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.loadComponentsFrom
import java.util.Collections
import java.util.WeakHashMap

private data class EntityState(
    var entityId: Long = UNINITIALIZED_ENTITY_ID,
    var gearyRemoved: Boolean = false,
    val coroutineScope: SerializableCoroutineScope,
)

object GearyRuntimeState {
    private val levelGeary = Collections.synchronizedMap(WeakHashMap<Level, Geary>())
    private val entityStates = Collections.synchronizedMap(WeakHashMap<MCEntity, EntityState>())

    fun initLevel(level: Level) {
        levelGeary.computeIfAbsent(level) { GearyPlatform.create(level) }
    }

    fun geary(level: Level): Geary =
        levelGeary[level] ?: error("Geary state is not initialized for $level")

    fun tick(level: Level) {
        geary(level).tick()
    }

    fun close(level: Level) {
        levelGeary.remove(level)?.application?.close()
    }

    fun initEntity(entity: MCEntity) {
        entityStates[entity] = EntityState(coroutineScope = EntityScope(entity))
    }

    fun entityId(entity: MCEntity): Long = state(entity).entityId

    fun setEntityId(entity: MCEntity, entityId: Long) {
        state(entity).entityId = entityId
    }

    fun coroutineScope(entity: Entity): SerializableCoroutineScope = state(entity as MCEntity).coroutineScope

    fun saveEntity(entity: Entity, tag: CompoundTag) {
        val state = state(entity as MCEntity)
        if (state.entityId != UNINITIALIZED_ENTITY_ID) {
            val gearyTag = CompoundTag()
            with(geary(entity.level())) {
                state.entityId.encodeComponentsTo(gearyTag)
            }
            if (!gearyTag.isEmpty) tag.put("geary", gearyTag)
        }

        val scopeTag = CompoundTag()
        state.coroutineScope.serialize(scopeTag)
        tag.put("EntityScope", scopeTag)
    }

    fun loadEntity(entity: Entity, tag: CompoundTag) {
        loadComponentsFrom(entity, tag.getCompound("geary"))
        val state = state(entity as MCEntity)
        state.coroutineScope.deserialize(tag.getCompound("EntityScope"))
        EventBus.post(OwnerScopeRestoredEvent(entity))
    }

    fun onSetLevel(entity: Entity, newLevel: Level) {
        val state = state(entity as MCEntity)
        if (state.entityId != UNINITIALIZED_ENTITY_ID && !state.gearyRemoved) {
            state.entityId = move(entity.level(), newLevel, state.entityId, entity).toLong()
        }
    }

    fun onRemove(entity: Entity) {
        if (entity is Player) return
        val state = state(entity as MCEntity)
        if (state.gearyRemoved) return
        state.gearyRemoved = true
        if (state.entityId != UNINITIALIZED_ENTITY_ID) {
            removeEntity(entity.level(), entity.id, state.entityId)
        }
        state.coroutineScope.cancel()
    }

    fun onSetId(entity: Entity, newId: Int, previousId: Int) {
        val state = state(entity as MCEntity)
        if (!state.gearyRemoved && state.entityId != UNINITIALIZED_ENTITY_ID) {
            state.entityId = bind(entity.level(), entity, newId, previousId).toLong()
        }
    }

    private fun state(entity: MCEntity) =
        entityStates[entity] ?: error("Entity state is not initialized for $entity")
}
