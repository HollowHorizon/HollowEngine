package ru.hollowhorizon.hollowengine.client.gui.modificators

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.util.Mth
import org.joml.Quaternionf
import ru.hollowhorizon.hc.client.utils.rl


private val Float.radians: Float
    get() = this * Mth.DEG_TO_RAD

private val TEXTURE_FACES: Array<UVRange> = arrayOf(
    UVRange(0.25f, 0.6666667f, 0.5f, 1f),  // bottom
    UVRange(0.25f, 0.33333334f, 0.5f, 0.6666667f),  // north
    UVRange(0.75f, 0.33333334f, 1f, 0.6666667f),  // south
    UVRange(0.25f, 0f, 0.5f, 0.33333334f),  // top
    UVRange(0.5f, 0.33333334f, 0.75f, 0.6666667f),  // east
    UVRange(0f, 0.33333334f, 0.25f, 0.6666667f) // west
)

private class UVRange(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

object SkyBoxRenderer {
    val TEXTURE = "hollowengine:textures/skybox.png".rl

    fun render(stack: PoseStack) {
        RenderSystem.depthMask(false)
        RenderSystem.enableBlend()

        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        RenderSystem.defaultBlendFunc()


        stack.pushPose()
        this.renderSkybox(stack)
        stack.popPose()

        RenderSystem.depthMask(true)
        RenderSystem.disableBlend()
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f)
    }

    fun renderSkybox(
        matrices: PoseStack,
    ) {
        val tessellator = Tesselator.getInstance()
        val bufferBuilder: BufferBuilder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        RenderSystem.setShaderTexture(0, TEXTURE)
        for (face in 0..5) {
            val tex: UVRange = TEXTURE_FACES[face]

            // 0 = bottom
            // 1 = north
            // 2 = south
            // 3 = top
            // 4 = east
            // 5 = west
            matrices.pushPose()

            when (face) {
                1 -> matrices.mulPose(Quaternionf().rotateX(90.0f.radians))
                2 -> {
                    matrices.mulPose(Quaternionf().rotateX((-90.0f).radians))
                    matrices.mulPose(Quaternionf().rotateY(180f.radians))
                }

                3 -> matrices.mulPose(Quaternionf().rotateX(180f.radians))
                4 -> {
                    matrices.mulPose(Quaternionf().rotateZ(90f.radians))
                    matrices.mulPose(Quaternionf().rotateY((-90f).radians))
                }

                5 -> {
                    matrices.mulPose(Quaternionf().rotateZ((-90.0f).radians))
                    matrices.mulPose(Quaternionf().rotateY(90.0f.radians))
                }
            }

            val matrix4f = matrices.last().pose()
            bufferBuilder.addVertex(matrix4f, -100.0f, -100.0f, -100.0f).setUv(tex.minX, tex.minY)
            bufferBuilder.addVertex(matrix4f, -100.0f, -100.0f, 100.0f).setUv(tex.minX, tex.maxY)
            bufferBuilder.addVertex(matrix4f, 100.0f, -100.0f, 100.0f).setUv(tex.maxX, tex.maxY)
            bufferBuilder.addVertex(matrix4f, 100.0f, -100.0f, -100.0f).setUv(tex.maxX, tex.minY)
            matrices.popPose()
        }
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())
    }
}