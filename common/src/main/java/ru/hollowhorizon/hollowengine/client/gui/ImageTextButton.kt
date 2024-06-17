package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.Blaze3D
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.ImageButton
import net.minecraft.client.gui.components.WidgetSprites
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import ru.hollowhorizon.hc.client.utils.math.Interpolation

class ImageTextButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    sprites: WidgetSprites,
    private val text: String,
    onPress: OnPress,
) : ImageButton(x, y, width, height, sprites, onPress) {
    private var animationTicks = Blaze3D.getTime()
    private var wasHoveredOrFocused = false
    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        if (wasHoveredOrFocused != isHoveredOrFocused) animationTicks = Blaze3D.getTime()

        val backSprite = sprites[true, false]
        val frontSprite = sprites[true, true]

        var transparency = Interpolation.SINE_OUT(((Blaze3D.getTime() - animationTicks) * 2).toFloat().coerceAtMost(1f))
        if (!isHoveredOrFocused) transparency = 1f - transparency
        blit(graphics.pose(), backSprite, x, y, width, height, 1f-transparency)
        blit(graphics.pose(), frontSprite, x, y, width, height, transparency)

        graphics.drawString(
            Minecraft.getInstance().font,
            text,
            x + width / 2 - Minecraft.getInstance().font.width(text) / 2,
            y + height / 2 - 4,
            0xFFFFFF
        )

        wasHoveredOrFocused = isHoveredOrFocused
    }

    fun blit(stack: PoseStack, sprite: ResourceLocation, x: Int, y: Int, width: Int, height: Int, transparency: Float) {
        RenderSystem.setShaderTexture(0, sprite)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader)
        val pose: Matrix4f = stack.last().pose()
        val tesselator = Tesselator.getInstance()
        val builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)
        builder.addVertex(pose, x.toFloat(), y.toFloat(), 0f).setUv(0f, 0f)
            .setColor(1f, 1f, 1f, transparency)
        builder.addVertex(pose, x.toFloat(), (y + height).toFloat(), 0f).setUv(0f, 1f)
            .setColor(1f, 1f, 1f, transparency)
        builder.addVertex(pose, (x + width).toFloat(), (y + height).toFloat(), 0f).setUv(1f, 1f)
            .setColor(1f, 1f, 1f, transparency)
        builder.addVertex(pose, (x + width).toFloat(), y.toFloat(), 0f).setUv(1f, 0f)
            .setColor(1f, 1f, 1f, transparency)
        BufferUploader.drawWithShader(builder.buildOrThrow())
    }
}