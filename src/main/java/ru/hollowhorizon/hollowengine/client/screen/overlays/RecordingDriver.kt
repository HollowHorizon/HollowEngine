package ru.hollowhorizon.hollowengine.client.screen.overlays

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.client.handlers.ClientTickHandler
import ru.hollowhorizon.hc.client.screens.util.Anchor
import ru.hollowhorizon.hc.client.utils.drawScaled
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.rl

object RecordingDriver {
    val texture = "hollowengine:textures/gui/icons/recording.png".rl
    private var startTime = 0
    var enable = false
        set(value) {
            field = value
            resetTime()
        }

    fun draw(stack: PoseStack, x: Int, y: Int, partialTick: Float) {
        if (!enable) return

        val progress = (ClientTickHandler.ticks - startTime + partialTick) / 20f

        RenderSystem.setShaderTexture(0, texture)
        Screen.blit(stack, x, y, 0f, 0f, 16, 16, 16, 16)

        Minecraft.getInstance().font.drawScaled(
            stack, Anchor.START, String.format("%.3f", progress).mcText, x + 18, y + 4, 0x0CA7f5, 1.2f
        )
    }

    fun resetTime() {
        startTime = ClientTickHandler.ticks
    }
}