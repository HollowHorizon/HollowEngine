package ru.hollowhorizon.hollowengine.common.geary.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.data.NbtDataStore
import ru.hollowhorizon.hollowengine.common.geary.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSyncPolicy
import ru.hollowhorizon.hollowengine.common.geary.components.NoAi
import ru.hollowhorizon.hollowengine.common.geary.components.NoAiRuntime
import ru.hollowhorizon.hollowengine.common.geary.components.ai.AIComponentSystems
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.sync.ComponentSync
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.scripting.nodes.EntityNodeRuntime
import java.util.*
import java.util.concurrent.atomic.AtomicLong

private class EntityState(
    var entity: MCEntity,
    var data: NbtDataStore? = null,
    var runtimeId: Long = UNINITIALIZED_ENTITY_ID,
    val coroutineScope: CoroutineScope,
) {
    val components = ComponentStore()

    /**
     * Rises with every batch [ComponentSync] sends for this entity on the server, and records the last
     * batch applied on the client.
     */
    var syncVersion: Long = 0L

    /** What the clients tracking this entity were last told, so a batch can carry only the difference. */
    var lastSyncedComponents: Map<ResourceLocation, Component> = emptyMap()

    init {
        components.onChange = { ComponentSync.markDirty(entity) }
    }
}

private data class LevelEntityState(
    val byUuid: MutableMap<UUID, EntityState> = linkedMapOf(),
)

/** The pieces of an [EntityState] that outlive the entity instance they were attached to. */
private class PendingTransfer(
    val components: Map<ResourceLocation, Component>?,
    val data: NbtDataStore?,
)

private data class SideTransferState(
    val pendingByEntityUuid: MutableMap<UUID, PendingTransfer> = linkedMapOf(),
    val transferringEntityUuids: MutableSet<UUID> = linkedSetOf(),
)

object GearyRuntimeState {
    private const val ENTITY_SNAPSHOT_NBT = "EntitySnapshot"
    private const val ENTITY_DATA_NBT = "HollowEngineData"

    private val levelStates = Collections.synchronizedMap(WeakHashMap<Level, LevelEntityState>())
    private val sideTransferState = Collections.synchronizedMap(linkedMapOf<Boolean, SideTransferState>())
    private val ids = AtomicLong(1L)

    private val noAiId by lazy {
        ComponentDescriptorRegistry.idFor(NoAi::class)
            ?: error("Component descriptor not found for ${NoAi::class.qualifiedName}")
    }

    fun initLevel(level: Level) {
        NodeRuntimeState.init(level)
        levelState(level)
    }

    fun tick(level: Level) {
        val states = synchronized(levelStates) {
            levelStates[level]?.byUuid?.values?.toList().orEmpty()
        }
        states.forEach { state ->
            val entity = state.entity
            if (entity.level() != level) return@forEach
            if (state.runtimeId == UNINITIALIZED_ENTITY_ID) state.runtimeId = ids.getAndIncrement()
            val components = state.components.readOnly
            NoAiRuntime.apply(entity, components.containsKey(noAiId))
            AIComponentSystems.tickEntity(entity, components)
        }
    }

    fun close(level: Level) {
        NodeRuntimeState.close(level)
        synchronized(levelStates) {
            levelStates.remove(level)?.byUuid?.values?.forEach { it.coroutineScope.cancel() }
        }
    }

    fun componentsById(entity: MCEntity): MutableMap<ResourceLocation, Any> = state(entity).components.asMutableMap()

    /** The components that are allowed over the network, i.e. the ones whose descriptor is `@Syncable`. */
    fun syncableComponents(entity: Entity): Map<ResourceLocation, Component> {
        val state = stateOrNull(entity.level(), entity.uuid) ?: return emptyMap()
        return state.components.readOnly.filterKeys { id ->
            ComponentDescriptorRegistry.descriptorOrNull(id)?.syncPolicy == ComponentSyncPolicy.SYNC
        }
    }

    fun syncVersion(entity: Entity): Long = stateOrNull(entity.level(), entity.uuid)?.syncVersion ?: 0L

    fun setSyncVersion(entity: Entity, version: Long) {
        state(entity).syncVersion = version
    }

    fun nextSyncVersion(entity: Entity): Long = state(entity).let { ++it.syncVersion }

    fun lastSyncedComponents(entity: Entity): Map<ResourceLocation, Component> =
        stateOrNull(entity.level(), entity.uuid)?.lastSyncedComponents ?: emptyMap()

    fun setLastSyncedComponents(entity: Entity, components: Map<ResourceLocation, Component>) {
        state(entity).lastSyncedComponents = components
    }

    fun markDirty(entity: MCEntity) {
        stateOrNull(entity.level(), entity.uuid)?.runtimeId = ids.getAndIncrement()
    }

    fun ensureEntity(level: Level, entity: MCEntity): Long {
        val state = stateOrNull(level, entity.uuid) ?: return UNINITIALIZED_ENTITY_ID
        if (state.runtimeId == UNINITIALIZED_ENTITY_ID) state.runtimeId = ids.getAndIncrement()
        return state.runtimeId
    }

    fun bind(level: Level, entity: MCEntity, entityId: Int, previousEntityId: Int): Long {
        val runtimeId = ensureEntity(level, entity)
        if (runtimeId != UNINITIALIZED_ENTITY_ID && entityId != previousEntityId) markDirty(entity)
        return runtimeId
    }

    fun bindIfInitialized(level: Level, entity: MCEntity): Long? {
        val state = stateOrNull(level, entity.uuid) ?: return null
        if (state.entity !== entity) levelState(level).byUuid[entity.uuid] = rebindStateEntity(state, entity)
        val rebound = levelState(level).byUuid[entity.uuid] ?: return null
        if (rebound.runtimeId == UNINITIALIZED_ENTITY_ID) rebound.runtimeId = ids.getAndIncrement()
        return rebound.runtimeId
    }

    fun move(old: Level, new: Level, mcEntity: MCEntity): Long {
        val oldState = levelState(old).byUuid.remove(mcEntity.uuid) ?: return UNINITIALIZED_ENTITY_ID
        val rebound = if (oldState.entity !== mcEntity) rebindStateEntity(oldState, mcEntity) else oldState
        rebound.runtimeId = ids.getAndIncrement()
        levelState(new).byUuid[mcEntity.uuid] = rebound
        return rebound.runtimeId
    }

    fun coroutineScope(entity: Entity): CoroutineScope = state(entity).coroutineScope

    /** The entity's data store, or null when it has never been written to. Creates nothing. */
    fun entityDataOrNull(entity: Entity): NbtDataStore? = stateOrNull(entity.level(), entity.uuid)?.data

    /** The entity's data store, creating both it and the entity's state on first use. */
    fun entityData(entity: Entity): NbtDataStore = state(entity).dataOrCreate()

    fun saveEntity(entity: Entity, tag: CompoundTag) {
        val state = stateOrNull(entity.level(), entity.uuid)
        if (state == null) {
            EntityNodeRuntime.save(entity, tag)
            return
        }

        try {
            val snapshot = state.components.snapshot(entity)
            if (snapshot == null) {
                tag.remove(ENTITY_SNAPSHOT_NBT)
            } else {
                tag.put(ENTITY_SNAPSHOT_NBT, EntitySerialization.serializeToNbt(snapshot))
            }

            val data = state.data
            if (data == null || data.isEmpty()) {
                tag.remove(ENTITY_DATA_NBT)
            } else {
                tag.put(ENTITY_DATA_NBT, data.save())
            }

            EntityNodeRuntime.save(entity, tag)
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Failed to save entity {} ({})", entity.id, entity.uuid, e)
        }
    }

    fun loadEntity(entity: Entity, tag: CompoundTag) {
        val hasNodes = tag.contains("NodeAttachments", Tag.TAG_COMPOUND.toInt())
        val hasData = tag.contains(ENTITY_DATA_NBT, Tag.TAG_COMPOUND.toInt())
        val hasComponents = tag.contains(ENTITY_SNAPSHOT_NBT, Tag.TAG_COMPOUND.toInt())
        if (!hasComponents && !hasNodes && !hasData) return

        val state = state(entity)
        if (hasComponents) {
            val snapshot = EntitySerialization.deserializeFromNbt(tag.getCompound(ENTITY_SNAPSHOT_NBT))
            state.components.replaceAll(snapshot.components)
        }

        if (hasData) state.dataOrCreate().load(tag.getCompound(ENTITY_DATA_NBT))

        if (hasNodes) EntityNodeRuntime.load(entity, tag)
    }

    fun onSetLevel(entity: Entity, newLevel: Level) {
        relocateStateToLevel(entity, newLevel)
    }

    fun onRemove(entity: Entity) {
        val level = entity.level()
        val state = levelState(level).byUuid[entity.uuid] ?: return
        if (state.entity !== entity) return

        val transfer = sideState(level).transferringEntityUuids.remove(entity.uuid)
        if (transfer || entity is Player) cacheForTransfer(level, entity.uuid, state)

        levelState(level).byUuid.remove(entity.uuid)
        state.coroutineScope.cancel()
        EntityNodeRuntime.remove(entity)
        ComponentSync.forget(entity)

        NoAiRuntime.cleanup(entity)
        AIComponentSystems.cleanup(entity)
    }

    fun onSetId(entity: Entity, newId: Int, previousId: Int) {
        if (newId != previousId) markDirty(entity)
    }

    fun cloneOwnedState(old: Entity, new: Entity, dropLooseOnDeath: Boolean) {
        val source = stateOrNull(old.level(), old.uuid) ?: return
        val components = if (dropLooseOnDeath) source.components.withoutLooseOnDeath() else source.components.copyOf()
        val target = state(new)
        target.components.clear()
        target.components.putAll(components)
        // Data is deliberately kept across death: unlike loose components it is script-owned state.
        source.data?.takeUnless { it.isEmpty() }?.let { target.data = it }
    }

    fun entitySnapshot(level: Level, entityUuid: UUID): EntitySnapshot? {
        val state = stateOrNull(level, entityUuid) ?: return null
        val entity = state.entity
        return state.components.snapshot(entity)
    }

    fun entitySnapshots(level: Level): List<Pair<Entity, EntitySnapshot>> = synchronized(levelStates) {
        levelStates[level]?.byUuid?.values?.mapNotNull { state ->
                val entity = state.entity
                val snapshot = state.components.snapshot(entity) ?: return@mapNotNull null
                entity to snapshot
            }.orEmpty()
    }

    fun updateEntitySnapshot(entity: Entity, snapshot: EntitySnapshot) {
        state(entity).components.replaceAll(snapshot.components)
        markDirty(entity)
    }

    fun removeEntitySnapshot(level: Level, entityUuid: UUID): Entity? {
        val pending = sideState(level).pendingByEntityUuid
        pending[entityUuid]?.let { cached ->
            if (cached.data == null) pending.remove(entityUuid)
            else pending[entityUuid] = PendingTransfer(null, cached.data)
        }
        val state = stateOrNull(level, entityUuid) ?: return null
        state.components.clear()
        val entity = state.entity
        markDirty(entity)
        return entity
    }

    fun onDimensionChanged(old: Entity, new: Entity, from: Level, to: Level) {
        sideState(from).transferringEntityUuids += old.uuid
        stateOrNull(from, old.uuid)?.let { state ->
            cacheForTransfer(from, old.uuid, state)
            levelState(from).byUuid.remove(old.uuid)
            state.coroutineScope.cancel()
        }
        restoreFromTransfer(to, new.uuid, state(new))
    }

    fun onPlayerDimensionChanged(player: Player, from: Level, to: Level) {
        stateOrNull(from, player.uuid)?.let { cacheForTransfer(from, player.uuid, it) }
        restoreFromTransfer(to, player.uuid, state(player))
    }

    private fun cacheForTransfer(level: Level, uuid: UUID, state: EntityState) {
        val components = state.components.copyOf().takeUnless { it.isEmpty() }
        val data = state.data?.takeUnless { it.isEmpty() }
        if (components == null && data == null) sideState(level).pendingByEntityUuid.remove(uuid)
        else sideState(level).pendingByEntityUuid[uuid] = PendingTransfer(components, data)
    }

    private fun restoreFromTransfer(level: Level, uuid: UUID, target: EntityState) {
        sideState(level).pendingByEntityUuid.remove(uuid)?.let { cached ->
            cached.components?.let {
                target.components.clear()
                target.components.putAll(it)
            }
            cached.data?.let { target.data = it }
        }
        sideState(level).transferringEntityUuids.remove(uuid)
    }

    private fun relocateStateToLevel(entity: Entity, newLevel: Level) {
        val target = levelState(newLevel).byUuid
        synchronized(levelStates) {
            levelStates.entries.forEach { (level, state) ->
                if (level == newLevel) return@forEach
                val moved = state.byUuid.remove(entity.uuid) ?: return@forEach
                target[entity.uuid] = if (moved.entity !== entity) rebindStateEntity(moved, entity) else moved
            }
        }
    }

    private fun stateOrNull(level: Level, uuid: UUID): EntityState? =
        synchronized(levelStates) { levelStates[level]?.byUuid?.get(uuid) }

    private fun state(entity: MCEntity): EntityState {
        val map = levelState(entity.level()).byUuid
        val existing = map[entity.uuid]
        if (existing == null) {
            return createState(entity).also { map[entity.uuid] = it }
        }
        if (existing.entity === entity) return existing
        return rebindStateEntity(existing, entity).also { map[entity.uuid] = it }
    }

    private fun createState(entity: MCEntity): EntityState =
        EntityState(entity = entity, coroutineScope = EntityScope(entity)).also {
            restoreFromTransfer(entity.level(), entity.uuid, it)
        }

    private fun rebindStateEntity(source: EntityState, entity: MCEntity): EntityState {
        source.coroutineScope.cancel()
        return EntityState(
            entity = entity,
            data = source.data,
            runtimeId = source.runtimeId,
            coroutineScope = EntityScope(entity),
        ).also { it.components.putAll(source.components.copyOf()) }
    }

    private fun levelState(level: Level): LevelEntityState = synchronized(levelStates) {
        levelStates.computeIfAbsent(level) { LevelEntityState() }
    }

    private fun sideState(level: Level): SideTransferState = synchronized(sideTransferState) {
        sideTransferState.computeIfAbsent(level.isClientSide) { SideTransferState() }
    }
}

private fun EntityState.dataOrCreate(): NbtDataStore = data ?: NbtDataStore().also { data = it }

fun Level.findEntityByUuid(uuid: UUID): Entity? = when (this) {
    is ServerLevel -> getEntity(uuid)
    is ClientLevel -> {
        entitiesForRendering().forEach { entity ->
            if (entity.uuid == uuid) return entity
        }
        null
    }

    else -> null
}
