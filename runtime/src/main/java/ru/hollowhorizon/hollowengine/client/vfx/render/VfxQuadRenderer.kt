package ru.hollowhorizon.hollowengine.client.vfx.render

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ShaderInstance
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.models.internal.drawWithShader
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.translucentShaderState
import ru.hollowhorizon.hollowengine.client.models.internal.utils.VboWrapper
import ru.hollowhorizon.hollowengine.client.utils.shaderPackParticleShader
import ru.hollowhorizon.hollowengine.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.nio.FloatBuffer
import java.util.*

/**
 * Draws billboard particles, one instanced quad per particle, one draw call per batch.
 *
 * Without a shader pack the engine's own program draws everything, and alpha-blended, additive and
 * opaque particles share one draw through premultiplied alpha (see [VfxBatchBlend]).
 *
 * With a pack the pack's particle program is patched for instancing, the way model instancing does
 * it: a program the pack does not know would draw into nothing, because the pack renders into its
 * own buffers.
 */
object VfxQuadRenderer {
    private const val STRIDE = VfxQuadPacker.STRIDE
    private const val STRIDE_BYTES = STRIDE * Float.SIZE_BYTES

    private val packer = VfxQuadPacker()

    private val vaos = IdentityHashMap<ShaderInstance, Int>()
    private var quadBuffer: VboWrapper? = null
    private var indexBuffer: VboWrapper? = null
    private var instanceBuffer: VboWrapper? = null
    private var instanceCapacity = 0
    private var upload: FloatBuffer = BufferUtils.createFloatBuffer(STRIDE * 256)

    /** Frees the GL objects; the next draw builds them again. */
    fun invalidate() {
        vaos.values.forEach(GL33::glDeleteVertexArrays)
        vaos.clear()
        quadBuffer?.delete()
        indexBuffer?.delete()
        instanceBuffer?.delete()
        quadBuffer = null
        indexBuffer = null
        instanceBuffer = null
        instanceCapacity = 0
    }

    /** Draws everything in [draws] as seen from [view]. */
    fun render(draws: List<VfxQuadDraw>, view: VfxView) {
        if (draws.isEmpty()) return

        val shaderPack = shouldOverrideShaders()
        val total = packer.pack(draws, view, mixed = !shaderPack)
        if (total == 0) return

        when {
            !shaderPack -> drawOwn(total, view)
            else -> drawWithPack(total)
        }
    }

    private fun drawOwn(total: Int, view: VfxView) {
        val shader = ModShaders.VFX_PARTICLE ?: return
        withInstanceState(total) {
            RenderSystem.setShader { shader }
            shader.setDefaultUniforms(
                VertexFormat.Mode.TRIANGLES, view.modelView, view.projection, Minecraft.getInstance().window
            )
            GL33.glDepthFunc(GL33.GL_LEQUAL)

            packer.batches.forEach { batch ->
                shader.setSampler("Sampler0", textureOf(batch))
                shader.setSampler("Sampler2", HollowModelManager.lightTexture.id)
                shader.apply()
                applyState(batch.key, premultiplied = true)
                drawBatch(shader, batch)
            }
            shader.clear()
        }
    }

    private fun drawWithPack(total: Int) {
        val opaque = shaderPackParticleShader(translucent = false)
        val translucent = shaderPackParticleShader(translucent = true)
        if (opaque == null || translucent == null) {
            drawExpanded()
            return
        }

        withInstanceState(total) {
            packer.batches.forEach { batch ->
                val shader = if (batch.key.blend == VfxBatchBlend.OPAQUE) opaque else translucent
                drawWithShader(shader, translucentShaderState()) {
                    RenderSystem.activeTexture(GL33.GL_TEXTURE2)
                    RenderSystem.bindTexture(HollowModelManager.lightTexture.id)
                    RenderSystem.activeTexture(GL33.GL_TEXTURE0)
                    RenderSystem.bindTexture(textureOf(batch))
                    applyState(batch.key, premultiplied = false)
                    drawBatch(shader, batch)
                }
            }
        }
    }

    private inline fun withInstanceState(total: Int, body: () -> Unit) {
        val previousVao = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
        val previousBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)
        val previousTexture = GL33.glGetInteger(GL33.GL_ACTIVE_TEXTURE)

        ensureBuffers()
        uploadInstances(total)
        try {
            body()
        } finally {
            restoreState()
            GlStateManager._glUseProgram(0)
            RenderSystem.activeTexture(previousTexture)
            RenderSystem.glBindVertexArray(previousVao)
            RenderSystem.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, previousBuffer)
        }
    }

    private fun drawBatch(shader: ShaderInstance, batch: VfxQuadBatch) {
        RenderSystem.glBindVertexArray(vaoFor(shader))
        instanceBuffer?.bind()
        pointInstances(shader, batch.first.toLong() * STRIDE_BYTES)
        GL33.glDrawElementsInstanced(GL33.GL_TRIANGLES, 6, GL33.GL_UNSIGNED_INT, 0L, batch.count)
    }

    private fun drawExpanded() {
        RenderSystem.setShader(GameRenderer::getParticleShader)
        RenderSystem.setShaderTexture(2, HollowModelManager.lightTexture.id)

        packer.batches.forEach { batch ->
            RenderSystem.setShaderTexture(0, textureOf(batch))
            applyState(batch.key, premultiplied = false)

            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
            val data = packer.packed
            for (index in batch.first until batch.first + batch.count) {
                val at = index * STRIDE
                val light = data[at + 17].toInt() or (data[at + 18].toInt() shl 16)
                corner(builder, data, at, -0.5f, -0.5f, 0f, 1f, light)
                corner(builder, data, at, 0.5f, -0.5f, 1f, 1f, light)
                corner(builder, data, at, 0.5f, 0.5f, 1f, 0f, light)
                corner(builder, data, at, -0.5f, 0.5f, 0f, 0f, light)
            }
            builder.build()?.let(BufferUploader::drawWithShader)
        }

        restoreState()
    }

    private fun corner(
        builder: BufferBuilder,
        data: FloatArray,
        at: Int,
        along: Float,
        across: Float,
        u: Float,
        v: Float,
        light: Int,
    ) {
        builder.addVertex(
            data[at] + data[at + 3] * along + data[at + 6] * across,
            data[at + 1] + data[at + 4] * along + data[at + 7] * across,
            data[at + 2] + data[at + 5] * along + data[at + 8] * across,
        ).setUv(data[at + 13] + u * data[at + 15], data[at + 14] + v * data[at + 16])
            .setColor(data[at + 9], data[at + 10], data[at + 11], data[at + 12]).setLight(light)
    }

    private fun textureOf(batch: VfxQuadBatch): Int =
        Minecraft.getInstance().textureManager.getTexture(batch.key.texture.rl).id

    private fun applyState(key: VfxBatchKey, premultiplied: Boolean) {
        applyBlend(key.blend, premultiplied)
        if (key.cull) RenderSystem.enableCull() else RenderSystem.disableCull()
        if (key.depthTest) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
        RenderSystem.depthMask(key.depthWrite)
    }

    private fun restoreState() {
        RenderSystem.depthMask(true)
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.blendEquation(GL33.GL_FUNC_ADD)
        RenderSystem.defaultBlendFunc()
        RenderSystem.enableCull()
    }

    private fun applyBlend(blend: VfxBatchBlend, premultiplied: Boolean) {
        RenderSystem.blendEquation(GL33.GL_FUNC_ADD)
        when (blend) {
            VfxBatchBlend.OPAQUE -> RenderSystem.disableBlend()
            VfxBatchBlend.MIXED -> {
                RenderSystem.enableBlend()
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
            }

            VfxBatchBlend.BLEND -> {
                RenderSystem.enableBlend()
                RenderSystem.defaultBlendFunc()
            }

            VfxBatchBlend.ADDITIVE -> {
                RenderSystem.enableBlend()
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
            }

            VfxBatchBlend.MULTIPLY -> {
                RenderSystem.enableBlend()
                if (premultiplied) {
                    RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO)
                } else {
                    RenderSystem.blendFunc(
                        GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
                    )
                }
            }
        }
    }

    private fun uploadInstances(total: Int) {
        if (instanceCapacity < total || upload.capacity() < total * STRIDE) {
            val capacity = Integer.highestOneBit((total - 1).coerceAtLeast(255)) * 2
            upload = BufferUtils.createFloatBuffer(capacity * STRIDE)
            instanceCapacity = capacity
            instanceBuffer?.bind()
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, capacity.toLong() * STRIDE_BYTES, GL33.GL_STREAM_DRAW)
        }
        upload.clear()
        upload.put(packer.packed, 0, total * STRIDE)
        upload.flip()
        instanceBuffer?.bind()
        GL33.glBufferSubData(GL33.GL_ARRAY_BUFFER, 0L, upload)
    }

    private fun ensureBuffers() {
        if (quadBuffer != null) return

        quadBuffer = VboWrapper.createArrayBuffer().apply {
            val corners = BufferUtils.createFloatBuffer(4 * 5)
            corners.put(-0.5f).put(-0.5f).put(0f).put(0f).put(1f)
            corners.put(0.5f).put(-0.5f).put(0f).put(1f).put(1f)
            corners.put(0.5f).put(0.5f).put(0f).put(1f).put(0f)
            corners.put(-0.5f).put(0.5f).put(0f).put(0f).put(0f)
            corners.flip()
            uploadData(corners)
        }
        indexBuffer = VboWrapper.createElementBuffer().apply {
            val indices = BufferUtils.createIntBuffer(6)
            indices.put(0).put(1).put(2).put(0).put(2).put(3)
            indices.flip()
            uploadData(indices)
        }
        instanceBuffer = VboWrapper.createArrayBuffer()
        instanceCapacity = 0
    }

    private fun vaoFor(shader: ShaderInstance): Int = vaos.getOrPut(shader) {
        val vao = GL33.glGenVertexArrays()
        GL33.glBindVertexArray(vao)

        quadBuffer?.bind()
        bindFloats(shader, "Position", 3, 5 * Float.SIZE_BYTES, 0L, divisor = 0)
        bindFloats(shader, "UV0", 2, 5 * Float.SIZE_BYTES, 3L * Float.SIZE_BYTES, divisor = 0)

        instanceBuffer?.bind()
        pointInstances(shader, 0L)

        indexBuffer?.bind()
        vao
    }

    /** Points the per-particle attributes at [base] bytes into the instance buffer. */
    private fun pointInstances(shader: ShaderInstance, base: Long) {
        bindFloats(shader, "InstanceCenter", 3, STRIDE_BYTES, base)
        bindFloats(shader, "InstanceRight", 3, STRIDE_BYTES, base + 3L * Float.SIZE_BYTES)
        bindFloats(shader, "InstanceUp", 3, STRIDE_BYTES, base + 6L * Float.SIZE_BYTES)
        bindFloats(shader, "InstanceColor", 4, STRIDE_BYTES, base + 9L * Float.SIZE_BYTES)
        bindFloats(shader, "InstanceUv", 4, STRIDE_BYTES, base + 13L * Float.SIZE_BYTES)
        bindFloats(shader, "InstanceLight", 2, STRIDE_BYTES, base + 17L * Float.SIZE_BYTES)
        bindFloats(shader, "InstanceBlend", 1, STRIDE_BYTES, base + 19L * Float.SIZE_BYTES)
    }

    private fun bindFloats(
        shader: ShaderInstance,
        name: String,
        size: Int,
        stride: Int,
        offset: Long,
        divisor: Int = 1,
    ) {
        val location = GL33.glGetAttribLocation(shader.id, name)
        if (location == -1) return

        GL33.glVertexAttribPointer(location, size, GL33.GL_FLOAT, false, stride, offset)
        GL33.glEnableVertexAttribArray(location)
        if (divisor != 0) GL33.glVertexAttribDivisor(location, divisor)
    }
}
