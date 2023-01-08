package ru.hollowhorizon.hollowstory.client.gui.widget.action

import com.mojang.blaze3d.matrix.MatrixStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.IRenderable
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.vector.Vector2f
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowstory.HollowStory
import kotlin.math.abs

class LineDrawer(val fromX: Int, val fromY: Int) {

    private var anim = 0

    fun render(stack: MatrixStack, toX: Int, toY: Int) {
        drawLinePretty(stack, fromX, fromY, toX, toY)
    }

    private fun drawLinePretty(stack: MatrixStack, x1: Int, y1: Int, x2: Int, y2: Int) {
        var amount: Int = (abs(x2 - x1) + abs(y2 - y1)) / 10
        if (amount > 50) amount = 50
        val deltaX: Float = (x2 - x1) / amount.toFloat()
        val deltaY: Float = (y2 - y1) / amount.toFloat()

        val points = mutableListOf<Vector2f>()
        for (i in 0..amount) {
            if (deltaX > 0) {
                val inter = easeInOutCubic(i / amount.toFloat())
                points.add(Vector2f(x1 + deltaX * i, y1 + deltaY * amount * inter))
            } else {
                val inter = easeInOutBack(i / amount.toFloat())
                points.add(Vector2f(x1 + deltaX * amount * inter, y1 + deltaY * i))
            }
        }

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.builder

        val animPos = anim.toFloat() / 20F

        Minecraft.getInstance().textureManager.bind(TEXTURE)

        GL11.glLineWidth(5F)
        buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_TEX)

        buffer.vertex(stack.last().pose(), x1.toFloat(), y1.toFloat(), 0.0f).uv(1F + animPos, 1F).endVertex()
        for (point in points) {
            buffer.vertex(stack.last().pose(), point.x, point.y, 0.0f).uv(animPos, 0F).endVertex()
            buffer.vertex(stack.last().pose(), point.x, point.y, 0.0f).uv(1F + animPos, 1F).endVertex()
        }
        buffer.vertex(stack.last().pose(), x2.toFloat(), y2.toFloat(), 0.0f).uv(animPos, 0F).endVertex()

        tessellator.end()
        GL11.glLineWidth(1F)
        anim++
    }

    fun easeInOutCubic(t: Float): Float {
        return if (t < 0.5f) {
            4 * t * t * t
        } else {
            1 - (-2 * t + 2) * (-2 * t + 2) * (-2 * t + 2) / 2
        }
    }

    fun easeInOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c2 = c1 * 1.525f

        return if (x < 0.5) {
            ((2 * x) * (2 * x) * ((c2 + 1) * 2 * x - c2)) / 2F
        } else {
            ((2 * x - 2) * (2 * x - 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2F
        }
    }

    companion object {
        val TEXTURE = ResourceLocation(HollowStory.MODID, "textures/gui/line.png")
    }
}