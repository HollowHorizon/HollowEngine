package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.math.Vec3f
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderArmEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent

object CutsceneCameraSystem {
    private var controller: CutscenePlaybackController? = null

    val activeController: CutscenePlaybackController?
        get() = controller

    val currentPose: CameraPose?
        get() = controller?.currentPose

    fun play(data: CutsceneData) {
        controller = CutscenePlaybackController().also {
            it.setupTracks(data)
            it.play()
        }
    }

    fun play(controller: CutscenePlaybackController) {
        this.controller = controller
        controller.play()
    }

    fun preview(controller: CutscenePlaybackController) {
        this.controller = controller
        controller.pause()
    }

    fun stop() {
        controller?.stop()
        controller = null
    }

    fun update(minecraft: Minecraft) {
        val active = controller ?: return
        if (minecraft.level == null || minecraft.player == null) {
            stop()
            return
        }

        active.update(minecraft.timer.realtimeDeltaTicks / 20f)
        if (!active.isPlaying && active.currentTime >= active.duration) {
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
        if (controller?.isPlaying == true) event.isCanceled = true
    }

    @SubscribeEvent
    fun onRenderHand(event: RenderArmEvent) {
        if (controller?.isPlaying == true) event.isCanceled = true
    }
}
