package ru.hollowhorizon.hollowengine.common.geary.api

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import kotlinx.coroutines.cancel
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.OwnerScopeRestoredEvent
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineScope
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.components.NoAi
import ru.hollowhorizon.hollowengine.common.geary.components.NoAiRuntime
import ru.hollowhorizon.hollowengine.common.geary.components.ai.AIComponentSystems
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import java.util.Collections
import java.util.IdentityHashMap
import java.util.LinkedHashMap
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

private data class EntityState(
    var runtimeId: Long = UNINITIALIZED_ENTITY_ID,
    var removed: Boolean = false,
    var dirty: Boolean = false,
    val componentsById: LinkedHashMap<net.minecraft.resources.ResourceLocation, Any> = linkedMapOf(),
    val coroutineScope: SerializableCoroutineScope,
)

object GearyRuntimeState {
    private val levelStates = Collections.synchronizedMap(WeakHashMap<Level, Unit>())
    private val entityStates = Collections.synchronizedMap(IdentityHashMap<MCEntity, EntityState>())
    private val ids = AtomicLong(1L)

    fun initLevel(level: Level) {
        levelStates[level] = Unit
        MaterializationRuntimeState.init(level)
    }

    fun tick(level: Level) {
        val entries = synchronized(entityStates) {
            entityStates.entries
                .filter { (entity, state) -> entity.level() == level && !state.removed }
                .map { it.key to it.value }
        }
        entries.forEach { (entity, state) ->
            val hasNoAi = state.componentsById.values.any { it is NoAi }
            NoAiRuntime.apply(entity, hasNoAi)
            AIComponentSystems.tickEntity(entity, state.componentsById)
        }
        MaterializationRuntimeState.service(level).tick()
    }

    fun close(level: Level) {
        MaterializationRuntimeState.close(level)
        synchronized(entityStates) {
            entityStates.entries.removeIf { (entity, _) -> entity.level() == level }
        }
        levelStates.remove(level)
    }

    fun initEntity(entity: MCEntity) {
        entityStates.putIfAbsent(entity, EntityState(coroutineScope = EntityScope(entity)))
    }

    fun componentsById(entity: MCEntity): LinkedHashMap<net.minecraft.resources.ResourceLocation, Any> =
        state(entity).componentsById

    fun markDirty(entity: MCEntity) {
        state(entity).dirty = true
    }

    fun entityId(entity: MCEntity): Long = state(entity).runtimeId

    fun ensureEntity(level: Level, entity: MCEntity): Long {
        val state = state(entity)
        if (state.runtimeId == UNINITIALIZED_ENTITY_ID) {
            state.runtimeId = ids.getAndIncrement()
            MaterializationRuntimeState.service(level).ensurePrimaryEntity(entity)
        }
        return state.runtimeId
    }

    fun bind(level: Level, entity: MCEntity, entityId: Int, previousEntityId: Int): Long {
        val runtimeId = ensureEntity(level, entity)
        if (entityId != previousEntityId) markDirty(entity)
        return runtimeId
    }

    fun bindIfInitialized(level: Level, entity: MCEntity): Long? {
        val state = stateOrNull(entity) ?: return null
        if (state.runtimeId == UNINITIALIZED_ENTITY_ID) return null
        MaterializationRuntimeState.service(level).ensurePrimaryEntity(entity)
        return state.runtimeId
    }

    fun move(old: Level, new: Level, entity: Long, mcEntity: MCEntity): Long {
        val state = state(mcEntity)
        MaterializationRuntimeState.service(old).moveEntityAnchors(mcEntity.uuid, new)
        state.runtimeId = ids.getAndIncrement()
        MaterializationRuntimeState.service(new).ensurePrimaryEntity(mcEntity)
        return state.runtimeId
    }

    fun removeEntity(level: Level, entity: Int, gearyEntity: Long) {
        val target = stateByRuntime(gearyEntity) ?: return
        target.removed = true
        target.componentsById.clear()
        MaterializationRuntimeState.service(level).detachPrimaryEntity(targetHostUuid(gearyEntity))
    }

    fun coroutineScope(entity: Entity): SerializableCoroutineScope = state(entity).coroutineScope

    fun saveEntity(entity: Entity, tag: CompoundTag) {
        try {
            val state = state(entity)
            if (state.componentsById.isNotEmpty()) {
                val snapshot = EntitySnapshot(components = state.componentsById.values.toList())
                tag.put("geary", EntitySerialization.serializeToNbt(snapshot))
            }
            MaterializationRuntimeState.service(entity.level()).saveEntityChildren(entity.uuid, tag)

            val scopeTag = CompoundTag()
            state.coroutineScope.serialize(scopeTag)
            tag.put("EntityScope", scopeTag)
            state.dirty = false
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Failed to save entity $entity", e)
        }
    }

    fun loadEntity(entity: Entity, tag: CompoundTag) {
        val encoded = tag.get("geary")
        if (encoded != null) {
            val snapshot = EntitySerialization.tryDeserializeFromNbt(encoded, "entity ${entity.id} snapshot")
            if (snapshot != null) {
                val byId = state(entity).componentsById
                byId.clear()
                snapshot.componentById().forEach { (id, component) -> byId[id] = component }
            }
        }

        MaterializationRuntimeState.service(entity.level()).loadEntityChildren(entity, tag)
        state(entity).coroutineScope.deserialize(tag.getCompound("EntityScope"))
        EventBus.post(OwnerScopeRestoredEvent(entity))
    }

    fun onSetLevel(entity: Entity, newLevel: Level) {
        val state = stateOrNull(entity) ?: return
        if (state.runtimeId == UNINITIALIZED_ENTITY_ID || state.removed) return
        val oldLevel = entity.level()
        MaterializationRuntimeState.service(oldLevel).moveEntityAnchors(entity.uuid, newLevel)
        state.runtimeId = move(oldLevel, newLevel, state.runtimeId, entity)
    }

    fun onRemove(entity: Entity) {
        val state = stateOrNull(entity) ?: return
        if (state.removed) return
        state.removed = true
        if (entity !is Player) {
            val materialization = MaterializationRuntimeState.service(entity.level())
            materialization.onHostRemoved(entity.uuid)
            materialization.detachPrimaryEntity(entity.uuid)
        }
        state.coroutineScope.cancel()
        NoAiRuntime.cleanup(entity)
        AIComponentSystems.cleanup(entity)
        entityStates.remove(entity)
    }

    fun onSetId(entity: Entity, newId: Int, previousId: Int) {
        val state = stateOrNull(entity) ?: return
        if (!state.removed && state.runtimeId != UNINITIALIZED_ENTITY_ID && newId != previousId) {
            markDirty(entity)
        }
    }

    private fun stateByRuntime(runtimeId: Long): EntityState? =
        entityStates.values.firstOrNull { it.runtimeId == runtimeId }

    private fun targetHostUuid(runtimeId: Long): java.util.UUID =
        entityStates.entries.firstOrNull { it.value.runtimeId == runtimeId }?.key?.uuid ?: java.util.UUID(0L, 0L)

    private fun stateOrNull(entity: MCEntity): EntityState? = entityStates[entity]

    private fun state(entity: MCEntity): EntityState =
        entityStates[entity] ?: error("Entity state is not initialized for $entity")
}
