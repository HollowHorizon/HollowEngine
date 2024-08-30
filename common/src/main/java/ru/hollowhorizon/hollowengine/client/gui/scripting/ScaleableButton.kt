package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Button.CreateNarration
import net.minecraft.client.gui.components.Button.OnPress
import net.minecraft.client.gui.components.Tooltip
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.GuiAnimator
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.rl

class ScaleableButton(
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    val image: String,
    val tooltipText: String = "",
    onPress: Button.() -> Unit = {},
) : Button(x, y, w, h, "".literal, OnPress { onPress(it) }, CreateNarration { it.get() }) {
    var lastHovered = false
    var animation = GuiAnimator.Single(0, 0, 10, Interpolation.SINE_IN::invoke)

    init {
        tooltip = Tooltip.create(tooltipText.literal)
    }

    override fun renderWidget(guiGraphics: GuiGraphics, x: Int, y: Int, f: Float) {
        if (lastHovered != isHovered) {
            animation = if (isHovered) GuiAnimator.Single(0, 10, 5, Interpolation.SINE_IN::invoke)
            else GuiAnimator.Single(10, 0, 5, Interpolation.SINE_IN::invoke)
        }

        animation.update()
        val progress = animation.value / 10f

        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()

        val stack = guiGraphics.pose()

        stack.pushPose()

        stack.translate(this.x.toDouble(), this.y.toDouble(), 0.0)

        val scale = 1f + 0.6f * progress
        stack.scale(scale, scale, scale)
        val tX = (scale * width - width) / 4.0
        val tY = (scale * height - height) / 4.0
        stack.translate(-tX, -tY, 70.0)

        val color = 0.6f + 0.4f * progress
        RenderSystem.setShaderColor(color, color, color, color)
        guiGraphics.blit(image.rl, 0, 0, 0f, 0f, width, height, width, height)

        stack.popPose()

        lastHovered = isHovered
    }
}