package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.Blaze3D
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractButton
import net.minecraft.client.gui.components.Button.OnPress
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.common.utils.rl

class ImageTextButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    image: String,
    hovered: String,
    val onPress: Runnable,
) : AbstractButton(x, y, width, height, "".literal) {
    val backSprite = image.rl
    val frontSprite = hovered.rl
    private var animationTicks = Blaze3D.getTime()
    private var wasHoveredOrFocused = false
    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {

        if (wasHoveredOrFocused != isHoveredOrFocused) animationTicks = Blaze3D.getTime()

        var transparency = Interpolation.SINE_OUT(((Blaze3D.getTime() - animationTicks) * 2).toFloat().coerceAtMost(1f))
        if (!isHoveredOrFocused) transparency = 1f - transparency
        blit(graphics.pose(), backSprite, x, y, width, height, 1f - transparency)
        blit(graphics.pose(), frontSprite, x, y, width, height, transparency)

        wasHoveredOrFocused = isHoveredOrFocused
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {}
    override fun onPress() {
        onPress.run()
    }

    fun blit(stack: PoseStack, sprite: ResourceLocation, x: Int, y: Int, width: Int, height: Int, transparency: Float) {
        RenderSystem.setShaderTexture(0, sprite)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        val pose = stack.last().pose()
        val tesselator = Tesselator.getInstance()
        val builder = tesselator.builder
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        builder.vertex(pose, x.toFloat(), y.toFloat(), 0f).uv(0f, 0f)
            .color(1f, 1f, 1f, transparency).endVertex()
        builder.vertex(pose, x.toFloat(), (y + height).toFloat(), 0f).uv(0f, 1f)
            .color(1f, 1f, 1f, transparency).endVertex()
        builder.vertex(pose, (x + width).toFloat(), (y + height).toFloat(), 0f).uv(1f, 1f)
            .color(1f, 1f, 1f, transparency).endVertex()
        builder.vertex(pose, (x + width).toFloat(), y.toFloat(), 0f).uv(1f, 0f)
            .color(1f, 1f, 1f, transparency).endVertex()
        tesselator.end()
    }
}