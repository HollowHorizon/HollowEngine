package ru.hollowhorizon.hollowengine.client.render

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.debug.DebugRenderer
import net.minecraft.client.renderer.debug.PathfindingRenderer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcPathDebugPacket
import ru.hollowhorizon.hollowengine.common.npcs.navigation.NpcPathDebugPoint

@ClientOnly
object NpcPathDebugRenderer {
    private val paths = mutableMapOf<Int, DebugPath>()

    fun update(packet: NpcPathDebugPacket) {
        if (packet.nodes.isEmpty()) {
            paths.remove(packet.entityId)
            return
        }

        val nodes = packet.nodes.map { debugNode ->
            Node(debugNode.x, debugNode.y, debugNode.z).apply {
                type = PathType.valueOf(debugNode.type)
                costMalus = debugNode.costMalus
            }
        }
        val path = Path(
            nodes,
            BlockPos(packet.targetX, packet.targetY, packet.targetZ),
            packet.reached,
        ).apply {
            nextNodeIndex = packet.nextNodeIndex.coerceIn(0, nodes.lastIndex)
        }
        paths[packet.entityId] = DebugPath(path, packet.steeringTarget, Util.getMillis())
    }

    @SubscribeEvent
    fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_ENTITIES || paths.isEmpty()) return

        val now = Util.getMillis()
        paths.values.removeIf { now - it.updatedAt > PATH_TIMEOUT_MS }
        if (paths.isEmpty()) return

        val camera = event.camera.position
        val bufferSource = Minecraft.getInstance().renderBuffers().bufferSource()
        for ((_, debugPath) in paths) {
            PathfindingRenderer.renderPath(
                event.poseStack,
                bufferSource,
                debugPath.path,
                NODE_RADIUS,
                false,
                true,
                camera.x,
                camera.y,
                camera.z,
            )
            renderSteeringTarget(event, bufferSource, debugPath.steeringTarget)
        }
        bufferSource.endBatch()
    }

    private fun renderSteeringTarget(
        event: RenderLevelStageEvent,
        bufferSource: MultiBufferSource,
        target: NpcPathDebugPoint,
    ) {
        val camera = event.camera.position
        val bounds = AABB.ofSize(
            Vec3(target.x, target.y + MARKER_HEIGHT * 0.5, target.z),
            MARKER_SIZE,
            MARKER_HEIGHT,
            MARKER_SIZE,
        ).move(-camera.x, -camera.y, -camera.z)
        DebugRenderer.renderFilledBox(event.poseStack, bufferSource, bounds, 0.0f, 1.0f, 1.0f, 0.8f)
    }

    private data class DebugPath(
        val path: Path,
        val steeringTarget: NpcPathDebugPoint,
        val updatedAt: Long,
    )

    private const val PATH_TIMEOUT_MS = 2_000L
    private const val NODE_RADIUS = 0.3f
    private const val MARKER_SIZE = 0.2
    private const val MARKER_HEIGHT = 0.7
}
