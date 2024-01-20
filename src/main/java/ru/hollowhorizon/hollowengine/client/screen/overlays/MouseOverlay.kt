package ru.hollowhorizon.hollowengine.client.screen.overlays

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.util.Mth
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.use
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3

object MouseOverlay {
    val texture = "hollowengine:textures/gui/icons/mouse.png".rl
    var enable = false
        set(value) {
            startTime = currentTime
            field = value
        }
    private val currentTime: Int
        get() = (Minecraft.getInstance().level?.gameTime ?: 0).toInt()
    private var startTime = 0

    fun draw(stack: PoseStack, x: Int, y: Int, partialTick: Float) {
        var progress = Mth.clamp((currentTime - startTime + partialTick) / 20f, 0f, 1f)

        if(!enable) progress = 1f - progress
        if(progress > 0f) {
            RenderSystem.enableBlend()
            RenderSystem.defaultBlendFunc()

            RenderSystem.setShaderTexture(0, texture)
            RenderSystem.setShaderColor(
                1.0f,
                1.0f,
                1.0f,
                progress
            )

            stack.use {
                Screen.blit(stack, x - 6, y - 6, 0f, 0f, 12, 12, 12, 12)
            }

            RenderSystem.setShaderColor(
                1.0f,
                1.0f,
                1.0f,
                1.0f
            )
        }
    }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class DrawMousePacket(private val enable: Boolean): HollowPacketV3<DrawMousePacket> {
    override fun handle(player: Player, data: DrawMousePacket) {
        MouseOverlay.enable = data.enable
    }

}