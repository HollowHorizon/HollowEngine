package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.bridge.mixins.client.ShaderInstanceAccessor
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import ru.hollowhorizon.hollowengine.client.ui.text.UiGlyphAtlasPage
import ru.hollowhorizon.hollowengine.client.ui.text.UiGlyphSampling
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
import java.nio.FloatBuffer

/** Consecutive MSDF glyphs sharing an atlas are uploaded and drawn as one streaming GPU batch. */
internal class UiMsdfTextBatch : AutoCloseable {
    private val vertices = UiFloatArrayBuilder()
    private val vertexBuffer = UiStreamingGpuBuffer(GL15.GL_ARRAY_BUFFER)
    private var vertexData: FloatBuffer? = null
    private var state: State? = null
    private var vertexArray = 0
    private var vertexArrayProgram = 0

    val isEmpty: Boolean get() = vertices.size == 0

    /**
     * [u0]/[v0] address the quad's bottom-left corner and [u1]/[v1] its top-right one. [sdBias] and
     * the page's sampling mode ride on the batch state rather than on each vertex: they are constant
     * for a run, so a bold or bitmap run costs one extra flush at most.
     */
    fun appendQuad(
        page: UiGlyphAtlasPage,
        sdBias: Float,
        bottomRight: UiVec3,
        topRight: UiVec3,
        topLeft: UiVec3,
        bottomLeft: UiVec3,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        color: UiColor,
    ) {
        val nextState = State(
            texture = page.textureId,
            distanceRange = page.distanceRange,
            width = page.width,
            height = page.height,
            sampling = page.sampling,
            sdBias = sdBias,
        )
        if (state != null && state != nextState) flush()
        state = nextState
        appendVertex(bottomRight, u1, v0, color)
        appendVertex(topRight, u1, v1, color)
        appendVertex(topLeft, u0, v1, color)
        appendVertex(bottomRight, u1, v0, color)
        appendVertex(topLeft, u0, v1, color)
        appendVertex(bottomLeft, u0, v0, color)
    }

    fun flush() {
        if (isEmpty) return
        val current = checkNotNull(state)
        val shader = ModShaders.MSDF_TEXT
        if (shader == null) {
            clear()
            return
        }
        vertexData = vertexData.ensureUiCapacity(vertices.size)
        val data = vertexData.prepareUiBuffer(vertices.size)
        vertices.writeTo(data)
        data.flip()
        val previousVertexArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        try {
            vertexBuffer.upload(data)
            ensureVertexArray(shader.id)
            RenderSystem.setShader { shader }
            RenderSystem.setShaderTexture(0, current.texture)
            shader.safeGetUniform("DistanceRange")?.set(current.distanceRange)
            shader.safeGetUniform("Softness")?.set(MsdfSoftness)
            shader.safeGetUniform("AtlasSize")?.set(current.width, current.height)
            shader.safeGetUniform("GlyphMode")?.set(current.sampling.shaderMode.toFloat())
            shader.safeGetUniform("SdBias")?.set(current.sdBias)
            shader.setDefaultUniforms(
                VertexFormat.Mode.TRIANGLES,
                RenderSystem.getModelViewMatrix(),
                RenderSystem.getProjectionMatrix(),
                Minecraft.getInstance().window,
            )
            configureUiBlend()
            shader.apply()
            (shader as ShaderInstanceAccessor).samplerLocations().forEachIndexed { texture, location ->
                RenderSystem.glUniform1i(location, texture)
            }
            GL30.glBindVertexArray(vertexArray)
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertices.size / VertexStride)
            shader.clear()
        } finally {
            GL30.glBindVertexArray(previousVertexArray)
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer)
            GL20.glUseProgram(previousProgram)
            clear()
        }
    }

    override fun close() {
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray)
        vertexArray = 0
        vertexArrayProgram = 0
        vertexBuffer.close()
        vertexData = vertexData.releaseUiBuffer()
        clear()
    }

    private fun ensureVertexArray(program: Int) {
        if (vertexArray != 0 && vertexArrayProgram == program) return
        if (vertexArray != 0) GL30.glDeleteVertexArrays(vertexArray)
        vertexArray = GL30.glGenVertexArrays()
        vertexArrayProgram = program
        GL30.glBindVertexArray(vertexArray)
        vertexBuffer.bind()
        bindAttribute(program, "Position", 3, 0L)
        bindAttribute(program, "UV0", 2, 3L * Float.SIZE_BYTES)
        bindAttribute(program, "Color", 4, 5L * Float.SIZE_BYTES)
        GL30.glBindVertexArray(0)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0)
    }

    private fun bindAttribute(program: Int, name: String, size: Int, offset: Long) {
        val location = GL20.glGetAttribLocation(program, name)
        check(location >= 0) { "MSDF shader is missing the $name vertex attribute" }
        GL20.glVertexAttribPointer(location, size, GL11.GL_FLOAT, false, VertexStrideBytes, offset)
        GL20.glEnableVertexAttribArray(location)
    }

    private fun appendVertex(point: UiVec3, u: Float, v: Float, color: UiColor) {
        vertices.add(point.x, point.y, point.z, u, v, color.red, color.green, color.blue, color.alpha)
    }

    private fun clear() {
        vertices.clear()
        state = null
    }

    private data class State(
        val texture: Int,
        val distanceRange: Float,
        val width: Float,
        val height: Float,
        val sampling: UiGlyphSampling,
        val sdBias: Float,
    )

    private companion object {
        const val VertexStride = 9
        const val VertexStrideBytes = VertexStride * Float.SIZE_BYTES

        /** MSDF edge anti-aliasing softness (screen-space smoothing of the signed-distance edge). */
        const val MsdfSoftness = 0.15f
    }
}
