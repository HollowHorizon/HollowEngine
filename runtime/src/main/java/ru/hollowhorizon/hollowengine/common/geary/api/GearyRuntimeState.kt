package ru.hollowhorizon.hollowengine.common.geary.api

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlinx.coroutines.cancel
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.OwnerScopeRestoredEvent
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineScope
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.geary.binding.*
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.NoAi
import ru.hollowhorizon.hollowengine.common.geary.components.NoAiRuntime
import ru.hollowhorizon.hollowengine.common.geary.components.ai.AIComponentSystems
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import java.util.*
import java.util.concurrent.atomic.AtomicLong

private data class EntityState(
    var entity: MCEntity,
    var runtimeId: Long = UNINITIALIZED_ENTITY_ID,
    var dirty: Boolean = false,
    val componentsById: MutableMap<ResourceLocation, Any> = Object2ObjectOpenHashMap(),
    val nodeSnapshotsByStableKey: MutableMap<UUID, EntitySnapshot> = Object2ObjectOpenHashMap(),
    val coroutineScope: SerializableCoroutineScope,
)

private data class LevelEntityState(
    val byUuid: MutableMap<UUID, EntityState> = Object2ObjectOpenHashMap(),
)

private data class SideTransferState(
    val pendingEntityComponentsByUuid: MutableMap<UUID, MutableMap<ResourceLocation, Any>> = Object2ObjectOpenHashMap(),
    val pendingNodeSnapshotsByHostUuid: MutableMap<UUID, MutableMap<UUID, EntitySnapshot>> = Object2ObjectOpenHashMap(),
    val transferringEntityUuids: MutableSet<UUID> = linkedSetOf(),
)

object GearyRuntimeState {
    private const val NODE_STATE_NBT = "NodeState"
    private const val NODE_STATE_PRIMARY_NBT = "primary"
    private const val NODE_STATE_CHILDREN_NBT = "children"

    private val levelStates = Collections.synchronizedMap(WeakHashMap<Level, LevelEntityState>())
    private val sideTransferState = Collections.synchronizedMap(Object2ObjectOpenHashMap<Boolean, SideTransferState>())
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
        val runtime = NodeRuntimeState.service(level)

        states.forEach { state ->
            val entity = state.entity
            if (entity.level() != level || entity.uuid !in levelState(level).byUuid) return@forEach

            if (state.runtimeId == UNINITIALIZED_ENTITY_ID) {
                state.runtimeId = ids.getAndIncrement()
            }

            runtime.ensurePrimaryEntity(entity)

            if (state.dirty) {
                runtime.updatePrimaryComponents(
                    hostEntity = entity,
                    components = state.componentsById.values,
                    syncToClients = level is ServerLevel,
                )
                state.dirty = false
            }

            if (level !is ServerLevel) {
                val primary = runtime.snapshot(entity.uuid)
                if (primary?.isPrimaryEntityOwner() == true) {
                    val desired = primary.componentById()
                    if (desired != state.componentsById) {
                        state.componentsById.clear()
                        desired.forEach { (id, component) -> state.componentsById[id] = component }
                    }
                }
            }

            val hasNoAi = state.componentsById.containsKey(noAiId)
            NoAiRuntime.apply(entity, hasNoAi)
            AIComponentSystems.tickEntity(entity, state.componentsById)
        }

        runtime.tick()
    }

    fun close(level: Level) {
        NodeRuntimeState.close(level)
        synchronized(levelStates) {
            levelStates.remove(level)?.byUuid?.values?.forEach { state ->
                state.coroutineScope.cancel()
            }
        }
    }

    fun initEntity(entity: MCEntity) {
        state(entity)
    }

    fun componentsById(entity: MCEntity): MutableMap<ResourceLocation, Any> =
        state(entity).componentsById

    fun markDirty(entity: MCEntity) {
        state(entity).dirty = true
    }

    fun ensureEntity(level: Level, entity: MCEntity): Long {
        val state = state(entity)
        if (state.runtimeId == UNINITIALIZED_ENTITY_ID) {
            state.runtimeId = ids.getAndIncrement()
            NodeRuntimeState.service(level).ensurePrimaryEntity(entity)
        }
        return state.runtimeId
    }

    fun bind(level: Level, entity: MCEntity, entityId: Int, previousEntityId: Int): Long {
        val runtimeId = ensureEntity(level, entity)
        if (entityId != previousEntityId) markDirty(entity)
        return runtimeId
    }

    fun bindIfInitialized(level: Level, entity: MCEntity): Long? {
        val state = stateOrNull(level, entity.uuid) ?: return null
        if (state.runtimeId == UNINITIALIZED_ENTITY_ID) return null
        if (state.entity !== entity) {
            val rebound = rebindStateEntity(state, entity)
            levelState(level).byUuid[entity.uuid] = rebound
        }
        NodeRuntimeState.service(level).ensurePrimaryEntity(entity)
        return levelState(level).byUuid[entity.uuid]?.runtimeId
    }

    fun move(old: Level, new: Level, mcEntity: MCEntity): Long {
        val oldMap = levelState(old).byUuid
        val current = oldMap.remove(mcEntity.uuid) ?: state(mcEntity)
        val rebound = if (current.entity !== mcEntity) rebindStateEntity(current, mcEntity) else current
        rebound.runtimeId = ids.getAndIncrement()
        levelState(new).byUuid[mcEntity.uuid] = rebound
        NodeRuntimeState.service(old).moveEntityNodes(mcEntity.uuid, new)
        NodeRuntimeState.service(new).ensurePrimaryEntity(mcEntity)
        return rebound.runtimeId
    }

    fun coroutineScope(entity: Entity): SerializableCoroutineScope = state(entity).coroutineScope

    fun saveEntity(entity: Entity, tag: CompoundTag) {
        try {
            val state = state(entity)

            val nodeState = CompoundTag()
            if (state.componentsById.isNotEmpty()) {
                val primarySnapshot = EntitySnapshot(
                    stableKey = entity.uuid,
                    hostUuid = entity.uuid,
                    primary = true,
                    components = state.componentsById.values.toList(),
                )
                nodeState.put(NODE_STATE_PRIMARY_NBT, EntitySerialization.serializeToNbt(primarySnapshot))
            }

            if (state.nodeSnapshotsByStableKey.isNotEmpty()) {
                val list = net.minecraft.nbt.ListTag()
                state.nodeSnapshotsByStableKey.values.forEach { snapshot ->
                    list.add(EntitySerialization.serializeToNbt(snapshot))
                }
                nodeState.put(NODE_STATE_CHILDREN_NBT, list)
            }

            if (!nodeState.isEmpty) tag.put(NODE_STATE_NBT, nodeState)
            else tag.remove(NODE_STATE_NBT)

            val scopeTag = CompoundTag()
            state.coroutineScope.serialize(scopeTag)
            tag.put("EntityScope", scopeTag)
            state.dirty = false
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Failed to save entity {} ({})", entity.id, entity.uuid, e)
        }
    }

    fun loadEntity(entity: Entity, tag: CompoundTag) {
        val state = state(entity)
        state.componentsById.clear()
        state.nodeSnapshotsByStableKey.clear()

        if (tag.contains(NODE_STATE_NBT, Tag.TAG_COMPOUND.toInt())) {
            val nodeState = tag.getCompound(NODE_STATE_NBT)
            if (nodeState.contains(NODE_STATE_PRIMARY_NBT, Tag.TAG_COMPOUND.toInt())) {
                val primary = EntitySerialization.tryDeserializeFromNbt(
                    nodeState.getCompound(NODE_STATE_PRIMARY_NBT),
                    "entity ${entity.id} node primary snapshot",
                )
                if (primary != null) {
                    primary.componentById().forEach { (id, component) -> state.componentsById[id] = component }
                    state.dirty = true
                } else {
                    HollowEngine.LOGGER.error("Failed to deserialize NodeState primary snapshot for entity {} ({})", entity.id, entity.uuid)
                }
            }

            val list = nodeState.getList(NODE_STATE_CHILDREN_NBT, Tag.TAG_COMPOUND.toInt())
            for (index in 0 until list.size) {
                val snapshot = EntitySerialization.deserializeFromNbt(list.getCompound(index))
                val normalized = if (snapshot.hostUuid != entity.uuid || !snapshot.isEntityBound()) {
                    HollowEngine.LOGGER.warn(
                        "Normalizing loaded child node snapshot {} for entity {} ({}): host was {} entityBound={}",
                        snapshot.requireStableKey(),
                        entity.id,
                        entity.uuid,
                        snapshot.hostUuid,
                        snapshot.isEntityBound(),
                    )
                    snapshot.withEntityBinding(entity.uuid, primary = false)
                } else snapshot
                state.nodeSnapshotsByStableKey[normalized.requireStableKey()] = normalized
            }
        }

        state.coroutineScope.deserialize(tag.getCompound("EntityScope"))
        EventBus.post(OwnerScopeRestoredEvent(entity))
    }

    fun onSetLevel(entity: Entity, newLevel: Level) {
        relocateStateToLevel(entity, newLevel)
        val state = stateOrNull(newLevel, entity.uuid) ?: return
        if (state.runtimeId == UNINITIALIZED_ENTITY_ID) return
        NodeRuntimeState.service(newLevel).ensurePrimaryEntity(entity)
    }

    fun onRemove(entity: Entity) {
        val level = entity.level()
        val map = levelState(level).byUuid
        val state = map[entity.uuid] ?: return

        if (state.entity !== entity) {
            HollowEngine.LOGGER.warn(
                "Ignoring stale entity remove for uuid {} in level {}: tracked={}, removed={}",
                entity.uuid,
                level.dimension().location(),
                state.entity,
                entity,
            )
            return
        }

        val transfer = sideState(level).transferringEntityUuids.remove(entity.uuid)
        if (transfer || entity is Player) {
            cacheForTransfer(level, entity.uuid, state)
        }

        map.remove(entity.uuid)
        state.coroutineScope.cancel()

        if (entity !is Player && !transfer) {
            val runtime = NodeRuntimeState.service(level)
            runtime.onHostRemoved(entity.uuid)
            runtime.detachPrimaryEntity(entity.uuid)
        }

        NoAiRuntime.cleanup(entity)
        AIComponentSystems.cleanup(entity)
    }

    fun onSetId(entity: Entity, newId: Int, previousId: Int) {
        if (newId != previousId) markDirty(entity)
    }

    fun snapshotForTransfer(entity: MCEntity): EntitySnapshot {
        val level = entity.level()
        val components = stateOrNull(level, entity.uuid)?.componentsById
            ?: sideState(level).pendingEntityComponentsByUuid[entity.uuid]
            ?: emptyMap()
        return EntitySnapshot(components = components.values.toList())
    }

    fun upsertNodeSnapshot(level: Level, hostUuid: UUID, snapshot: EntitySnapshot) {
        val stableKey = snapshot.requireStableKey()
        val normalized = if (snapshot.hostUuid != hostUuid || !snapshot.isEntityBound()) {
            HollowEngine.LOGGER.warn(
                "Normalizing node snapshot {} binding from host={} (entityBound={}) to host={}",
                stableKey,
                snapshot.hostUuid,
                snapshot.isEntityBound(),
                hostUuid,
            )
            snapshot.withEntityBinding(hostUuid, primary = false)
        } else snapshot

        val holder = stateOrNull(level, hostUuid)
            ?: level.findEntityByUuid(hostUuid)?.let { state(it) }

        if (holder != null) {
            holder.nodeSnapshotsByStableKey[stableKey] = normalized
        } else {
            HollowEngine.LOGGER.warn(
                "Host entity {} is not initialized in level {} while storing node snapshot {}. Keeping transfer cache.",
                hostUuid,
                level.dimension().location(),
                stableKey,
            )
            sideState(level)
                .pendingNodeSnapshotsByHostUuid
                .computeIfAbsent(hostUuid) { Object2ObjectOpenHashMap() }[stableKey] = normalized
        }
    }

    fun removeNodeSnapshot(level: Level, stableKey: UUID): Boolean {
        var removed = false
        levelState(level).byUuid.values.forEach { state ->
            if (state.nodeSnapshotsByStableKey.remove(stableKey) != null) removed = true
        }
        sideState(level).pendingNodeSnapshotsByHostUuid.values.forEach { pending ->
            if (pending.remove(stableKey) != null) removed = true
        }
        return removed
    }

    fun nodeSnapshot(level: Level, stableKey: UUID): EntitySnapshot? {
        levelState(level).byUuid.values.forEach { state ->
            state.nodeSnapshotsByStableKey[stableKey]?.let { return it }
        }
        sideState(level).pendingNodeSnapshotsByHostUuid.values.forEach { pending ->
            pending[stableKey]?.let { return it }
        }
        return null
    }

    fun clearNodeSnapshots(level: Level, hostUuid: UUID) {
        stateOrNull(level, hostUuid)?.nodeSnapshotsByStableKey?.clear()
        sideState(level).pendingNodeSnapshotsByHostUuid.remove(hostUuid)
    }

    fun nodeSnapshots(level: Level, hostUuid: UUID): List<EntitySnapshot> {
        val byStableKey = linkedMapOf<UUID, EntitySnapshot>()
        stateOrNull(level, hostUuid)?.nodeSnapshotsByStableKey?.values?.forEach { byStableKey[it.requireStableKey()] = it }
        sideState(level).pendingNodeSnapshotsByHostUuid[hostUuid]?.values?.forEach { byStableKey[it.requireStableKey()] = it }
        return byStableKey.values.toList()
    }

    fun nodeSnapshots(level: Level): List<EntitySnapshot> {
        val byStableKey = linkedMapOf<UUID, EntitySnapshot>()
        levelState(level).byUuid.values.forEach { state ->
            state.nodeSnapshotsByStableKey.values.forEach { snapshot ->
                byStableKey[snapshot.requireStableKey()] = snapshot
            }
        }
        return byStableKey.values.toList()
    }

    fun onDimensionChanged(old: Entity, new: Entity, from: Level, to: Level) {
        val transfer = sideState(from)
        transfer.transferringEntityUuids += old.uuid

        stateOrNull(from, old.uuid)?.let { oldState ->
            cacheForTransfer(from, old.uuid, oldState)
            levelState(from).byUuid.remove(old.uuid)
            oldState.coroutineScope.cancel()
        }

        val newState = state(new)
        restoreFromTransfer(to, new.uuid, newState)
        newState.dirty = true
        NodeRuntimeState.service(to).ensurePrimaryEntity(new)
    }

    fun onPlayerDimensionChanged(player: Player, from: Level, to: Level) {
        stateOrNull(from, player.uuid)?.let { state ->
            cacheForTransfer(from, player.uuid, state)
        }
        val playerState = state(player)
        restoreFromTransfer(to, player.uuid, playerState)
        if (playerState.runtimeId == UNINITIALIZED_ENTITY_ID) {
            playerState.runtimeId = ids.getAndIncrement()
        }
        playerState.dirty = true
        NodeRuntimeState.service(to).ensurePrimaryEntity(player)
    }

    private fun cacheForTransfer(level: Level, uuid: UUID, state: EntityState) {
        val transfer = sideState(level)
        if (state.componentsById.isNotEmpty()) {
            val copied = Object2ObjectOpenHashMap<ResourceLocation, Any>()
            copied.putAll(state.componentsById)
            transfer.pendingEntityComponentsByUuid[uuid] = copied
        } else {
            transfer.pendingEntityComponentsByUuid.remove(uuid)
        }

        if (state.nodeSnapshotsByStableKey.isNotEmpty()) {
            val copied = Object2ObjectOpenHashMap<UUID, EntitySnapshot>()
            copied.putAll(state.nodeSnapshotsByStableKey)
            transfer.pendingNodeSnapshotsByHostUuid[uuid] = copied
        } else {
            transfer.pendingNodeSnapshotsByHostUuid.remove(uuid)
        }
    }

    private fun restoreFromTransfer(level: Level, uuid: UUID, target: EntityState) {
        val transfer = sideState(level)
        transfer.pendingEntityComponentsByUuid.remove(uuid)?.let { restored ->
            target.componentsById.clear()
            target.componentsById.putAll(restored)
        }
        transfer.pendingNodeSnapshotsByHostUuid.remove(uuid)?.let { restored ->
            target.nodeSnapshotsByStableKey.clear()
            target.nodeSnapshotsByStableKey.putAll(restored)
        }
        transfer.transferringEntityUuids.remove(uuid)
    }

    private fun relocateStateToLevel(entity: Entity, newLevel: Level) {
        val uuid = entity.uuid
        val target = levelState(newLevel).byUuid

        synchronized(levelStates) {
            levelStates.entries.forEach { (level, state) ->
                if (level == newLevel) return@forEach
                val moved = state.byUuid.remove(uuid) ?: return@forEach
                val rebound = if (moved.entity !== entity) rebindStateEntity(moved, entity) else moved
                target[uuid] = rebound
            }
        }
    }

    private fun stateOrNull(level: Level, uuid: UUID): EntityState? =
        synchronized(levelStates) { levelStates[level]?.byUuid?.get(uuid) }

    private fun state(entity: MCEntity): EntityState {
        val level = entity.level()
        val map = levelState(level).byUuid
        val existing = map[entity.uuid]

        if (existing == null) {
            val created = createState(entity)
            map[entity.uuid] = created
            return created
        }

        if (existing.entity !== entity) {
            HollowEngine.LOGGER.warn(
                "Replacing tracked entity reference for uuid {} in level {}. old={}, new={}",
                entity.uuid,
                level.dimension().location(),
                existing.entity,
                entity,
            )
            val rebound = rebindStateEntity(existing, entity)
            map[entity.uuid] = rebound
            return rebound
        }

        return existing
    }

    private fun createState(entity: MCEntity): EntityState {
        val state = EntityState(entity = entity, coroutineScope = EntityScope(entity))
        restoreFromTransfer(entity.level(), entity.uuid, state)
        return state
    }

    private fun rebindStateEntity(source: EntityState, entity: MCEntity): EntityState {
        source.coroutineScope.cancel()
        val rebound = EntityState(
            entity = entity,
            runtimeId = source.runtimeId,
            dirty = source.dirty,
            coroutineScope = EntityScope(entity),
        )
        rebound.componentsById.putAll(source.componentsById)
        rebound.nodeSnapshotsByStableKey.putAll(source.nodeSnapshotsByStableKey)
        return rebound
    }

    private fun levelState(level: Level): LevelEntityState = synchronized(levelStates) {
        levelStates.computeIfAbsent(level) { LevelEntityState() }
    }

    private fun sideState(level: Level): SideTransferState = synchronized(sideTransferState) {
        sideTransferState.computeIfAbsent(level.isClientSide) { SideTransferState() }
    }
}

private fun Level.findEntityByUuid(uuid: UUID): Entity? =
    (this as? ServerLevel)?.getEntity(uuid)