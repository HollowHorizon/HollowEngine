package ru.hollowhorizon.hollowengine.common.geary.binding

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.components.lightComponentOrNull
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntityNodeSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.network.sendAllInDimension
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import java.util.*

class NodeRuntimeService(
    private val level: Level,
) {
    private val activeWorldSnapshots = linkedMapOf<UUID, EntitySnapshot>()
    private val activeWorldChunks = linkedSetOf<Long>()
    private val playerVisibleNodes = linkedMapOf<UUID, MutableSet<UUID>>()
    private val playerChunkPositions = linkedMapOf<UUID, Long>()

    val records: Collection<NodeMaterializedRecord>
        get() {
            val combined = linkedMapOf<UUID, EntitySnapshot>()
            activeWorldSnapshots.forEach { (stableKey, snapshot) -> combined[stableKey] = snapshot }
            GearyRuntimeState.nodeSnapshots(level).forEach { snapshot ->
                combined[snapshot.requireStableKey()] = snapshot
            }
            return combined.entries.mapNotNull { (stableKey, snapshot) ->
                NodeMaterializedRecord(stableKey, snapshot, snapshot.hostUuidOrNull())
            }
        }

    fun forEachModelRecord(action: (NodeMaterializedRecord) -> Unit) {
        records.forEach { record ->
            if (record.snapshot.modelOrNull() != null) action(record)
        }
    }

    fun forEachModelNodeRecord(action: (NodeMaterializedRecord, ModelNodeEntry) -> Unit) {
        records.forEach { record ->
            record.snapshot.modelNodes().forEach { node ->
                action(record, node)
            }
        }
    }

    fun forEachLightRecord(action: (NodeMaterializedRecord) -> Unit) {
        records.forEach { record ->
            if (record.snapshot.lightComponentOrNull() != null) action(record)
        }
    }

    fun forEachLightNodeRecord(action: (NodeMaterializedRecord, LightNodeEntry) -> Unit) {
        records.forEach { record ->
            record.snapshot.lightNodes().forEach { node ->
                action(record, node)
            }
        }
    }

    fun tick() {
        if (level is ServerLevel) {
            updateWorldNodes(level)
            syncPlayers(level)
        }
    }

    fun snapshot(stableKey: UUID): EntitySnapshot? {
        activeWorldSnapshots[stableKey]?.let { return it }
        GearyRuntimeState.nodeSnapshot(level, stableKey)?.let { return it }
        val serverLevel = level as? ServerLevel ?: return null
        return WorldNodeSavedData.get(serverLevel).allRecords().firstOrNull { it.stableKey == stableKey }?.snapshot
    }

    fun childStableKeys(hostUuid: UUID): Set<UUID> =
        GearyRuntimeState.nodeSnapshots(level, hostUuid).mapTo(linkedSetOf()) { it.requireStableKey() }

    fun materialize(snapshot: EntitySnapshot): Long {
        val stableKey = snapshot.requireStableKey()
        val normalized = snapshot

        if (normalized.isEntityBound()) {
            GearyRuntimeState.upsertNodeSnapshot(level, normalized.requireHostUuid(), normalized)
        } else {
            activeWorldSnapshots[stableKey] = normalized
            persistIfWorldBound(normalized)
        }

        return 0L
    }

    fun remove(stableKey: UUID, syncToClients: Boolean = false): Boolean {
        var removed = false
        val snapshot = activeWorldSnapshots.remove(stableKey)
        if (snapshot != null) {
            removed = true
        }
        if (GearyRuntimeState.removeNodeSnapshot(level, stableKey)) removed = true

        if (level is ServerLevel) {
            if (WorldNodeSavedData.get(level).remove(stableKey) != null) removed = true
            playerVisibleNodes.values.forEach { it.remove(stableKey) }
            if (syncToClients) NodeEntityRemovePacket(stableKey).sendAllInDimension(level)
        }
        return removed
    }

    fun moveEntityNodes(hostUuid: UUID, newLevel: Level) {
        // No-op: entity-bound snapshots are stored in GearyRuntimeState and follow host entity by UUID.
    }

    fun queuePendingEntitySnapshots(hostUuid: UUID, snapshots: Collection<EntitySnapshot>) {
        snapshots.forEach { GearyRuntimeState.upsertNodeSnapshot(level, hostUuid, it) }
    }

    fun rebindEntityNodes(hostUuid: UUID) {
        val host = level.findEntityByUuid(hostUuid)
        if (host == null) {
            HollowEngine.LOGGER.warn("Cannot rebind node snapshots: host entity {} was not found in level {}", hostUuid, level.dimension().location())
            return
        }
        onHostAvailable(host)
    }

    fun onHostAvailable(entity: Entity) {
        // No-op: snapshots are attached to host state directly.
    }

    fun onHostRemoved(hostUuid: UUID) {
        GearyRuntimeState.clearNodeSnapshots(level, hostUuid)
    }

    fun updateWorldNodePosition(stableKey: UUID, position: Vec3) {
        val snapshot = snapshot(stableKey)
        if (snapshot == null) {
            HollowEngine.LOGGER.warn("Cannot update world position for node snapshot {}: snapshot not found", stableKey)
            return
        }
        val current = snapshot.transformOrNull() ?: TransformComponent()
        updateTransform(stableKey, current.withWorldPosition(position))
    }

    fun updateTransform(
        stableKey: UUID,
        transform: TransformComponent,
        nodeId: UUID? = null,
        syncToClients: Boolean = false,
    ): Boolean {
        val current = snapshot(stableKey)
        if (current == null) {
            HollowEngine.LOGGER.warn("Cannot update transform for node snapshot {}: snapshot not found", stableKey)
            return false
        }
        val updatedSnapshot = if (current.isWorldBound()) {
            current
                .withWorldBinding(
                    position = Vec3(transform.x.toDouble(), transform.y.toDouble(), transform.z.toDouble()),
                    localId = current.worldLocalIdOrRandom(),
                )
                .withOrReplace(transform, nodeId)
        } else {
            current.withOrReplace(transform, nodeId)
        }
        if (!updateSnapshot(stableKey, updatedSnapshot, syncToClients = syncToClients)) return false
        return true
    }

    fun updateSnapshot(stableKey: UUID, snapshot: EntitySnapshot, syncToClients: Boolean = false): Boolean {
        val normalizedSnapshot = snapshot.copy(stableKey = stableKey)
        if (normalizedSnapshot.isEntityBound()) {
            GearyRuntimeState.upsertNodeSnapshot(level, normalizedSnapshot.requireHostUuid(), normalizedSnapshot)
        } else {
            activeWorldSnapshots[stableKey] = normalizedSnapshot
            persistIfWorldBound(normalizedSnapshot)
        }
        if (syncToClients) syncSnapshot(normalizedSnapshot)
        return true
    }

    fun updateNode(
        stableKey: UUID,
        node: EntityNodeSnapshot,
        syncToClients: Boolean = false,
    ): Boolean {
        val current = snapshot(stableKey)
        if (current == null) {
            HollowEngine.LOGGER.warn("Cannot update node {} in snapshot {}: snapshot not found", node.id, stableKey)
            return false
        }
        val updated = if (current.nodeByIdOrNull(node.id) != null) {
            current.withNodes(current.nodeList().map { if (it.id == node.id) node else it })
        } else {
            current.withAddedNode(
                nodeId = node.id,
                parentId = node.parentId ?: current.rootNode().id,
                nodeComponents = node.components,
            )
        }
        return updateSnapshot(stableKey, updated, syncToClients)
    }

    fun removeNode(
        stableKey: UUID,
        nodeId: UUID,
        syncToClients: Boolean = false,
    ): Boolean {
        val current = snapshot(stableKey)
        if (current == null) {
            HollowEngine.LOGGER.warn("Cannot remove node {} from snapshot {}: snapshot not found", nodeId, stableKey)
            return false
        }
        if (nodeId == current.rootNode().id || current.nodeByIdOrNull(nodeId) == null) {
            HollowEngine.LOGGER.warn("Cannot remove node {} from snapshot {}: node does not exist or is root", nodeId, stableKey)
            return false
        }
        return updateSnapshot(stableKey, current.withRemovedNode(nodeId), syncToClients)
    }

    fun updateNodeComponent(
        stableKey: UUID,
        nodeId: UUID,
        component: Component,
        syncToClients: Boolean = false,
    ): Boolean {
        val current = snapshot(stableKey)
        if (current == null) {
            HollowEngine.LOGGER.warn("Cannot update component on node {} in snapshot {}: snapshot not found", nodeId, stableKey)
            return false
        }
        if (current.nodeByIdOrNull(nodeId) == null) {
            HollowEngine.LOGGER.warn("Cannot update component on node {} in snapshot {}: node not found", nodeId, stableKey)
            return false
        }
        return updateSnapshot(stableKey, current.withOrReplace(component, nodeId), syncToClients)
    }

    fun removeNodeComponent(
        stableKey: UUID,
        nodeId: UUID,
        componentTypeId: ResourceLocation,
        syncToClients: Boolean = false,
    ): Boolean {
        val current = snapshot(stableKey)
        if (current == null) {
            HollowEngine.LOGGER.warn("Cannot remove component {} from node {} in snapshot {}: snapshot not found", componentTypeId, nodeId, stableKey)
            return false
        }
        if (current.nodeByIdOrNull(nodeId) == null) {
            HollowEngine.LOGGER.warn("Cannot remove component {} from node {} in snapshot {}: node not found", componentTypeId, nodeId, stableKey)
            return false
        }
        val descriptor = ComponentDescriptorRegistry.descriptorOrNull(componentTypeId)
        if (descriptor == null) {
            HollowEngine.LOGGER.warn("Cannot remove component {} from node {} in snapshot {}: descriptor not found", componentTypeId, nodeId, stableKey)
            return false
        }
        val updated = current.removeComponents({ it::class == descriptor.value }, nodeId)
        return updateSnapshot(stableKey, updated, syncToClients)
    }

    fun syncSnapshot(snapshot: EntitySnapshot) {
        if (level !is ServerLevel) return
        if (snapshot.isEntityBound()) {
            val hostUuid = snapshot.requireHostUuid()
            val host = level.getEntity(hostUuid)
            if (host == null) {
                HollowEngine.LOGGER.warn("Cannot sync entity-bound snapshot {}: host entity {} not found in level {}", snapshot.requireStableKey(), hostUuid, level.dimension().location())
                return
            }
            NodeEntitySnapshotPacket(snapshot).sendTrackingEntityAndSelf(host)
        } else {
            persistIfWorldBound(snapshot)
            val chunkX = snapshot.requireWorldChunkX()
            val chunkZ = snapshot.requireWorldChunkZ()
            val affectedPlayers = level.players()
                .filterIsInstance<ServerPlayer>()
                .filter { shouldSeeWorldSnapshot(it, chunkX, chunkZ) }
            if (affectedPlayers.isNotEmpty()) NodeEntitySnapshotPacket(snapshot).send(affectedPlayers)
        }
    }

    fun syncEntityNodesToPlayer(player: ServerPlayer, hostEntity: Entity) {
        GearyRuntimeState.nodeSnapshots(level, hostEntity.uuid).forEach { NodeEntitySnapshotPacket(it).send(player) }
    }

    fun removeEntityNodesFromPlayer(player: ServerPlayer, hostUuid: UUID) {
        childStableKeys(hostUuid).forEach { NodeEntityRemovePacket(it).send(player) }
    }

    private fun persistIfWorldBound(snapshot: EntitySnapshot) {
        val level = level as? ServerLevel ?: return
        if (snapshot.isEntityBound()) return
        val savedData = WorldNodeSavedData.get(level)
        val stableKey = snapshot.requireStableKey()
        savedData.remove(stableKey)
        savedData.put(DormantRecord(stableKey, snapshot))
    }

    private fun updateWorldNodes(level: ServerLevel) {
        val savedData = WorldNodeSavedData.get(level)
        val currentChunks = linkedSetOf<Long>()
        level.chunkSource.chunkMap.getChunks().forEach { holder ->
            currentChunks += ChunkKey.pack(holder.pos.x, holder.pos.z)
        }

        currentChunks.forEach { chunkKey ->
            if (chunkKey in activeWorldChunks) return@forEach
            savedData.forEachRecordInChunk(chunkKey) { record ->
                if (record.stableKey !in activeWorldSnapshots) {
                    activeWorldSnapshots[record.stableKey] = record.snapshot
                }
            }
        }

        activeWorldChunks.forEach { chunkKey ->
            if (chunkKey in currentChunks) return@forEach
            activeWorldSnapshots.entries.removeIf { (_, snapshot) ->
                if (snapshot.isEntityBound()) return@removeIf false
                ChunkKey.pack(snapshot.requireWorldChunkX(), snapshot.requireWorldChunkZ()) == chunkKey
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
                if (previousChunk == packedChunk && player.uuid in playerVisibleNodes) return@forEach
                syncWorldNodesForPlayer(player)
            }
    }

    private fun syncWorldNodesForPlayer(player: ServerPlayer) {
        val visible = linkedSetOf<UUID>()
        val viewDistance = player.server.playerList.viewDistance + 1
        val playerChunk = ChunkPos(player.blockPosition())
        WorldNodeSavedData.get(player.serverLevel()).forEachRecordInChunkRange(playerChunk.x, playerChunk.z, viewDistance) { record ->
            visible += record.stableKey
            NodeEntitySnapshotPacket(record.snapshot).send(player)
        }

        val previous = playerVisibleNodes.put(player.uuid, visible).orEmpty()
        (previous - visible).forEach { removed ->
            NodeEntityRemovePacket(removed).send(player)
        }
    }

    private fun shouldSeeWorldSnapshot(player: ServerPlayer, chunkX: Int, chunkZ: Int): Boolean {
        val playerChunk = ChunkPos(player.blockPosition())
        val viewDistance = player.server.playerList.viewDistance + 1
        return kotlin.math.abs(chunkX - playerChunk.x) <= viewDistance &&
            kotlin.math.abs(chunkZ - playerChunk.z) <= viewDistance
    }
}

private fun Level.findEntityByUuid(uuid: UUID): Entity? =
    (this as? ServerLevel)?.getEntity(uuid)

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
