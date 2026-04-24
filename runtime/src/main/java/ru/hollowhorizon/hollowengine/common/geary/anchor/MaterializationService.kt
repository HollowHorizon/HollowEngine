package ru.hollowhorizon.hollowengine.common.geary.anchor

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.components.lightComponentOrNull
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import java.util.*

private const val ENTITY_CHILDREN_NBT = "AnchoredChildren"

class MaterializationService(
    private val level: Level,
) {
    private val activeSnapshots = linkedMapOf<UUID, EntitySnapshot>()
    private val entityChildrenByHost = linkedMapOf<UUID, LinkedHashSet<UUID>>()
    private val pendingEntitySnapshots = linkedMapOf<UUID, MutableList<EntitySnapshot>>()
    private val activeWorldChunks = linkedSetOf<Long>()
    private val playerVisibleAnchors = linkedMapOf<UUID, MutableSet<UUID>>()
    private val playerChunkPositions = linkedMapOf<UUID, Long>()

    val records: Collection<MaterializedRecord>
        get() = activeSnapshots.entries.mapNotNull { (stableKey, snapshot) ->
            val anchor = snapshot.anchorOrNull() ?: return@mapNotNull null
            MaterializedRecord(stableKey, snapshot, anchor)
        }

    fun forEachModelRecord(action: (MaterializedRecord) -> Unit) {
        records.forEach { record ->
            if (record.snapshot.modelOrNull() != null) action(record)
        }
    }

    fun forEachLightRecord(action: (MaterializedRecord) -> Unit) {
        records.forEach { record ->
            if (record.snapshot.lightComponentOrNull() != null) action(record)
        }
    }

    fun tick() {
        if (level is ServerLevel) {
            updateWorldMaterialization(level)
            syncPlayers(level)
        }
    }

    fun ensurePrimaryEntity(entity: Entity): UUID {
        onHostAvailable(entity)
        return entity.uuid
    }

    fun stableKeyOf(runtimeId: Long): UUID? = null
    fun runtimeIdOf(stableKey: UUID): Long? = null

    fun snapshot(stableKey: UUID): EntitySnapshot? {
        activeSnapshots[stableKey]?.let { return it }
        val serverLevel = level as? ServerLevel ?: return null
        return WorldAnchorSavedData.get(serverLevel).allRecords().firstOrNull { it.stableKey == stableKey }?.snapshot
    }

    fun childStableKeys(hostUuid: UUID): Set<UUID> = entityChildrenByHost[hostUuid].orEmpty().toSet()

    fun materialize(snapshot: EntitySnapshot): Long {
        val stableKey = snapshot.requireStableKey()
        val anchor = snapshot.requireAnchor()
        val normalized = snapshot.withIdentity(anchor)

        when (anchor) {
            is EntityAnchor -> {
                if (!anchor.primary && level.findEntityByUuid(anchor.hostUuid) == null) {
                    pendingEntitySnapshots.computeIfAbsent(anchor.hostUuid) { mutableListOf() }.add(normalized)
                    return 0L
                }
                activeSnapshots[stableKey] = normalized
                if (!anchor.primary) {
                    entityChildrenByHost.computeIfAbsent(anchor.hostUuid) { linkedSetOf() }.add(stableKey)
                }
            }

            is WorldAnchor -> {
                activeSnapshots[stableKey] = normalized
                persistIfWorldAnchored(normalized)
            }
        }

        return 0L
    }

    fun detachPrimaryEntity(stableKey: UUID) {
        val snapshot = activeSnapshots[stableKey] ?: return
        if ((snapshot.anchorOrNull() as? EntityAnchor)?.primary != true) return
        activeSnapshots.remove(stableKey)
    }

    fun remove(stableKey: UUID, syncToClients: Boolean = false): Boolean {
        var removed = false
        val snapshot = activeSnapshots.remove(stableKey)
        if (snapshot != null) {
            removed = true
            val entityAnchor = snapshot.anchorOrNull() as? EntityAnchor
            if (entityAnchor != null && !entityAnchor.primary) {
                entityChildrenByHost[entityAnchor.hostUuid]?.remove(stableKey)
            }
        }

        pendingEntitySnapshots.values.forEach { pending ->
            if (pending.removeIf { it.requireStableKey() == stableKey }) {
                removed = true
            }
        }

        if (level is ServerLevel) {
            if (WorldAnchorSavedData.get(level).remove(stableKey) != null) removed = true
            playerVisibleAnchors.values.forEach { it.remove(stableKey) }
            if (syncToClients) AnchoredEntityRemovePacket(stableKey).sendAllInDimension(level)
        }
        return removed
    }

    fun saveEntityChildren(hostUuid: UUID, tag: net.minecraft.nbt.CompoundTag) {
        val children = captureEntityChildSnapshots(hostUuid)
        if (children.isEmpty()) return

        val list = net.minecraft.nbt.ListTag()
        children.forEach { snapshot ->
            list.add(EntitySerialization.serializeToNbt(snapshot))
        }
        tag.put(ENTITY_CHILDREN_NBT, list)
    }

    fun loadEntityChildren(entity: Entity, tag: net.minecraft.nbt.CompoundTag) {
        val children = mutableListOf<EntitySnapshot>()
        val list = tag.getList(ENTITY_CHILDREN_NBT, net.minecraft.nbt.Tag.TAG_COMPOUND.toInt())
        for (index in 0 until list.size) {
            children += EntitySerialization.deserializeFromNbt(list.getCompound(index))
        }
        if (children.isEmpty()) return
        pendingEntitySnapshots.computeIfAbsent(entity.uuid) { mutableListOf() }.addAll(children)
        onHostAvailable(entity)
    }

    fun moveEntityAnchors(hostUuid: UUID, newLevel: Level) {
        if (newLevel === level) {
            rebindEntityAnchors(hostUuid)
            return
        }
        transferEntityAnchors(hostUuid, MaterializationRuntimeState.service(newLevel))
    }

    fun queuePendingEntitySnapshots(hostUuid: UUID, snapshots: Collection<EntitySnapshot>) {
        if (snapshots.isEmpty()) return
        pendingEntitySnapshots.computeIfAbsent(hostUuid) { mutableListOf() }.addAll(snapshots)
    }

    fun rebindEntityAnchors(hostUuid: UUID) {
        val host = level.findEntityByUuid(hostUuid) ?: return
        onHostAvailable(host)
    }

    fun onHostAvailable(entity: Entity) {
        val pending = pendingEntitySnapshots.remove(entity.uuid).orEmpty()
        if (pending.isEmpty()) return
        pending.forEach(::materialize)
    }

    fun onHostRemoved(hostUuid: UUID) {
        val children = entityChildrenByHost.remove(hostUuid).orEmpty().toList()
        children.forEach { childKey -> remove(childKey) }
        pendingEntitySnapshots.remove(hostUuid)
    }

    fun updateWorldAnchorPosition(stableKey: UUID, position: Vec3) {
        val snapshot = snapshot(stableKey) ?: return
        val current = snapshot.transformOrNull() ?: TransformComponent()
        updateTransform(stableKey, current.withWorldPosition(position))
    }

    fun updateTransform(stableKey: UUID, transform: TransformComponent, syncToClients: Boolean = false): Boolean {
        val current = snapshot(stableKey) ?: return false
        val anchor = current.anchorOrNull() ?: return false
        val updatedAnchor = if (anchor is WorldAnchor) {
            worldAnchorFor(Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble()), anchor.localId)
        } else {
            anchor
        }
        val updated = current.withIdentity(updatedAnchor).withOrReplace(transform)
        activeSnapshots[stableKey] = updated
        persistIfWorldAnchored(updated)
        if (syncToClients) syncSnapshot(updated)
        return true
    }

    fun updateSnapshot(stableKey: UUID, snapshot: EntitySnapshot, syncToClients: Boolean = false): Boolean {
        val anchor = snapshot.anchorOrNull() ?: return false
        val normalizedSnapshot = snapshot.withIdentity(anchor)
        activeSnapshots[stableKey] = normalizedSnapshot
        persistIfWorldAnchored(normalizedSnapshot)
        if (syncToClients) syncSnapshot(normalizedSnapshot)
        return true
    }

    fun syncSnapshot(snapshot: EntitySnapshot) {
        if (level !is ServerLevel) return
        val anchor = snapshot.requireAnchor()
        when (anchor) {
            is EntityAnchor -> {
                val host = level.getEntity(anchor.hostUuid) ?: return
                AnchoredEntitySnapshotPacket(snapshot).sendTrackingEntityAndSelf(host)
            }

            is WorldAnchor -> {
                persistIfWorldAnchored(snapshot)
                val affectedPlayers = level.players()
                    .filterIsInstance<ServerPlayer>()
                    .filter { shouldSeeWorldAnchor(it, anchor) }
                if (affectedPlayers.isNotEmpty()) AnchoredEntitySnapshotPacket(snapshot).send(affectedPlayers)
            }
        }
    }

    fun syncEntityAnchorsToPlayer(player: ServerPlayer, hostEntity: Entity) {
        val hostUuid = hostEntity.uuid
        entityChildrenByHost[hostUuid].orEmpty().forEach { childKey ->
            activeSnapshots[childKey]?.let { AnchoredEntitySnapshotPacket(it).send(player) }
        }
    }

    fun removeEntityAnchorsFromPlayer(player: ServerPlayer, hostUuid: UUID) {
        childStableKeys(hostUuid).forEach { AnchoredEntityRemovePacket(it).send(player) }
    }

    private fun persistIfWorldAnchored(snapshot: EntitySnapshot) {
        val level = level as? ServerLevel ?: return
        val anchor = snapshot.anchorOrNull() as? WorldAnchor ?: return
        val savedData = WorldAnchorSavedData.get(level)
        val stableKey = snapshot.requireStableKey()
        savedData.remove(stableKey)
        savedData.put(DormantRecord(stableKey, snapshot.withIdentity(anchor)))
    }

    private fun captureEntityChildSnapshots(hostUuid: UUID): List<EntitySnapshot> {
        val snapshots = mutableListOf<EntitySnapshot>()
        entityChildrenByHost[hostUuid].orEmpty().forEach { childKey ->
            activeSnapshots[childKey]?.let(snapshots::add)
        }
        snapshots += pendingEntitySnapshots[hostUuid].orEmpty()
        return snapshots
    }

    private fun transferEntityAnchors(hostUuid: UUID, target: MaterializationService) {
        val movedChildren = captureEntityChildSnapshots(hostUuid)
        if (movedChildren.isEmpty()) return

        target.queuePendingEntitySnapshots(hostUuid, movedChildren)
        entityChildrenByHost.remove(hostUuid).orEmpty().forEach { childKey ->
            activeSnapshots.remove(childKey)
        }
        pendingEntitySnapshots.remove(hostUuid)
    }

    private fun updateWorldMaterialization(level: ServerLevel) {
        val savedData = WorldAnchorSavedData.get(level)
        val currentChunks = linkedSetOf<Long>()
        level.chunkSource.chunkMap.getChunks().forEach { holder ->
            currentChunks += ChunkKey.pack(holder.pos.x, holder.pos.z)
        }

        currentChunks.forEach { chunkKey ->
            if (chunkKey in activeWorldChunks) return@forEach
            savedData.forEachRecordInChunk(chunkKey) { record ->
                if (record.stableKey !in activeSnapshots) {
                    activeSnapshots[record.stableKey] = record.snapshot
                }
            }
        }

        activeWorldChunks.forEach { chunkKey ->
            if (chunkKey in currentChunks) return@forEach
            activeSnapshots.entries.removeIf { (_, snapshot) ->
                val worldAnchor = snapshot.anchorOrNull() as? WorldAnchor ?: return@removeIf false
                ChunkKey.pack(worldAnchor.chunkX, worldAnchor.chunkZ) == chunkKey
            }
        }

        activeWorldChunks.clear()
        activeWorldChunks.addAll(currentChunks)
    }

    private fun syncPlayers(level: ServerLevel) {
        level.players()
            .filterIsInstance<ServerPlayer>()
            .forEach { player ->
                val chunkPos = ChunkPos(player.blockPosition())
                val packedChunk = ChunkKey.pack(chunkPos.x, chunkPos.z)
                val previousChunk = playerChunkPositions.put(player.uuid, packedChunk)
                if (previousChunk == packedChunk && player.uuid in playerVisibleAnchors) return@forEach
                syncWorldAnchorsForPlayer(player)
            }
    }

    private fun syncWorldAnchorsForPlayer(player: ServerPlayer) {
        val visible = linkedSetOf<UUID>()
        val viewDistance = player.server.playerList.viewDistance + 1
        val playerChunk = ChunkPos(player.blockPosition())
        WorldAnchorSavedData.get(player.serverLevel()).forEachRecordInChunkRange(playerChunk.x, playerChunk.z, viewDistance) { record ->
            visible += record.stableKey
            AnchoredEntitySnapshotPacket(record.snapshot).send(player)
        }

        val previous = playerVisibleAnchors.put(player.uuid, visible).orEmpty()
        (previous - visible).forEach { removed ->
            AnchoredEntityRemovePacket(removed).send(player)
        }
    }

    private fun shouldSeeWorldAnchor(player: ServerPlayer, anchor: WorldAnchor): Boolean {
        val playerChunk = ChunkPos(player.blockPosition())
        val viewDistance = player.server.playerList.viewDistance + 1
        return kotlin.math.abs(anchor.chunkX - playerChunk.x) <= viewDistance &&
            kotlin.math.abs(anchor.chunkZ - playerChunk.z) <= viewDistance
    }
}

private fun Level.findEntityByUuid(uuid: UUID): Entity? =
    (this as? ServerLevel)?.getEntity(uuid)

object MaterializationRuntimeState {
    private val services = Collections.synchronizedMap(WeakHashMap<Level, MaterializationService>())

    fun init(level: Level) {
        services.computeIfAbsent(level, ::MaterializationService)
    }

    fun service(level: Level): MaterializationService =
        services[level] ?: error("Materialization state is not initialized for $level")

    fun close(level: Level) {
        services.remove(level)
    }
}
