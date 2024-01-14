package ru.hollowhorizon.hollowengine.client.video

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl

class VideoScreen: Screen("".mcText) {
    private lateinit var video: Video
    lateinit var texture: VideoFrameTexture

    override fun init() {
        super.init()

        texture = VideoFrameTexture(NativeImage(864, 488, false))
        video = Video("video.mp4", "hollowengine:videos/example".rl, texture, 30.0, true)
    }

    override fun tick() {
        video.update()
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick)

        RenderSystem.setShaderTexture(0, texture.id)
        blit(pPoseStack, 0, 0, 0f, 0f, width, height, width, height)
    }
}