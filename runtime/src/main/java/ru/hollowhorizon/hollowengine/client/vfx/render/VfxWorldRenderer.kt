package ru.hollowhorizon.hollowengine.client.vfx.render

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import org.joml.Matrix4f
import ru.hollowhorizon.hollowengine.client.vfx.VfxBoneBindings
import ru.hollowhorizon.hollowengine.client.vfx.VfxInstance
import ru.hollowhorizon.hollowengine.client.vfx.VfxScenes
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage

/**
 * Ticks and draws the effects of the world.
 */
@ClientOnly
object VfxWorldRenderer {
    private val quads = ArrayList<VfxQuadDraw>()

    @SubscribeEvent
    fun onRenderLevel(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_PARTICLES) return

        val camera = event.camera.position
        val scene = VfxScenes.current()
        val bones = VfxBoneBindings.drain(camera)
        if (scene == null && bones.isEmpty()) return

        scene?.update()

        val levelPose = event.poseStack.last().pose()
        quads.clear()

        scene?.forEachInstance { instance, _ ->
            val placement = placeInWorld(instance, levelPose, camera.x, camera.y, camera.z)
            VfxDrawCollector.collectQuads(instance, placement, quads)
            VfxDrawCollector.renderMeshes(instance, placement)
        }
        bones.forEach { binding ->
            VfxDrawCollector.collectQuads(binding.instance, binding.placement, quads)
            VfxDrawCollector.renderMeshes(binding.instance, binding.placement)
        }

        Minecraft.getInstance().renderBuffers().bufferSource().endBatch()
        if (quads.isEmpty()) return

        VfxQuadRenderer.render(
            draws = quads,
            view = VfxView.ofCamera(event.camera, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix()),
        )
        quads.clear()
    }

    private fun placeInWorld(
        instance: VfxInstance,
        levelPose: Matrix4f,
        cameraX: Double,
        cameraY: Double,
        cameraZ: Double,
    ): Matrix4f = Matrix4f(levelPose).translate(
        (instance.origin.x - cameraX).toFloat(),
        (instance.origin.y - cameraY).toFloat(),
        (instance.origin.z - cameraZ).toFloat(),
    )
}
