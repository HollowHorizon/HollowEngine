package ru.hollowhorizon.hollowengine.client.gui.overlay

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.resources.ResourceLocation
//? if > 1.20.1 {
/*import ru.hollowhorizon.hollowengine.client.utils.color
import ru.hollowhorizon.hollowengine.client.utils.endVertex
import ru.hollowhorizon.hollowengine.client.utils.uv
import ru.hollowhorizon.hollowengine.client.utils.vertex
*///?}

@JvmOverloads
fun GuiGraphics.blitColor(
    location: ResourceLocation,
    x1: Int,
    y1: Int,
    width: Int,
    height: Int,
    minU: Float = 0f,
    maxU: Float = 1f,
    minV: Float = 0f,
    maxV: Float = 1f,
) {
    RenderSystem.setShaderTexture(0, location)
    RenderSystem.setShader { GameRenderer.getPositionTexColorShader() }
    val (r, g, b, a) = RenderSystem.getShaderColor()
    val matrix4f = pose().last().pose()
    //? if > 1.20.1 {
    /*val bufferBuilder: BufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
    *///?} else {
    val bufferBuilder: BufferBuilder = Tesselator.getInstance().builder
    bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
    //?}
    val y2 = y1 + height
    val x2 = x1 + width
    bufferBuilder.vertex(matrix4f, x1.toFloat(), y1.toFloat(), 0f).color(r, g, b, a).uv(minU, minV).endVertex()
    bufferBuilder.vertex(matrix4f, x1.toFloat(), y2.toFloat(), 0f).color(r, g, b, a).uv(minU, maxV).endVertex()
    bufferBuilder.vertex(matrix4f, x2.toFloat(), y2.toFloat(), 0f).color(r, g, b, a).uv(maxU, maxV).endVertex()
    bufferBuilder.vertex(matrix4f, x2.toFloat(), y1.toFloat(), 0f).color(r, g, b, a).uv(maxU, minV).endVertex()
    //? if > 1.20.1 {
    /*BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())
    *///?} else {
    BufferUploader.drawWithShader(bufferBuilder.end())
    //?}
}