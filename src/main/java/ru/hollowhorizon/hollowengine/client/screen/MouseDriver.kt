package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiComponent.blit
import net.minecraft.util.Mth
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.use
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet

object MouseDriver {
    val texture = "hollowengine:textures/gui/icons/mouse.png".rl
    var enable = false
        set(value) {
            startTime = currentTime
            field = value
        }
    val currentTime: Int
        get() = (Minecraft.getInstance().level?.gameTime ?: 0).toInt()
    private var startTime = 0

    fun draw(stack: PoseStack, x: Int, y: Int, partialTick: Float) {
        var progress = Mth.clamp((currentTime - startTime + partialTick) / 10f, 0f, 1f)

        if(!enable) progress = 1f - progress
        if(progress > 0f) {
            RenderSystem.setShaderTexture(0, texture)
            RenderSystem.setShaderColor(
                1.0f,
                1.0f,
                1.0f,
                progress
            )

            stack.use {
                stack.translate(0.0, 0.0, -90.0)
                blit(stack, x - 6, y - 6, 0f, 0f, 12, 12, 12, 12)
            }
        }
    }
}

@HollowPacketV2(toTarget = NetworkDirection.PLAY_TO_CLIENT)
class DrawMousePacket: Packet<Boolean>({ player, value ->
    MouseDriver.enable = value
})