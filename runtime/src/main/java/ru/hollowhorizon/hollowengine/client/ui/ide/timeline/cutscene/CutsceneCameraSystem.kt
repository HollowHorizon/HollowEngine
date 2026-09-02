package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene

import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.dialogue.StoryCameraSystem
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderItemInHandEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent

@ClientOnly
object CutsceneCameraSystem {
    private var controller: CutscenePlaybackController? = null
    private var isPreview = false

    val activeController: CutscenePlaybackController?
        get() = controller

    /**
     * The pose the camera should take. A cutscene being played wins over a story's own `@camera`,
     * because it is the more specific instruction; the render bridge reads this one property, so both
     * arrive through the same path.
     */
    val currentPose: CameraPose?
        get() = controller?.currentPose ?: StoryCameraSystem.currentPose

    /** Whether anything is holding the camera, a cutscene or a story. */
    val isOverriding: Boolean get() = currentPose != null

    fun play(data: CutsceneData, loop: Boolean = false, anchor: CutsceneAnchor = CutsceneAnchor.WHERE_RECORDED) {
        isPreview = false
        controller = CutscenePlaybackController().also {
            it.setupTracks(data, loop, anchor)
            it.play()
        }
    }

    fun play(controller: CutscenePlaybackController) {
        isPreview = false
        this.controller = controller
        controller.play()
    }

    fun preview(controller: CutscenePlaybackController) {
        isPreview = true
        this.controller = controller
        controller.pause()
    }

    fun stop() {
        controller?.stop()
        controller = null
        isPreview = false
    }

    fun update(minecraft: Minecraft) {
        StoryCameraSystem.update()
        val active = controller ?: return
        if (minecraft.level == null || minecraft.player == null) {
            stop()
            return
        }

        active.update(minecraft.timer.realtimeDeltaTicks / 20f)
        if (!isPreview && !active.isPlaying && active.currentTime >= active.duration) {
            controller = null
        }
    }

    fun capturePlayerPose(minecraft: Minecraft = Minecraft.getInstance()): CameraPose? {
        val player = minecraft.player ?: return null
        val eye = player.getEyePosition(1f)
        return CameraPose(
            position = eye.toVec3f(),
            rotation = Vec3f(player.xRot, player.yRot, 0f),
            fov = minecraft.options.fov().get().toFloat(),
        )
    }

    private fun Vec3.toVec3f(): Vec3f = Vec3f(x.toFloat(), y.toFloat(), z.toFloat())

    @SubscribeEvent
    fun onRenderOverlays(event: RenderOverlayEvent.Pre) {
        if (controller != null) event.isCanceled = true
    }

    @SubscribeEvent
    fun onRenderHand(event: RenderItemInHandEvent) {
        if (controller != null) event.isCanceled = true
    }
}
