package ru.hollowhorizon.hollowengine.common.geary.binding

import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.api.findEntityByUuid
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.Snapshot
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import java.util.*

/**
 * The snapshots materialized in one level. Every snapshot belongs to an entity: nodes that lived in
 * the world on their own were removed together with the lighting they were mostly used for.
 */
class NodeRuntimeService(
    private val level: Level,
) {
    val records: Collection<NodeMaterializedRecord>
        get() = GearyRuntimeState.entitySnapshots(level).map { (entity, snapshot) ->
            NodeMaterializedRecord(entity.uuid, snapshot.withEntity(entity), entity)
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

    fun snapshot(id: UUID): Snapshot? {
        val snapshot = GearyRuntimeState.entitySnapshot(level, id) ?: return null
        val entity = level.findEntityByUuid(id)
        return if (entity == null) snapshot else snapshot.withEntity(entity)
    }

    fun materialize(snapshot: Snapshot) {
        when (snapshot) {
            is EntitySnapshot -> snapshot.entity?.let { GearyRuntimeState.updateEntitySnapshot(it, snapshot) }
        }
    }

    fun remove(id: UUID, syncToClients: Boolean = false): Boolean {
        val removed = GearyRuntimeState.removeEntitySnapshot(level, id) ?: return false
        if (level is ServerLevel && syncToClients) {
            EntitySnapshotRemovePacket(removed.id).sendTrackingEntityAndSelf(removed)
        }
        return true
    }

    fun updateTransform(id: UUID, transform: TransformComponent, nodeId: UUID? = null): Boolean {
        val current = snapshot(id)
        if (current == null) {
            HollowEngine.LOGGER.warn("Cannot update transform for snapshot {}: snapshot not found", id)
            return false
        }
        return updateSnapshot(id, current.withOrReplace(transform, nodeId))
    }

    /**
     * Writing the components is all a caller has to do. [ComponentSync] batches the change out to the
     * clients tracking the entity at the end of the tick.
     */
    fun updateSnapshot(id: UUID, snapshot: Snapshot): Boolean {
        when (snapshot) {
            is EntitySnapshot -> {
                val entity = snapshot.entity ?: level.findEntityByUuid(id)
                if (entity == null) {
                    HollowEngine.LOGGER.warn(
                        "Cannot update entity snapshot {}: entity not found in level {}",
                        id,
                        level.dimension().location(),
                    )
                    return false
                }
                GearyRuntimeState.updateEntitySnapshot(entity, snapshot.withEntity(entity))
            }
        }
        return true
    }

    fun removeEntityNodesFromPlayer(player: ServerPlayer, hostEntityUuid: UUID) {
        val host = level.findEntityByUuid(hostEntityUuid) ?: return
        EntitySnapshotRemovePacket(host.id).send(player)
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
