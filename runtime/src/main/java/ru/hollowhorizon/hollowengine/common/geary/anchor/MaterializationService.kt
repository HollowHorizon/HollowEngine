package ru.hollowhorizon.hollowengine.common.geary.anchor

import com.mineinabyss.geary.engine.EntityProvider
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.get
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.geary.api.UNINITIALIZED_ENTITY_ID
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.applySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.network.send
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import java.util.UUID

private const val ENTITY_CHILDREN_NBT = "AnchoredChildren"

class MaterializationService(
    private val level: Level,
) {
    private val runtimeByStableKey = linkedMapOf<UUID, Long>()
    private val materializedByRuntime = linkedMapOf<Long, MaterializedRecord>()
    private val activeWorldStableKeysByChunk = linkedMapOf<Long, LinkedHashSet<UUID>>()
    private val activeWorldChunkByStableKey = linkedMapOf<UUID, Long>()
    private val entityChildrenByHost = linkedMapOf<UUID, LinkedHashSet<UUID>>()
    private val pendingEntitySnapshots = linkedMapOf<UUID, MutableList<EntitySnapshot>>()
    private val activeWorldChunks = linkedSetOf<Long>()
    private val playerVisibleAnchors = linkedMapOf<UUID, MutableSet<UUID>>()
    private val playerChunkPositions = linkedMapOf<UUID, Long>()

    val records: Collection<MaterializedRecord> get() = materializedByRuntime.values

    fun tick() {
        if (level is ServerLevel) {
            updateWorldMaterialization(level)
            syncPlayers(level)
        }
    }

    fun ensurePrimaryEntity(entity: Entity, runtimeId: Long): UUID {
        val stableKey = entity.uuid
        runtimeByStableKey[stableKey] = runtimeId
        putMaterializedRecord(MaterializedRecord(stableKey, runtimeId, EntityAnchor(entity.uuid, primary = true)))
        with(level.geary) {
            val gearyEntity = runtimeId.toGeary()
            gearyEntity.set(StableKeyComponent(stableKey))
            gearyEntity.set(EntityAnchor(entity.uuid, primary = true))
            gearyEntity.set(PrimaryAnchorObject())
        }
        onHostAvailable(entity)
        return stableKey
    }

    fun stableKeyOf(runtimeId: Long): UUID? = materializedByRuntime[runtimeId]?.stableKey

    fun runtimeIdOf(stableKey: UUID): Long? = runtimeByStableKey[stableKey]

    fun snapshot(stableKey: UUID): EntitySnapshot? {
        val runtimeId = runtimeByStableKey[stableKey] ?: return null
        return with(level.geary) { snapshotOf(runtimeId.toGeary()) }
    }

    fun childStableKeys(hostUuid: UUID): Set<UUID> = entityChildrenByHost[hostUuid].orEmpty().toSet()

    fun materialize(snapshot: EntitySnapshot): Long {
        val stableKey = snapshot.requireStableKey()
        val anchor = snapshot.requireAnchor()
        val existing = runtimeByStableKey[stableKey]
        if (existing != null) {
            with(level.geary) {
                applySnapshot(existing.toGeary(), snapshot)
            }
            putMaterializedRecord(MaterializedRecord(stableKey, existing, anchor))
            persistIfWorldAnchored(snapshot)
            return existing
        }

        return when (anchor) {
            is EntityAnchor -> materializeEntityAnchored(stableKey, anchor, snapshot)
            is WorldAnchor -> materializeWorldAnchored(stableKey, anchor, snapshot)
        }
    }

    fun detachPrimaryEntity(stableKey: UUID, runtimeId: Long) {
        if (runtimeByStableKey[stableKey] != runtimeId) return
        runtimeByStableKey.remove(stableKey)
        materializedByRuntime.remove(runtimeId)
        removeActiveWorldIndex(stableKey)
    }

    fun remove(stableKey: UUID, syncToClients: Boolean = false): Boolean {
        var removed = false
        val runtimeId = runtimeByStableKey.remove(stableKey)
        if (runtimeId != null) {
            removed = true
            val record = materializedByRuntime.remove(runtimeId)
            removeActiveWorldIndex(stableKey)
            val entityAnchor = record?.anchor as? EntityAnchor
            if (entityAnchor != null) {
                if (!entityAnchor.primary) {
                    entityChildrenByHost[entityAnchor.hostUuid]?.remove(stableKey)
                }
            }
            level.geary.entityRemoveProvider.remove(runtimeId.toULong())
        }

        pendingEntitySnapshots.values.forEach { pending ->
            if (pending.removeIf { it.requireStableKey() == stableKey }) {
                removed = true
            }
        }

        if (level is ServerLevel) {
            if (WorldAnchorSavedData.get(level).remove(stableKey) != null) {
                removed = true
            }
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
            val snapshot = EntitySerialization.deserializeFromNbt(list.getCompound(index))
            children += snapshot
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
        transferEntityAnchors(hostUuid, this)
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
        val runtimeId = runtimeByStableKey[stableKey] ?: return
        with(level.geary) {
            val gearyEntity = runtimeId.toGeary()
            val current = gearyEntity.get<TransformComponent>() ?: TransformComponent()
            updateTransform(stableKey, current.withWorldPosition(position))
        }
    }

    fun updateTransform(stableKey: UUID, transform: TransformComponent, syncToClients: Boolean = false): Boolean {
        val runtimeId = runtimeByStableKey[stableKey]
        if (runtimeId != null) {
            with(level.geary) {
                val gearyEntity = runtimeId.toGeary()
                val updatedAnchor = gearyEntity.get<WorldAnchor>()?.let { anchor ->
                    val worldPosition = Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble())
                    worldAnchorFor(worldPosition, anchor.localId)
                } ?: gearyEntity.get<EntityAnchor>()
                if (updatedAnchor is WorldAnchor) {
                    gearyEntity.set(updatedAnchor)
                }
                gearyEntity.set(transform)
                val resolvedAnchor = updatedAnchor ?: materializedByRuntime[runtimeId]?.anchor ?: return false
                putMaterializedRecord(MaterializedRecord(stableKey, runtimeId, resolvedAnchor))
                val snapshot = snapshotOf(gearyEntity)
                persistIfWorldAnchored(snapshot)
                if (syncToClients) syncSnapshot(snapshot)
                return true
            }
        }

        val serverLevel = level as? ServerLevel ?: return false
        val savedData = WorldAnchorSavedData.get(serverLevel)
        val existing = savedData.remove(stableKey) ?: return false
        val anchor = existing.anchor as? WorldAnchor ?: return false
        val updatedSnapshot = existing.snapshot
            .withIdentity(worldAnchorFor(Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble()), anchor.localId), stableKey)
            .withOrReplace(transform)
        savedData.put(DormantRecord(stableKey, updatedSnapshot))
        if (syncToClients) syncSnapshot(updatedSnapshot)
        return true
    }

    fun updateSnapshot(stableKey: UUID, snapshot: EntitySnapshot, syncToClients: Boolean = false): Boolean {
        val anchor = snapshot.anchorOrNull() ?: return false
        val normalizedSnapshot = snapshot.withIdentity(anchor, stableKey)

        val runtimeId = runtimeByStableKey[stableKey]
        if (runtimeId != null) {
            with(level.geary) {
                applySnapshot(runtimeId.toGeary(), normalizedSnapshot)
            }
            putMaterializedRecord(MaterializedRecord(stableKey, runtimeId, anchor))
            persistIfWorldAnchored(normalizedSnapshot)
            if (syncToClients) syncSnapshot(normalizedSnapshot)
            return true
        }

        val serverLevel = level as? ServerLevel ?: return false
        val savedData = WorldAnchorSavedData.get(serverLevel)
        if (savedData.remove(stableKey) == null) return false
        savedData.put(DormantRecord(stableKey, normalizedSnapshot))
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
        val primaryRuntime = runtimeByStableKey[hostEntity.uuid] ?: return
        val primarySnapshot = with(level.geary) { snapshotOf(primaryRuntime.toGeary()) }
            .withIdentity(EntityAnchor(hostEntity.uuid, primary = true), hostEntity.uuid)
            .withOrReplace(PrimaryAnchorObject())
        AnchoredEntitySnapshotPacket(primarySnapshot).send(player)

        entityChildrenByHost[hostEntity.uuid].orEmpty().forEach { childKey ->
            val runtimeId = runtimeByStableKey[childKey] ?: return@forEach
            val snapshot = with(level.geary) { snapshotOf(runtimeId.toGeary()) }
            AnchoredEntitySnapshotPacket(snapshot).send(player)
        }
    }

    fun removeEntityAnchorsFromPlayer(player: ServerPlayer, hostUuid: UUID) {
        childStableKeys(hostUuid).forEach { AnchoredEntityRemovePacket(it).send(player) }
    }

    private fun materializeEntityAnchored(stableKey: UUID, anchor: EntityAnchor, snapshot: EntitySnapshot): Long {
        val hostRuntime = runtimeByStableKey[anchor.hostUuid]
        if (hostRuntime == null) {
            pendingEntitySnapshots.computeIfAbsent(anchor.hostUuid) { mutableListOf() }.add(snapshot)
            return runtimeByStableKey[stableKey] ?: UNINITIALIZED_ENTITY_ID
        }

        if (anchor.primary) {
            with(level.geary) {
                applySnapshot(hostRuntime.toGeary(), snapshot)
            }
            runtimeByStableKey[stableKey] = hostRuntime
            putMaterializedRecord(MaterializedRecord(stableKey, hostRuntime, anchor))
            return hostRuntime
        }

        val runtimeId = createRuntimeEntity()
        with(level.geary) {
            applySnapshot(runtimeId.toGeary(), snapshot)
        }
        runtimeByStableKey[stableKey] = runtimeId
        putMaterializedRecord(MaterializedRecord(stableKey, runtimeId, anchor))
        entityChildrenByHost.computeIfAbsent(anchor.hostUuid) { linkedSetOf() }.add(stableKey)
        return runtimeId
    }

    private fun materializeWorldAnchored(stableKey: UUID, anchor: WorldAnchor, snapshot: EntitySnapshot): Long {
        val runtimeId = createRuntimeEntity()
        with(level.geary) {
            applySnapshot(runtimeId.toGeary(), snapshot)
        }
        runtimeByStableKey[stableKey] = runtimeId
        putMaterializedRecord(MaterializedRecord(stableKey, runtimeId, anchor))
        persistIfWorldAnchored(snapshot)
        return runtimeId
    }

    private fun createRuntimeEntity(): Long =
        level.geary.get<EntityProvider>().create().toLong()

    private fun persistIfWorldAnchored(snapshot: EntitySnapshot) {
        val level = level as? ServerLevel ?: return
        val anchor = snapshot.anchorOrNull() as? WorldAnchor ?: return
        val savedData = WorldAnchorSavedData.get(level)
        savedData.remove(snapshot.requireStableKey())
        savedData.put(
            DormantRecord(
                stableKey = snapshot.requireStableKey(),
                snapshot = snapshot.withIdentity(anchor, snapshot.requireStableKey()),
            )
        )
    }

    private fun captureEntityChildSnapshots(hostUuid: UUID): List<EntitySnapshot> {
        val snapshots = mutableListOf<EntitySnapshot>()
        entityChildrenByHost[hostUuid].orEmpty().forEach { childKey ->
            val runtimeId = runtimeByStableKey[childKey] ?: return@forEach
            val snapshot = with(level.geary) { snapshotOf(runtimeId.toGeary()) }
            snapshots += snapshot
        }
        snapshots += pendingEntitySnapshots[hostUuid].orEmpty()
        return snapshots
    }

    private fun transferEntityAnchors(hostUuid: UUID, target: MaterializationService) {
        val movedChildren = captureEntityChildSnapshots(hostUuid)
        if (movedChildren.isEmpty()) return

        target.queuePendingEntitySnapshots(hostUuid, movedChildren)
        entityChildrenByHost.remove(hostUuid).orEmpty().forEach { childKey ->
            remove(childKey)
        }
        pendingEntitySnapshots.remove(hostUuid)
    }

    private fun putMaterializedRecord(record: MaterializedRecord) {
        materializedByRuntime[record.runtimeId] = record
        updateActiveWorldIndex(record.stableKey, record.anchor)
    }

    private fun updateActiveWorldIndex(stableKey: UUID, anchor: AnchorComponent) {
        removeActiveWorldIndex(stableKey)
        val worldAnchor = anchor as? WorldAnchor ?: return
        val chunkKey = ChunkKey.pack(worldAnchor.chunkX, worldAnchor.chunkZ)
        activeWorldChunkByStableKey[stableKey] = chunkKey
        activeWorldStableKeysByChunk.computeIfAbsent(chunkKey) { linkedSetOf() }.add(stableKey)
    }

    private fun removeActiveWorldIndex(stableKey: UUID) {
        val chunkKey = activeWorldChunkByStableKey.remove(stableKey) ?: return
        activeWorldStableKeysByChunk[chunkKey]?.let { stableKeys ->
            stableKeys.remove(stableKey)
            if (stableKeys.isEmpty()) activeWorldStableKeysByChunk.remove(chunkKey)
        }
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
                if (record.stableKey !in runtimeByStableKey) {
                    materialize(record.snapshot)
                }
            }
        }

        activeWorldChunks.forEach { chunkKey ->
            if (chunkKey in currentChunks) return@forEach
            activeWorldStableKeysByChunk[chunkKey]
                ?.toList()
                ?.forEach(::remove)
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

object MaterializationRuntimeState {
    private val services = java.util.Collections.synchronizedMap(java.util.WeakHashMap<Level, MaterializationService>())

    fun init(level: Level) {
        services.computeIfAbsent(level, ::MaterializationService)
    }

    fun service(level: Level): MaterializationService =
        services[level] ?: error("Materialization state is not initialized for $level")

    fun close(level: Level) {
        services.remove(level)
    }
}
