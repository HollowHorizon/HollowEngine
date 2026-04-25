package ru.hollowhorizon.hollowengine.common.geary.binding

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.api.findEntityByUuid
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.components.lightComponentOrNull
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.LevelSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.Snapshot
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

class NodeRuntimeService(
    private val level: Level,
) {
    private val activeLevelSnapshots = linkedMapOf<UUID, LevelSnapshot>()
    private val activeLevelChunks = linkedSetOf<Long>()
    private val playerVisibleLevelSnapshots = linkedMapOf<UUID, MutableSet<UUID>>()
    private val playerChunkPositions = linkedMapOf<UUID, Long>()

    val records: Collection<NodeMaterializedRecord>
        get() {
            val combined = linkedMapOf<UUID, NodeMaterializedRecord>()
            activeLevelSnapshots.forEach { (id, snapshot) ->
                combined[id] = NodeMaterializedRecord(id, snapshot, null)
            }
            GearyRuntimeState.entitySnapshots(level).forEach { (entity, snapshot) ->
                combined[entity.uuid] = NodeMaterializedRecord(entity.uuid, snapshot.withEntity(entity), entity)
            }
            return combined.values
        }

    fun forEachModelRecord(action: (NodeMaterializedRecord) -> Unit) {
        records.forEach { record ->
            if (record.snapshot.modelOrNull() != null) action(record)
        }
    }

    fun forEachModelNodeRecord(action: (NodeMaterializedRecord, ModelNodeEntry) -> Unit) {
        records.forEach { record ->
            record.snapshot.modelNodes().forEach { node -> action(record, node) }
        }
    }

    fun forEachLightRecord(action: (NodeMaterializedRecord) -> Unit) {
        records.forEach { record ->
            if (record.snapshot.lightComponentOrNull() != null) action(record)
        }
    }

    fun forEachLightNodeRecord(action: (NodeMaterializedRecord, LightNodeEntry) -> Unit) {
        records.forEach { record ->
            record.snapshot.lightNodes().forEach { node -> action(record, node) }
        }
    }

    fun tick() {
        if (level is ServerLevel) {
            updateLevelSnapshots(level)
            syncPlayers(level)
        }
    }

    fun snapshot(id: UUID): Snapshot? {
        activeLevelSnapshots[id]?.let { return it }
        GearyRuntimeState.entitySnapshot(level, id)?.let { snapshot ->
            val entity = level.findEntityByUuid(id)
            return if (entity == null) snapshot else snapshot.withEntity(entity)
        }
        val serverLevel = level as? ServerLevel ?: return null
        return WorldNodeSavedData.get(serverLevel).allRecords().firstOrNull { it.id == id }?.snapshot
    }

    fun materialize(snapshot: Snapshot): Long {
        when (snapshot) {
            is LevelSnapshot -> {
                activeLevelSnapshots[snapshot.id] = snapshot
                persist(snapshot)
            }
            is EntitySnapshot -> {
                val entity = snapshot.entity
                if (entity != null) GearyRuntimeState.updateEntitySnapshot(entity, snapshot)
            }
        }
        return 0L
    }

    fun remove(id: UUID, syncToClients: Boolean = false): Boolean {
        var removedLevel = activeLevelSnapshots.remove(id) != null
        val removedEntity = GearyRuntimeState.removeEntitySnapshot(level, id)
        if (level is ServerLevel) {
            if (WorldNodeSavedData.get(level).remove(id) != null) removedLevel = true
            playerVisibleLevelSnapshots.values.forEach { it.remove(id) }
            if (syncToClients && removedLevel) LevelSnapshotRemovePacket(id).sendAllInDimension(level)
            if (syncToClients && removedEntity != null) EntitySnapshotRemovePacket(removedEntity.id).sendTrackingEntityAndSelf(removedEntity)
        }
        return removedLevel || removedEntity != null
    }

    fun updateWorldNodePosition(id: UUID, position: Vec3) {
        val snapshot = snapshot(id) as? LevelSnapshot
        if (snapshot == null) {
            HollowEngine.LOGGER.warn("Cannot update level snapshot {} position: snapshot not found", id)
            return
        }
        updateTransform(id, (snapshot.transformOrNull() ?: TransformComponent()).withWorldPosition(position))
    }

    fun updateTransform(
        id: UUID,
        transform: TransformComponent,
        nodeId: UUID? = null,
        syncToClients: Boolean = false,
    ): Boolean {
        val current = snapshot(id)
        if (current == null) {
            HollowEngine.LOGGER.warn("Cannot update transform for snapshot {}: snapshot not found", id)
            return false
        }
        val updatedSnapshot = current.withOrReplace(transform, nodeId)
        return updateSnapshot(id, updatedSnapshot, syncToClients = syncToClients)
    }

    fun updateSnapshot(id: UUID, snapshot: Snapshot, syncToClients: Boolean = false): Boolean {
        when (snapshot) {
            is LevelSnapshot -> {
                val normalized = snapshot.copy(id = id)
                activeLevelSnapshots[id] = normalized
                persist(normalized)
                if (syncToClients) syncSnapshot(normalized)
            }
            is EntitySnapshot -> {
                val entity = snapshot.entity ?: level.findEntityByUuid(id)
                if (entity == null) {
                    HollowEngine.LOGGER.warn("Cannot update entity snapshot {}: entity not found in level {}", id, level.dimension().location())
                    return false
                }
                val normalized = snapshot.withEntity(entity)
                GearyRuntimeState.updateEntitySnapshot(entity, normalized)
                if (syncToClients) syncSnapshot(normalized)
            }
        }
        return true
    }

    fun syncSnapshot(snapshot: Snapshot) {
        if (level !is ServerLevel) return
        when (snapshot) {
            is EntitySnapshot -> {
                val entity = snapshot.entity ?: return
                EntitySnapshotPacket(entity.id, snapshot).sendTrackingEntityAndSelf(entity)
            }
            is LevelSnapshot -> {
                persist(snapshot)
                val chunkX = snapshot.requireWorldChunkX()
                val chunkZ = snapshot.requireWorldChunkZ()
                val affectedPlayers = level.players()
                    .filterIsInstance<ServerPlayer>()
                    .filter { shouldSeeWorldSnapshot(it, chunkX, chunkZ) }
                if (affectedPlayers.isNotEmpty()) LevelSnapshotPacket(snapshot).send(affectedPlayers)
            }
        }
    }

    fun syncEntityNodesToPlayer(player: ServerPlayer, hostEntity: Entity) {
        GearyRuntimeState.entitySnapshot(level, hostEntity.uuid)
            ?.let { EntitySnapshotPacket(hostEntity.id, it).send(player) }
    }

    fun removeEntityNodesFromPlayer(player: ServerPlayer, hostEntityUuid: UUID) {
        val host = level.findEntityByUuid(hostEntityUuid) ?: return
        EntitySnapshotRemovePacket(host.id).send(player)
    }

    private fun persist(snapshot: LevelSnapshot) {
        val level = level as? ServerLevel ?: return
        WorldNodeSavedData.get(level).put(DormantRecord(snapshot.id, snapshot))
    }

    private fun updateLevelSnapshots(level: ServerLevel) {
        val savedData = WorldNodeSavedData.get(level)
        val currentChunks = linkedSetOf<Long>()
        level.chunkSource.chunkMap.getChunks().forEach { holder ->
            currentChunks += ChunkKey.pack(holder.pos.x, holder.pos.z)
        }

        currentChunks.forEach { chunkKey ->
            if (chunkKey in activeLevelChunks) return@forEach
            savedData.forEachRecordInChunk(chunkKey) { record ->
                if (record.id !in activeLevelSnapshots) {
                    activeLevelSnapshots[record.id] = record.snapshot
                }
            }
        }

        activeLevelChunks.forEach { chunkKey ->
            if (chunkKey in currentChunks) return@forEach
            activeLevelSnapshots.entries.removeIf { (_, snapshot) ->
                ChunkKey.pack(snapshot.requireWorldChunkX(), snapshot.requireWorldChunkZ()) == chunkKey
            }
        }

        activeLevelChunks.clear()
        activeLevelChunks.addAll(currentChunks)
    }

    private fun syncPlayers(level: ServerLevel) {
        level.players()
            .filterIsInstance<ServerPlayer>()
            .forEach { player ->
                val chunkPos = ChunkPos(player.blockPosition())
                val packedChunk = ChunkKey.pack(chunkPos.x, chunkPos.z)
                val previousChunk = playerChunkPositions.put(player.uuid, packedChunk)
                if (previousChunk == packedChunk && player.uuid in playerVisibleLevelSnapshots) return@forEach
                syncWorldNodesForPlayer(player)
            }
    }

    private fun syncWorldNodesForPlayer(player: ServerPlayer) {
        val visible = linkedSetOf<UUID>()
        val viewDistance = player.server.playerList.viewDistance + 1
        val playerChunk = ChunkPos(player.blockPosition())
        WorldNodeSavedData.get(player.serverLevel()).forEachRecordInChunkRange(playerChunk.x, playerChunk.z, viewDistance) { record ->
            visible += record.id
            LevelSnapshotPacket(record.snapshot).send(player)
        }

        val previous = playerVisibleLevelSnapshots.put(player.uuid, visible).orEmpty()
        (previous - visible).forEach { removed ->
            LevelSnapshotRemovePacket(removed).send(player)
        }
    }

    private fun shouldSeeWorldSnapshot(player: ServerPlayer, chunkX: Int, chunkZ: Int): Boolean {
        val playerChunk = ChunkPos(player.blockPosition())
        val viewDistance = player.server.playerList.viewDistance + 1
        return kotlin.math.abs(chunkX - playerChunk.x) <= viewDistance &&
            kotlin.math.abs(chunkZ - playerChunk.z) <= viewDistance
    }
}

object NodeRuntimeState {
    private val services = Collections.synchronizedMap(WeakHashMap<Level, NodeRuntimeService>())

    fun init(level: Level) {
        services.computeIfAbsent(level, ::NodeRuntimeService)
    }

    fun service(level: Level): NodeRuntimeService =
        services[level] ?: error("Node runtime state is not initialized for $level")

    fun close(level: Level) {
        services.remove(level)
    }
}

typealias NodeSceneService = NodeRuntimeService
