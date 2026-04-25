package ru.hollowhorizon.hollowengine.common.geary.api

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
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
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.OwnerScopeRestoredEvent
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineScope
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.geary.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.binding.isEntityBound
import ru.hollowhorizon.hollowengine.common.geary.binding.requireStableKey
import ru.hollowhorizon.hollowengine.common.geary.binding.withEntityBinding
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.NoAi
import ru.hollowhorizon.hollowengine.common.geary.components.NoAiRuntime
import ru.hollowhorizon.hollowengine.common.geary.components.ai.AIComponentSystems
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntityNodeSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import java.util.AbstractMap
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

private data class EntityState(
    var entity: MCEntity,
    var runtimeId: Long = UNINITIALIZED_ENTITY_ID,
    val snapshotsByStableKey: MutableMap<UUID, EntitySnapshot> = Object2ObjectOpenHashMap(),
    val coroutineScope: SerializableCoroutineScope,
)

private data class LevelEntityState(
    val byUuid: MutableMap<UUID, EntityState> = Object2ObjectOpenHashMap(),
)

private data class SideTransferState(
    val pendingSnapshotsByHostUuid: MutableMap<UUID, MutableMap<UUID, EntitySnapshot>> = Object2ObjectOpenHashMap(),
    val transferringEntityUuids: MutableSet<UUID> = linkedSetOf(),
)

object GearyRuntimeState {
    private const val NODE_STATE_NBT = "NodeState"
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
        states.forEach { state ->
            val entity = state.entity
            if (entity.level() != level || state.snapshotCount == 0) return@forEach
            if (state.runtimeId == UNINITIALIZED_ENTITY_ID) state.runtimeId = ids.getAndIncrement()

            val components = EntityComponentMap(state)
            NoAiRuntime.apply(entity, components.containsKey(noAiId))
            AIComponentSystems.tickEntity(entity, components)
        }
        NodeRuntimeState.service(level).tick()
    }

    fun close(level: Level) {
        NodeRuntimeState.close(level)
        synchronized(levelStates) {
            levelStates.remove(level)?.byUuid?.values?.forEach { it.coroutineScope.cancel() }
        }
    }

    fun initEntity(entity: MCEntity) {
        // Entity state is intentionally lazy. Empty vanilla entities should not allocate runtime state.
    }

    fun componentsById(entity: MCEntity): MutableMap<ResourceLocation, Any> =
        EntityComponentMap(state(entity))

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
        NodeRuntimeState.service(old).moveEntityNodes(mcEntity.uuid, new)
        return rebound.runtimeId
    }

    fun coroutineScope(entity: Entity): SerializableCoroutineScope = state(entity).coroutineScope

    fun saveEntity(entity: Entity, tag: CompoundTag) {
        val state = stateOrNull(entity.level(), entity.uuid)
        if (state == null || state.snapshotCount == 0) {
            tag.remove(NODE_STATE_NBT)
            return
        }

        try {
            val list = net.minecraft.nbt.ListTag()
            state.snapshotsByStableKey.values.forEach { snapshot ->
                list.add(EntitySerialization.serializeToNbt(snapshot))
            }
            tag.put(NODE_STATE_NBT, CompoundTag().apply { put(NODE_STATE_CHILDREN_NBT, list) })

            val scopeTag = CompoundTag()
            state.coroutineScope.serialize(scopeTag)
            tag.put("EntityScope", scopeTag)
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Failed to save entity {} ({})", entity.id, entity.uuid, e)
        }
    }

    fun loadEntity(entity: Entity, tag: CompoundTag) {
        if (!tag.contains(NODE_STATE_NBT, Tag.TAG_COMPOUND.toInt()) && !tag.contains("EntityScope", Tag.TAG_COMPOUND.toInt())) {
            return
        }

        val state = state(entity)
        state.snapshotsByStableKey.clear()

        if (tag.contains(NODE_STATE_NBT, Tag.TAG_COMPOUND.toInt())) {
            val nodeState = tag.getCompound(NODE_STATE_NBT)
            val list = nodeState.getList(NODE_STATE_CHILDREN_NBT, Tag.TAG_COMPOUND.toInt())
            for (index in 0 until list.size) {
                val snapshot = EntitySerialization.deserializeFromNbt(list.getCompound(index))
                val normalized = if (snapshot.hostUuid == entity.uuid && snapshot.isEntityBound()) snapshot
                else snapshot.withEntityBinding(entity.uuid)
                state.snapshotsByStableKey[normalized.requireStableKey()] = normalized
            }
        }

        state.coroutineScope.deserialize(tag.getCompound("EntityScope"))
        EventBus.post(OwnerScopeRestoredEvent(entity))
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

        if (entity !is Player && !transfer) {
            NodeRuntimeState.service(level).onHostRemoved(entity.uuid)
        }

        NoAiRuntime.cleanup(entity)
        AIComponentSystems.cleanup(entity)
    }

    fun onSetId(entity: Entity, newId: Int, previousId: Int) {
        if (newId != previousId) markDirty(entity)
    }

    fun snapshotForTransfer(entity: MCEntity): EntitySnapshot =
        stateOrNull(entity.level(), entity.uuid)?.entitySnapshot()
            ?: EntitySnapshot(stableKey = entity.uuid, hostUuid = entity.uuid)

    fun cloneOwnedState(old: Entity, new: Entity, dropLooseOnDeath: Boolean) {
        val source = stateOrNull(old.level(), old.uuid) ?: return
        val target = state(new)
        target.snapshotsByStableKey.clear()
        source.snapshotsByStableKey.values.forEach { snapshot ->
            val normalized = snapshot
                .withEntityBinding(new.uuid)
                .let { if (dropLooseOnDeath) it.dropLooseOnDeathComponents() else it }
            target.snapshotsByStableKey[normalized.requireStableKey()] = normalized
        }
    }

    fun upsertNodeSnapshot(level: Level, hostUuid: UUID, snapshot: EntitySnapshot) {
        val stableKey = snapshot.requireStableKey()
        val normalized = if (snapshot.hostUuid == hostUuid && snapshot.isEntityBound()) snapshot
        else snapshot.withEntityBinding(hostUuid)

        val holder = stateOrNull(level, hostUuid) ?: level.findEntityByUuid(hostUuid)?.let(::state)
        if (holder != null) {
            holder.snapshotsByStableKey[stableKey] = normalized
        } else {
            sideState(level)
                .pendingSnapshotsByHostUuid
                .computeIfAbsent(hostUuid) { Object2ObjectOpenHashMap() }[stableKey] = normalized
        }
    }

    fun removeNodeSnapshot(level: Level, stableKey: UUID): Boolean {
        var removed = false
        levelState(level).byUuid.values.forEach { state ->
            if (state.snapshotsByStableKey.remove(stableKey) != null) removed = true
        }
        sideState(level).pendingSnapshotsByHostUuid.values.forEach { pending ->
            if (pending.remove(stableKey) != null) removed = true
        }
        return removed
    }

    fun nodeSnapshot(level: Level, stableKey: UUID): EntitySnapshot? {
        levelState(level).byUuid.values.forEach { state ->
            state.snapshotsByStableKey[stableKey]?.let { return it }
        }
        sideState(level).pendingSnapshotsByHostUuid.values.forEach { pending ->
            pending[stableKey]?.let { return it }
        }
        return null
    }

    fun clearNodeSnapshots(level: Level, hostUuid: UUID) {
        stateOrNull(level, hostUuid)?.snapshotsByStableKey?.clear()
        sideState(level).pendingSnapshotsByHostUuid.remove(hostUuid)
    }

    fun nodeSnapshots(level: Level, hostUuid: UUID): List<EntitySnapshot> {
        val byStableKey = linkedMapOf<UUID, EntitySnapshot>()
        stateOrNull(level, hostUuid)?.snapshotsByStableKey?.values?.forEach { byStableKey[it.requireStableKey()] = it }
        sideState(level).pendingSnapshotsByHostUuid[hostUuid]?.values?.forEach { byStableKey[it.requireStableKey()] = it }
        return byStableKey.values.toList()
    }

    fun nodeSnapshots(level: Level): List<EntitySnapshot> {
        val byStableKey = linkedMapOf<UUID, EntitySnapshot>()
        levelState(level).byUuid.values.forEach { state ->
            state.snapshotsByStableKey.values.forEach { snapshot -> byStableKey[snapshot.requireStableKey()] = snapshot }
        }
        return byStableKey.values.toList()
    }

    fun onDimensionChanged(old: Entity, new: Entity, from: Level, to: Level) {
        sideState(from).transferringEntityUuids += old.uuid
        stateOrNull(from, old.uuid)?.let { state ->
            cacheForTransfer(from, old.uuid, state)
            levelState(from).byUuid.remove(old.uuid)
            state.coroutineScope.cancel()
        }
        val newState = state(new)
        restoreFromTransfer(to, new.uuid, newState)
    }

    fun onPlayerDimensionChanged(player: Player, from: Level, to: Level) {
        stateOrNull(from, player.uuid)?.let { cacheForTransfer(from, player.uuid, it) }
        restoreFromTransfer(to, player.uuid, state(player))
    }

    private fun cacheForTransfer(level: Level, uuid: UUID, state: EntityState) {
        val transfer = sideState(level)
        if (state.snapshotsByStableKey.isNotEmpty()) {
            transfer.pendingSnapshotsByHostUuid[uuid] = Object2ObjectOpenHashMap<UUID, EntitySnapshot>().apply {
                putAll(state.snapshotsByStableKey)
            }
        } else {
            transfer.pendingSnapshotsByHostUuid.remove(uuid)
        }
    }

    private fun restoreFromTransfer(level: Level, uuid: UUID, target: EntityState) {
        sideState(level).pendingSnapshotsByHostUuid.remove(uuid)?.let { restored ->
            target.snapshotsByStableKey.clear()
            target.snapshotsByStableKey.putAll(restored)
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
            runtimeId = source.runtimeId,
            coroutineScope = EntityScope(entity),
        ).also { rebound -> rebound.snapshotsByStableKey.putAll(source.snapshotsByStableKey) }
    }

    private fun levelState(level: Level): LevelEntityState = synchronized(levelStates) {
        levelStates.computeIfAbsent(level) { LevelEntityState() }
    }

    private fun sideState(level: Level): SideTransferState = synchronized(sideTransferState) {
        sideTransferState.computeIfAbsent(level.isClientSide) { SideTransferState() }
    }
}

private val EntityState.snapshotCount: Int
    get() = snapshotsByStableKey.size

private fun EntityState.entitySnapshot(): EntitySnapshot? =
    snapshotsByStableKey[entity.uuid]

private fun EntityState.entitySnapshotOrEmpty(): EntitySnapshot =
    entitySnapshot() ?: EntitySnapshot(stableKey = entity.uuid, hostUuid = entity.uuid)

private fun EntityState.writeEntityComponents(components: Collection<Any>) {
    val root = entitySnapshotOrEmpty().rootNode()
    val updatedRoot = root.copy(components = components.toList())
    val updated = entitySnapshotOrEmpty()
        .withNodes(
            entitySnapshotOrEmpty().nodeList().map { node -> if (node.id == root.id) updatedRoot else node },
            explicitRootNodeId = root.id,
        )
        .withEntityBinding(entity.uuid)
    snapshotsByStableKey[entity.uuid] = updated
}

private class EntityComponentMap(private val state: EntityState) : AbstractMutableMap<ResourceLocation, Any>() {
    override val entries: MutableSet<MutableMap.MutableEntry<ResourceLocation, Any>>
        get() = snapshotMap().map { (id, component) ->
            object : MutableMap.MutableEntry<ResourceLocation, Any> {
                override val key: ResourceLocation = id
                override val value: Any get() = snapshotMap()[id] ?: component
                override fun setValue(newValue: Any): Any = put(id, newValue) ?: component
            }
        }.toMutableSet()

    override fun put(key: ResourceLocation, value: Any): Any? {
        val previous = snapshotMap()[key]
        val merged = LinkedHashMap(snapshotMap())
        merged[key] = value
        state.writeEntityComponents(merged.values)
        return previous
    }

    override fun remove(key: ResourceLocation): Any? {
        val merged = LinkedHashMap(snapshotMap())
        val previous = merged.remove(key) ?: return null
        state.writeEntityComponents(merged.values)
        return previous
    }

    override fun clear() {
        state.snapshotsByStableKey.remove(state.entity.uuid)
    }

    private fun snapshotMap(): LinkedHashMap<ResourceLocation, Any> =
        LinkedHashMap<ResourceLocation, Any>().apply {
            state.entitySnapshotOrEmpty().rootNode().components.forEach { component ->
                val id = ComponentDescriptorRegistry.idFor(component::class)
                    ?: error("Component descriptor not found for ${component::class.qualifiedName}")
                put(id, component)
            }
        }
}

private fun Level.findEntityByUuid(uuid: UUID): Entity? =
    when (this) {
        is ServerLevel -> getEntity(uuid)
        is ClientLevel -> {
            entitiesForRendering().forEach { entity ->
                if (entity.uuid == uuid) return entity
            }
            null
        }
        else -> null
    }
