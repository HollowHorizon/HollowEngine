package ru.hollowhorizon.hollowengine.client.video

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3

class VideoScreen(
    resourceType: VideoSource,
    resource: String,
    framesPerSecond: Double,
    sizeX: Int, sizeY: Int,
    muted: Boolean
) : Screen("".mcText) {
    var texture = VideoFrameTexture(NativeImage(sizeX, sizeY, false))
    private val video = Video(resourceType, resource, texture, framesPerSecond, muted)

    override fun tick() {
        video.update()
        if (video.isEnd) onClose()
    }

    override fun render(pPoseStack: PoseStack, pMouseX: Int, pMouseY: Int, pPartialTick: Float) {
        super.render(pPoseStack, pMouseX, pMouseY, pPartialTick)

        RenderSystem.setShaderTexture(0, texture.id)
        blit(pPoseStack, 0, 0, 0f, 0f, width, height, width, height)
    }

    override fun onClose() {
        super.onClose()
        video.stop()
    }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class PlayVideoPacket(
    var resourceType: VideoSource = VideoSource.FILE,
    var resource: String = "untitled.mp4",
    var framesPerSecond: Double = 60.0,
    var sizeX: Int = 1920, var sizeY: Int = 1080,
    var muted: Boolean = false
) : HollowPacketV3<PlayVideoPacket> {
    override fun handle(player: Player, data: PlayVideoPacket) {
        data.apply {
            Minecraft.getInstance().setScreen(
                VideoScreen(resourceType, resource, framesPerSecond, sizeX, sizeY, muted)
            )
        }
    }
}