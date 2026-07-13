package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.HollowEngine
import java.nio.FloatBuffer

internal class UiAnalyticRectRenderer : UiSdfRenderer(
    name = "analytic rect",
    vertexShaderPath = VertexShaderPath,
    fragmentShaderPath = FragmentShaderPath,
    samplerNames = arrayOf("RecordBuffer", "PaintBuffer", "StopBuffer"),
) {
    private val recordBuffer = UiStreamingTextureBuffer(GL30.GL_RGBA32F)
    private val paintBuffer = UiStreamingTextureBuffer(GL30.GL_RGBA32F)
    private val stopBuffer = UiStreamingTextureBuffer(GL30.GL_RGBA32F)
    private var vertexData: FloatBuffer? = null
    private var recordData: FloatBuffer? = null
    private var paintData: FloatBuffer? = null
    private var stopData: FloatBuffer? = null

    override val vertexStrideFloats get() = UiAnalyticRectBatch.VertexStride

    fun draw(batch: UiAnalyticRectBatch) {
        if (batch.isEmpty || !isAvailable) return
        val previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        val previousTextureBuffers = captureTextureBufferBindings()
        try {
            upload(batch)
            withCullStatePreserved {
                RenderSystem.disableCull()
                configureUiBlend()
                bindProgram()
                uploadMatrices()
                bindVertexArray()
                bindTextureBuffers(arrayOf(recordBuffer, paintBuffer, stopBuffer))
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, batch.vertexCount)
            }
        } finally {
            restoreTextureBufferBindings(previousTextureBuffers, previousActiveTexture)
            GL30.glBindVertexArray(previousVertexArray)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer)
            GL20.glUseProgram(previousProgram)
        }
    }

    override fun onProgramReady() {
        HollowEngine.LOGGER.info("Initialized UI analytic rectangle renderer (GL 3.3 buffer textures)")
    }

    override fun onClose() {
        recordBuffer.close()
        paintBuffer.close()
        stopBuffer.close()
        vertexData = vertexData.releaseUiBuffer()
        recordData = recordData.releaseUiBuffer()
        paintData = paintData.releaseUiBuffer()
        stopData = stopData.releaseUiBuffer()
    }

    private fun upload(batch: UiAnalyticRectBatch) {
        vertexData = vertexData.ensureUiCapacity(batch.vertexFloatCount)
        recordData = recordData.ensureUiCapacity(batch.recordFloatCount)
        paintData = paintData.ensureUiCapacity(batch.paintFloatCount)
        stopData = stopData.ensureUiCapacity(batch.stopFloatCount)
        val vertices = vertexData.prepareUiBuffer(batch.vertexFloatCount)
        val records = recordData.prepareUiBuffer(batch.recordFloatCount)
        val paints = paintData.prepareUiBuffer(batch.paintFloatCount)
        val stops = stopData.prepareUiBuffer(batch.stopFloatCount)
        batch.writeVertices(vertices)
        batch.writeRecords(records)
        batch.writePaints(paints)
        batch.writeStops(stops)
        vertices.flip()
        records.flip()
        paints.flip()
        stops.flip()
        vertexBuffer.upload(vertices)
        recordBuffer.upload(records)
        paintBuffer.upload(paints)
        stopBuffer.upload(stops)
    }

    private companion object {
        val VertexShaderPath = ResourceLocation.fromNamespaceAndPath(
            HollowEngine.MODID,
            "shaders/ui/rect_sdf.vsh",
        )
        val FragmentShaderPath = ResourceLocation.fromNamespaceAndPath(
            HollowEngine.MODID,
            "shaders/ui/rect_sdf.fsh",
        )
    }
}
