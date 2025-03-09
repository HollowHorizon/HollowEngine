package ru.hollowhorizon.hollowengine.client.gui.modificators

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
//? if <=1.19.2 {
/*import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
*///?} else {
import org.joml.Quaternionf
//?}
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.util.Mth
import ru.hollowhorizon.hc.common.utils.rl


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
        //? if >=1.21 {
        /*val bufferBuilder: BufferBuilder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        *///?} else {
        val bufferBuilder: BufferBuilder = tessellator.builder
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX)
        //?}
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

            //? if >=1.20.1 {
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
            //?} else {
            /*when (face) {
                1 -> matrices.mulPose(Quaternion(Vector3f.XP, 90.0f, true))
                2 -> {
                    matrices.mulPose(Quaternion(Vector3f.XP, -90.0f, true))
                    matrices.mulPose(Quaternion(Vector3f.YP, 180.0f, true))
                }

                3 -> matrices.mulPose(Quaternion(Vector3f.XP, 180.0f, true))
                4 -> {
                    matrices.mulPose(Quaternion(Vector3f.ZP, 90.0f, true))
                    matrices.mulPose(Quaternion(Vector3f.YP, -90.0f, true))
                }

                5 -> {
                    matrices.mulPose(Quaternion(Vector3f.ZP, -90.0f, true))
                    matrices.mulPose(Quaternion(Vector3f.YP, 90.0f, true))
                }
            }
            *///?}

            val matrix4f = matrices.last().pose()
            //? if >=1.21 {
            /*bufferBuilder.addVertex(matrix4f, -100.0f, -100.0f, -100.0f).setUv(tex.minX, tex.minY)
            bufferBuilder.addVertex(matrix4f, -100.0f, -100.0f, 100.0f).setUv(tex.minX, tex.maxY)
            bufferBuilder.addVertex(matrix4f, 100.0f, -100.0f, 100.0f).setUv(tex.maxX, tex.maxY)
            bufferBuilder.addVertex(matrix4f, 100.0f, -100.0f, -100.0f).setUv(tex.maxX, tex.minY)
            *///?} else {
            bufferBuilder.vertex(matrix4f, -100.0f, -100.0f, -100.0f).uv(tex.minX, tex.minY)
            bufferBuilder.vertex(matrix4f, -100.0f, -100.0f, 100.0f).uv(tex.minX, tex.maxY)
            bufferBuilder.vertex(matrix4f, 100.0f, -100.0f, 100.0f).uv(tex.maxX, tex.maxY)
            bufferBuilder.vertex(matrix4f, 100.0f, -100.0f, -100.0f).uv(tex.maxX, tex.minY)
            //?}
            matrices.popPose()
        }
        //? if >=1.21 {
        /*BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())
        *///?} else {
        BufferUploader.drawWithShader(bufferBuilder.end())
        //?}
    }
}