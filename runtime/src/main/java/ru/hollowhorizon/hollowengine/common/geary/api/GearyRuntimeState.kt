package ru.hollowhorizon.hollowengine.common.geary.api

import com.google.common.eventbus.EventBus
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
import ru.hollowhorizon.hollowengine.common.geary.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.NoAi
import ru.hollowhorizon.hollowengine.common.geary.components.NoAiRuntime
import ru.hollowhorizon.hollowengine.common.geary.components.ai.AIComponentSystems
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
    var snapshot: EntitySnapshot? = null,
    var runtimeId: Long = UNINITIALIZED_ENTITY_ID,
    val coroutineScope: SerializableCoroutineScope,
)

private data class LevelEntityState(
    val byUuid: MutableMap<UUID, EntityState> = linkedMapOf(),
)

private data class SideTransferState(
    val pendingSnapshotsByEntityUuid: MutableMap<UUID, EntitySnapshot> = linkedMapOf(),
    val transferringEntityUuids: MutableSet<UUID> = linkedSetOf(),
)

object GearyRuntimeState {
    private const val ENTITY_SNAPSHOT_NBT = "EntitySnapshot"

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
            if (entity.level() != level || state.snapshot == null) return@forEach
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
        return rebound.runtimeId
    }

    fun coroutineScope(entity: Entity): SerializableCoroutineScope = state(entity).coroutineScope

    fun saveEntity(entity: Entity, tag: CompoundTag) {
        val state = stateOrNull(entity.level(), entity.uuid)
        if (state == null) {
            tag.remove(ENTITY_SNAPSHOT_NBT)
            tag.remove("EntityScope")
            return
        }

        try {
            val snapshot = state.snapshot
            if (snapshot == null) {
                tag.remove(ENTITY_SNAPSHOT_NBT)
            } else {
                tag.put(ENTITY_SNAPSHOT_NBT, EntitySerialization.serializeToNbt(snapshot))
            }

            val scopeTag = CompoundTag()
            state.coroutineScope.serialize(scopeTag)
            tag.put("EntityScope", scopeTag)
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Failed to save entity {} ({})", entity.id, entity.uuid, e)
        }
    }

    fun loadEntity(entity: Entity, tag: CompoundTag) {
        if (!tag.contains(ENTITY_SNAPSHOT_NBT, Tag.TAG_COMPOUND.toInt()) && !tag.contains("EntityScope", Tag.TAG_COMPOUND.toInt())) {
            return
        }

        val state = state(entity)
        if (tag.contains(ENTITY_SNAPSHOT_NBT, Tag.TAG_COMPOUND.toInt())) {
            state.snapshot = EntitySerialization.deserializeFromNbt(tag.getCompound(ENTITY_SNAPSHOT_NBT)).withEntity(entity)
        }

        state.coroutineScope.deserialize(tag.getCompound("EntityScope"))
        OwnerScopeRestoredEvent.post(OwnerScopeRestoredEvent(entity))
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

        NoAiRuntime.cleanup(entity)
        AIComponentSystems.cleanup(entity)
    }

    fun onSetId(entity: Entity, newId: Int, previousId: Int) {
        if (newId != previousId) markDirty(entity)
    }

    fun cloneOwnedState(old: Entity, new: Entity, dropLooseOnDeath: Boolean) {
        val source = stateOrNull(old.level(), old.uuid) ?: return
        val snapshot = source.snapshot
            ?.let { if (dropLooseOnDeath) it.dropLooseOnDeathComponents() else it }
            ?.withEntity(new)
        state(new).snapshot = snapshot
    }

    fun entitySnapshot(level: Level, entityUuid: UUID): EntitySnapshot? =
        stateOrNull(level, entityUuid)?.snapshot

    fun entitySnapshots(level: Level): List<Pair<Entity, EntitySnapshot>> =
        synchronized(levelStates) {
            levelStates[level]?.byUuid?.values
                ?.mapNotNull { state ->
                    val entity = state.entity as? Entity ?: return@mapNotNull null
                    val snapshot = state.snapshot ?: return@mapNotNull null
                    entity to snapshot.withEntity(entity)
                }
                .orEmpty()
        }

    fun updateEntitySnapshot(entity: Entity, snapshot: EntitySnapshot) {
        state(entity).snapshot = snapshot.withEntity(entity)
        markDirty(entity)
    }

    fun removeEntitySnapshot(level: Level, entityUuid: UUID): Entity? {
        sideState(level).pendingSnapshotsByEntityUuid.remove(entityUuid)
        val state = stateOrNull(level, entityUuid) ?: return null
        state.snapshot = null
        val entity = state.entity as? Entity ?: return null
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
        val snapshot = state.snapshot
        if (snapshot == null) sideState(level).pendingSnapshotsByEntityUuid.remove(uuid)
        else sideState(level).pendingSnapshotsByEntityUuid[uuid] = snapshot
    }

    private fun restoreFromTransfer(level: Level, uuid: UUID, target: EntityState) {
        sideState(level).pendingSnapshotsByEntityUuid.remove(uuid)?.let { restored ->
            target.snapshot = restored.withEntity(target.entity as Entity)
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
            snapshot = source.snapshot?.withEntity(entity as Entity),
            runtimeId = source.runtimeId,
            coroutineScope = EntityScope(entity),
        )
    }

    private fun levelState(level: Level): LevelEntityState = synchronized(levelStates) {
        levelStates.computeIfAbsent(level) { LevelEntityState() }
    }

    private fun sideState(level: Level): SideTransferState = synchronized(sideTransferState) {
        sideTransferState.computeIfAbsent(level.isClientSide) { SideTransferState() }
    }
}

private fun EntityState.entitySnapshotOrEmpty(): EntitySnapshot =
    snapshot ?: EntitySnapshot().withEntity(entity as Entity)

private fun EntityState.writeEntityComponents(components: Collection<Any>) {
    snapshot = EntitySnapshot(components = components.filterIsInstance<Component>()).withEntity(entity as Entity)
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
        state.snapshot = null
    }

    private fun snapshotMap(): LinkedHashMap<ResourceLocation, Any> =
        LinkedHashMap<ResourceLocation, Any>().apply {
            state.entitySnapshotOrEmpty().components.forEach { component ->
                val id = ComponentDescriptorRegistry.idFor(component::class)
                    ?: error("Component descriptor not found for ${component::class.qualifiedName}")
                put(id, component)
            }
        }
}

fun Level.findEntityByUuid(uuid: UUID): Entity? =
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
