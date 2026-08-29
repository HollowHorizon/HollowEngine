package ru.hollowhorizon.hollowengine.addons.acoustic.client

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.bmp.acoustic.client.AcousticClientManager
import org.bmp.acoustic.client.source.AcousticAttachmentHandle
import org.bmp.acoustic.client.source.AcousticClientAttachments
import org.bmp.acoustic.client.source.AcousticEmitterTransform
import ru.hollowhorizon.hollowengine.addons.acoustic.HollowModelAcousticTarget
import ru.hollowhorizon.hollowengine.addons.acoustic.HollowModelAnchorKind
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.extensions
import ru.hollowhorizon.hollowengine.common.addons.minecraft
import ru.hollowhorizon.hollowengine.common.addons.subscribe
import ru.hollowhorizon.hollowengine.common.attachments.api.findEntityByUuid
import ru.hollowhorizon.hollowengine.common.attachments.binding.NodeRuntimeState
import ru.hollowhorizon.hollowengine.common.attachments.binding.modelNodes
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.client.models.internal.v2.modelInstanceOrNull
import ru.hollowhorizon.hollowengine.client.render.resolveNodeWorldTransform
import ru.hollowhorizon.hollowengine.common.utils.math.MutableMat4f
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f

internal object AcousticClientIntegration {
    fun install(context: HollowAddonContext) {
        context.extensions.onUnload(AcousticModelAttachmentPublisher::close)
        context.minecraft.subscribe<RenderLevelStageEvent>(priority = -10) { event ->
            if (event.stage == RenderStage.AFTER_SKY) {
                AcousticModelAttachmentPublisher.update(event.partialTick)
            }
        }
    }
}

internal object AcousticModelAttachmentPublisher : AutoCloseable {
    private val lock = Any()
    private val tracked = LinkedHashMap<ResourceLocation, TrackedTarget>()
    private val playbackTargets = HashMap<String, PlaybackTarget>()
    private var level: ClientLevel? = null

    fun setTarget(playbackId: String, target: HollowModelAcousticTarget?, sourceLevel: Level) {
        val clientLevel = sourceLevel as? ClientLevel ?: return
        synchronized(lock) {
            changeLevel(clientLevel)
            val previous = playbackTargets[playbackId]
            if (target == null) {
                removePlayback(playbackId)
                return
            }
            if (previous?.attachmentId == target.attachmentId) return

            removePlayback(playbackId)
            val trackedTarget = tracked.getOrPut(target.attachmentId) {
                TrackedTarget(target, AcousticClientAttachments.register(target.attachmentId))
            }
            trackedTarget.playbackIds += playbackId
            playbackTargets[playbackId] = PlaybackTarget(
                attachmentId = target.attachmentId,
                hasPlayed = AcousticClientManager.isPlaying(playbackId),
            )
        }
    }

    fun update(partialTick: Float) {
        val (currentLevel, snapshot) = synchronized(lock) {
            val currentLevel = level ?: return
            removeFinishedPlaybacks()
            currentLevel to tracked.values.toList()
        }
        snapshot.forEach { trackedTarget -> update(currentLevel, trackedTarget, partialTick) }
    }

    override fun close() {
        synchronized(lock) {
            tracked.values.forEach { target -> target.handle.close() }
            tracked.clear()
            playbackTargets.clear()
            level = null
        }
    }

    private fun update(level: ClientLevel, trackedTarget: TrackedTarget, partialTick: Float) {
        val previousEntity = synchronized(lock) {
            if (tracked[trackedTarget.target.attachmentId] !== trackedTarget) return
            trackedTarget.entity
        }
        if (previousEntity.isPermanentlyRemoved()) {
            remove(trackedTarget)
            return
        }

        val entity = previousEntity
            ?.takeUnless(Entity::isRemoved)
            ?: level.findEntityByUuid(trackedTarget.target.entityUuid)
        if (entity == null) {
            markUnavailable(trackedTarget)
            return
        }
        if (entity.isPermanentlyRemoved()) {
            remove(trackedTarget)
            return
        }
        synchronized(lock) {
            if (tracked[trackedTarget.target.attachmentId] !== trackedTarget) return
            trackedTarget.entity = entity
        }

        val modelNode = NodeRuntimeState.service(level)
            .snapshot(entity.uuid)
            ?.modelNodes()
            ?.singleOrNull { node -> node.nodeId == trackedTarget.target.nodeId }
        if (modelNode == null) {
            markUnavailable(trackedTarget)
            return
        }
        val attachment = entity.modelInstanceOrNull(modelNode.nodeId, modelNode.model.model)?.attachment
        if (attachment == null) {
            markUnavailable(trackedTarget)
            return
        }
        val localNode = attachment.resolve(trackedTarget.target)
        if (trackedTarget.target.anchorKind != HollowModelAnchorKind.ROOT && localNode == null) {
            markUnavailable(trackedTarget)
            return
        }

        val matrix = MutableMat4f().set(resolveNodeWorldTransform(entity, modelNode.transform, partialTick).matrixF)
        localNode?.let { node -> matrix.mul(node.globalMatrix) }
        val position = matrix.transform(Vec3f.ZERO, 1f, MutableVec3f())
        synchronized(lock) {
            if (tracked[trackedTarget.target.attachmentId] !== trackedTarget) return
            trackedTarget.handle.update(
                AcousticEmitterTransform(
                    Vec3(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()),
                    entity.deltaMovement,
                    null,
                ),
            )
            trackedTarget.hasPosition = true
            trackedTarget.isUnavailable = false
        }
    }

    private fun markUnavailable(target: TrackedTarget) {
        synchronized(lock) {
            if (tracked[target.target.attachmentId] === target) target.markUnavailableIfNeeded()
        }
    }

    private fun remove(target: TrackedTarget) {
        synchronized(lock) {
            if (!tracked.remove(target.target.attachmentId, target)) return
            target.playbackIds.forEach(playbackTargets::remove)
            target.handle.close()
        }
    }

    private fun changeLevel(next: ClientLevel) {
        if (level === next) return
        tracked.values.forEach { target -> target.handle.close() }
        tracked.clear()
        playbackTargets.clear()
        level = next
    }

    private fun removeFinishedPlaybacks() {
        val finished = buildList {
            playbackTargets.forEach { (playbackId, target) ->
                if (AcousticClientManager.isPlaying(playbackId)) {
                    target.hasPlayed = true
                    target.inactiveChecks = 0
                } else if (target.hasPlayed || ++target.inactiveChecks >= MAX_PENDING_CHECKS) {
                    add(playbackId)
                }
            }
        }
        finished.forEach(::removePlayback)
    }

    private fun removePlayback(playbackId: String) {
        val playbackTarget = playbackTargets.remove(playbackId) ?: return
        val trackedTarget = tracked[playbackTarget.attachmentId] ?: return
        trackedTarget.playbackIds -= playbackId
        if (trackedTarget.playbackIds.isEmpty()) {
            tracked.remove(playbackTarget.attachmentId)
            trackedTarget.handle.close()
        }
    }

    private fun ModelAttachment.resolve(target: HollowModelAcousticTarget): RuntimeNode? = when (target.anchorKind) {
        HollowModelAnchorKind.ROOT -> null
        HollowModelAnchorKind.UNIQUE_BONE_NAME -> allNodes()
            .filter { node -> node.name == target.anchorSegments.single() }
            .singleOrNull()
        HollowModelAnchorKind.BONE_PATH -> allNodePaths()
            .filter { (_, path) ->
                path == target.anchorSegments || path.size > 1 && path.drop(1) == target.anchorSegments
            }
            .map(Pair<RuntimeNode, List<String>>::first)
            .singleOrNull()
    }

    private fun ModelAttachment.allNodes(): Sequence<RuntimeNode> =
        nodes.asSequence().flatMap { node -> node.walkNodes() }

    private fun RuntimeNode.walkNodes(): Sequence<RuntimeNode> = sequence {
        yield(this@walkNodes)
        children.forEach { child -> yieldAll(child.walkNodes()) }
    }

    private fun ModelAttachment.allNodePaths(): Sequence<Pair<RuntimeNode, List<String>>> = sequence {
        nodes.forEach { root -> yieldAll(root.walkPaths(emptyList())) }
    }

    private fun RuntimeNode.walkPaths(parentPath: List<String>): Sequence<Pair<RuntimeNode, List<String>>> = sequence {
        val path = parentPath + name
        yield(this@walkPaths to path)
        children.forEach { child -> yieldAll(child.walkPaths(path)) }
    }

    private fun Entity?.isPermanentlyRemoved(): Boolean =
        this?.removalReason == Entity.RemovalReason.KILLED || (this is LivingEntity && isDeadOrDying)

    private data class TrackedTarget(
        val target: HollowModelAcousticTarget,
        val handle: AcousticAttachmentHandle,
        var entity: Entity? = null,
        var hasPosition: Boolean = false,
        var isUnavailable: Boolean = false,
        val playbackIds: MutableSet<String> = LinkedHashSet(),
    ) {
        fun markUnavailableIfNeeded() {
            if (!hasPosition || isUnavailable) return
            handle.markTemporarilyUnavailable()
            isUnavailable = true
        }
    }

    private data class PlaybackTarget(
        val attachmentId: ResourceLocation,
        var hasPlayed: Boolean,
        var inactiveChecks: Int = 0,
    )

    /** Drops a tracking packet only if its immediately following Acoustic play packet never arrives. */
    private const val MAX_PENDING_CHECKS = 600
}
