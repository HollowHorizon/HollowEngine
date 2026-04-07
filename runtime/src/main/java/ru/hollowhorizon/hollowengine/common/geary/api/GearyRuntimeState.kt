package ru.hollowhorizon.hollowengine.common.geary.api

import com.mineinabyss.geary.modules.Geary
import kotlinx.coroutines.cancel
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.OwnerScopeRestoredEvent
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineScope
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
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
        MaterializationRuntimeState.init(level)
    }

    fun geary(level: Level): Geary =
        levelGeary[level] ?: error("Geary state is not initialized for $level")

    fun tick(level: Level) {
        geary(level).tick()
        MaterializationRuntimeState.service(level).tick()
    }

    fun close(level: Level) {
        MaterializationRuntimeState.close(level)
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
        MaterializationRuntimeState.service(entity.level()).saveEntityChildren(entity.uuid, tag)

        val scopeTag = CompoundTag()
        state.coroutineScope.serialize(scopeTag)
        tag.put("EntityScope", scopeTag)
    }

    fun loadEntity(entity: Entity, tag: CompoundTag) {
        loadComponentsFrom(entity, tag.getCompound("geary"))
        val state = state(entity as MCEntity)
        if (state.entityId != UNINITIALIZED_ENTITY_ID) {
            MaterializationRuntimeState.service(entity.level()).ensurePrimaryEntity(entity, state.entityId)
        }
        MaterializationRuntimeState.service(entity.level()).loadEntityChildren(entity, tag)
        state.coroutineScope.deserialize(tag.getCompound("EntityScope"))
        EventBus.post(OwnerScopeRestoredEvent(entity))
    }

    fun onSetLevel(entity: Entity, newLevel: Level) {
        val state = state(entity as MCEntity)
        if (state.entityId != UNINITIALIZED_ENTITY_ID && !state.gearyRemoved) {
            val oldLevel = entity.level()
            val previousRuntimeId = state.entityId
            val oldMaterialization = MaterializationRuntimeState.service(oldLevel)
            oldMaterialization.moveEntityAnchors(entity.uuid, newLevel)
            state.entityId = move(oldLevel, newLevel, previousRuntimeId, entity).toLong()
            oldMaterialization.detachPrimaryEntity(entity.uuid, previousRuntimeId)
            MaterializationRuntimeState.service(newLevel).ensurePrimaryEntity(entity, state.entityId)
        }
    }

    fun onRemove(entity: Entity) {
        if (entity is Player) return
        val state = state(entity as MCEntity)
        if (state.gearyRemoved) return
        state.gearyRemoved = true
        if (state.entityId != UNINITIALIZED_ENTITY_ID) {
            val materialization = MaterializationRuntimeState.service(entity.level())
            materialization.onHostRemoved(entity.uuid)
            materialization.detachPrimaryEntity(entity.uuid, state.entityId)
            removeEntity(entity.level(), entity.id, state.entityId)
        }
        state.coroutineScope.cancel()
    }

    fun onSetId(entity: Entity, newId: Int, previousId: Int) {
        val state = state(entity as MCEntity)
        if (!state.gearyRemoved && state.entityId != UNINITIALIZED_ENTITY_ID) {
            state.entityId = bind(entity.level(), entity, newId, previousId).toLong()
            MaterializationRuntimeState.service(entity.level()).ensurePrimaryEntity(entity, state.entityId)
        }
    }

    private fun state(entity: MCEntity) =
        entityStates[entity] ?: error("Entity state is not initialized for $entity")
}
