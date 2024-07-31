/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.screen.overlays

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Vector3f
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.core.Direction
import net.minecraft.util.Mth
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.common.capabilities.StructuresCapability

object BoxRenderer {
    var START = Vec3(0.0, 0.0, 0.0)
        set(value) {
            START_OLD = START
            field = value
            animationTicks = TickHandler.currentTicks.toFloat()
        }
    private var START_OLD = Vec3(0.0, 0.0, 0.0)
    var END = Vec3(0.0, 0.0, 0.0)
        set(value) {
            END_OLD = END
            field = value
            animationTicks = TickHandler.currentTicks.toFloat()
        }
    private var END_OLD = Vec3(0.0, 0.0, 0.0)
    var animationTicks = 0f
    var mode = 0
    const val CULL_BACK: Int = 0
    const val CULL_FRONT: Int = 1
    const val CULL_NONE: Int = 2

    fun draw(stack: PoseStack) {
        val time =
            ((TickHandler.currentTicks - animationTicks + Minecraft.getInstance().partialTick)).coerceAtMost(20f) / 20f
        if (time == 1f) {
            END_OLD = END
            START_OLD = START
        }

        val END = END_OLD.lerp(END, Interpolation.CUBIC_OUT(time).toDouble())
        val START = START_OLD.lerp(START, Interpolation.CUBIC_OUT(time).toDouble())
        drawBoxFill(stack, AABB(START, END), intArrayOf(59, 118, 212, 128))
        drawBoxOutline(stack, AABB(START, END), intArrayOf(255, 255, 255, 220), 5f)

        drawStructures(stack)
    }

    fun drawStructures(stack: PoseStack) {
        val level = Minecraft.getInstance().level ?: return
        val structures = level[StructuresCapability::class]
        structures.structures.values.filter { it.center.distSqr(Minecraft.getInstance().player!!.blockPosition()) < 250 * 250 }
            .forEach {
            SelectingOverlay.draw(stack, it.toAABB(), 0f)
                //drawBoxFill(stack, it.toAABB(), intArrayOf(59, 118, 212, 128))
                //drawBoxOutline(stack, it.toAABB(), intArrayOf(255, 255, 255, 220), 5f)
            }
    }

    fun drawBoxOutline(
        ms: PoseStack,
        box: AABB,
        color: IntArray,
        lineWidth: Float,
    ) {
        ms.pushPose()
        val camera: Camera = Minecraft.getInstance().gameRenderer.mainCamera
        ms.translate(box.minX - camera.position.x, box.minY - camera.position.y, box.minZ - camera.position.z)
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.depthMask(false)
        RenderSystem.disableCull()
        val tessellator = Tesselator.getInstance()
        val buffer: BufferBuilder = tessellator.builder
        RenderSystem.disableDepthTest()
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader)
        RenderSystem.lineWidth(lineWidth)
        buffer.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL)
        vertexBoxLines(
            ms,
            buffer,
            moveToZero(box),
            color
        )
        tessellator.end()
        RenderSystem.enableDepthTest()
        RenderSystem.enableCull()
        RenderSystem.disableBlend()
        RenderSystem.depthMask(true)
        ms.popPose()
    }

    fun drawBoxFill(
        ms: PoseStack,
        box: AABB,
        cols: IntArray,
        vararg excludeDirs: Direction?,
    ) {
        ms.pushPose()
        val camera: Camera = Minecraft.getInstance().gameRenderer.mainCamera
        ms.translate(box.minX - camera.position.x, box.minY - camera.position.y, box.minZ - camera.position.z)
        RenderSystem.enableBlend()
        RenderSystem.depthMask(false)

        RenderSystem.disableDepthTest()

        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        )
        //        RenderLayer.MultiPhaseParameters.Builder builder = RenderLayer.MultiPhaseParameters.builder();
//        builder.depthTest(RenderPhase.LEQUAL_DEPTH_TEST);
        val tessellator = Tesselator.getInstance()
        val buffer: BufferBuilder = tessellator.builder
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        val bleh = arrayOfNulls<Direction>(1)

        vertexBoxQuads(
            ms,
            buffer,
            moveToZero(box),
            cols
        )
        tessellator.end()
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.disableBlend()
        ms.popPose()
    }

    fun getMinVec(box: AABB): Vec3 {
        return Vec3(box.minX, box.minY, box.minZ)
    }

    fun moveToZero(box: AABB): AABB {
        return box.move(getMinVec(box).negate())
    }

    fun vertexBoxQuads(
        matrices: PoseStack,
        vertexConsumer: VertexConsumer,
        box: AABB,
        quadColor: IntArray,
        vararg excludeDirs: Direction,
    ) {
        var x1 = box.minX.toFloat()
        var y1 = box.minY.toFloat()
        var z1 = box.minZ.toFloat()
        var x2 = box.maxX.toFloat()
        var y2 = box.maxY.toFloat()
        var z2 = box.maxZ.toFloat()
        val cullMode: Int = CULL_NONE //if (excludeDirs.isEmpty()) CULL_BACK else CULL_NONE

        if (Direction.DOWN !in excludeDirs) {
            y1 -= 0.01f
            vertexQuad(matrices, vertexConsumer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, cullMode, quadColor)
        }

        if (Direction.WEST !in excludeDirs) {
            x1 -= 0.01f
            vertexQuad(matrices, vertexConsumer, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, cullMode, quadColor)
        }

        if (Direction.EAST !in excludeDirs) {
            x2 += 0.01f
            vertexQuad(matrices, vertexConsumer, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, cullMode, quadColor)
        }

        if (Direction.NORTH !in excludeDirs) {
            z1 -= 0.01f
            vertexQuad(matrices, vertexConsumer, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, cullMode, quadColor)
        }

        if (Direction.SOUTH !in excludeDirs) {
            z2 += 0.01f
            vertexQuad(matrices, vertexConsumer, x2, y1, z2, x2, y2, z2, x1, y2, z2, x1, y1, z2, cullMode, quadColor)
        }

        if (Direction.UP !in excludeDirs) {
            y2 += 0.01f
            vertexQuad(matrices, vertexConsumer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, cullMode, quadColor)
        }
    }

    fun vertexQuad(
        matrices: PoseStack,
        vertexConsumer: VertexConsumer,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        x4: Float,
        y4: Float,
        z4: Float,
        cullMode: Int,
        cols: IntArray,
    ) {
        if (cullMode != CULL_FRONT) {
            vertexConsumer.vertex(matrices.last().pose(), x1, y1, z1)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
            vertexConsumer.vertex(matrices.last().pose(), x2, y2, z2)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
            vertexConsumer.vertex(matrices.last().pose(), x3, y3, z3)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
            vertexConsumer.vertex(matrices.last().pose(), x4, y4, z4)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
        }

        if (cullMode != CULL_BACK) {
            vertexConsumer.vertex(matrices.last().pose(), x4, y4, z4)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
            vertexConsumer.vertex(matrices.last().pose(), x3, y3, z3)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
            vertexConsumer.vertex(matrices.last().pose(), x2, y2, z2)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
            vertexConsumer.vertex(matrices.last().pose(), x1, y1, z1)
                .color(cols[0], cols[1], cols[2], cols[3]).endVertex()
        }
    }

    fun vertexBoxLines(
        matrices: PoseStack,
        vertexConsumer: VertexConsumer,
        box: AABB,
        cols: IntArray,
        vararg excludeDirs: Direction?,
    ) {
        val x1 = box.minX.toFloat()
        val y1 = box.minY.toFloat()
        val z1 = box.minZ.toFloat()
        val x2 = box.maxX.toFloat()
        val y2 = box.maxY.toFloat()
        val z2 = box.maxZ.toFloat()
        val exDown: Boolean = Direction.DOWN in excludeDirs
        val exWest: Boolean = Direction.WEST in excludeDirs
        val exEast: Boolean = Direction.EAST in excludeDirs
        val exNorth: Boolean = Direction.NORTH in excludeDirs
        val exSouth: Boolean = Direction.SOUTH in excludeDirs
        val exUp: Boolean = Direction.UP in excludeDirs


        if (!exDown) {
            vertexLine(matrices, vertexConsumer, x1, y1, z1, x2, y1, z1, cols)
            vertexLine(matrices, vertexConsumer, x2, y1, z1, x2, y1, z2, cols)
            vertexLine(matrices, vertexConsumer, x2, y1, z2, x1, y1, z2, cols)
            vertexLine(matrices, vertexConsumer, x1, y1, z2, x1, y1, z1, cols)
        }

        if (!exWest) {
            if (exDown) vertexLine(matrices, vertexConsumer, x1, y1, z1, x1, y1, z2, cols)
            vertexLine(matrices, vertexConsumer, x1, y1, z2, x1, y2, z2, cols)
            vertexLine(matrices, vertexConsumer, x1, y1, z1, x1, y2, z1, cols)
            if (exUp) vertexLine(matrices, vertexConsumer, x1, y2, z1, x1, y2, z2, cols)
        }

        if (!exEast) {
            if (exDown) vertexLine(matrices, vertexConsumer, x2, y1, z1, x2, y1, z2, cols)
            vertexLine(matrices, vertexConsumer, x2, y1, z2, x2, y2, z2, cols)
            vertexLine(matrices, vertexConsumer, x2, y1, z1, x2, y2, z1, cols)
            if (exUp) vertexLine(matrices, vertexConsumer, x2, y2, z1, x2, y2, z2, cols)
        }

        if (!exNorth) {
            if (exDown) vertexLine(matrices, vertexConsumer, x1, y1, z1, x2, y1, z1, cols)
            if (exEast) vertexLine(matrices, vertexConsumer, x2, y1, z1, x2, y2, z1, cols)
            if (exWest) vertexLine(matrices, vertexConsumer, x1, y1, z1, x1, y2, z1, cols)
            if (exUp) vertexLine(matrices, vertexConsumer, x1, y2, z1, x2, y2, z1, cols)
        }

        if (!exSouth) {
            if (exDown) vertexLine(matrices, vertexConsumer, x1, y1, z2, x2, y1, z2, cols)
            if (exEast) vertexLine(matrices, vertexConsumer, x2, y1, z2, x2, y2, z2, cols)
            if (exWest) vertexLine(matrices, vertexConsumer, x1, y1, z2, x1, y2, z2, cols)
            if (exUp) vertexLine(matrices, vertexConsumer, x1, y2, z2, x2, y2, z2, cols)
        }

        if (!exUp) {
            vertexLine(matrices, vertexConsumer, x1, y2, z1, x2, y2, z1, cols)
            vertexLine(matrices, vertexConsumer, x2, y2, z1, x2, y2, z2, cols)
            vertexLine(matrices, vertexConsumer, x2, y2, z2, x1, y2, z2, cols)
            vertexLine(matrices, vertexConsumer, x1, y2, z2, x1, y2, z1, cols)
        }
    }

    fun vertexLine(
        matrices: PoseStack,
        vertexConsumer: VertexConsumer,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        cols: IntArray,
    ) {
        val model = matrices.last().pose()
        val normal = matrices.last().normal()

        val normalVec = getNormal(x1, y1, z1, x2, y2, z2)

        vertexConsumer.vertex(model, x1, y1, z1).color(cols[0], cols[1], cols[2], cols[3])
            .normal(normal, normalVec.x(), normalVec.y(), normalVec.z()).endVertex()
        vertexConsumer.vertex(model, x2, y2, z2).color(cols[0], cols[1], cols[2], cols[3])
            .normal(normal, normalVec.x(), normalVec.y(), normalVec.z()).endVertex()
    }

    fun getNormal(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Vector3f {
        val xNormal = x2 - x1
        val yNormal = y2 - y1
        val zNormal = z2 - z1
        val normalSqrt: Float = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal)

        return Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt)
    }
}

private fun Vec3.negate(): Vec3 {
    return Vec3(-x, -y, -z)
}
