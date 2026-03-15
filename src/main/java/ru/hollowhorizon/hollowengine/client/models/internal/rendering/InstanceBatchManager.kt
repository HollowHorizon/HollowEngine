package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.models.internal.INSTANCED_SHADER
import ru.hollowhorizon.hollowengine.client.models.internal.drawWithShader
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.shouldOverrideShaders

data class SubmittedInstance(
    val modelView: Matrix4f,
    val normal: Matrix3f,
    val overlay: Int,
    val light: Int,
    val sortKey: Float,
)

object InstanceBatchManager {
    private val batches = LinkedHashMap<PipelineRenderer, MutableList<SubmittedInstance>>()

    fun canBatch(): Boolean = !shouldOverrideShaders()

    fun submit(renderer: PipelineRenderer, instance: SubmittedInstance) {
        batches.getOrPut(renderer) { ArrayList() }.add(instance)
    }

    fun flush() {
        if (batches.isEmpty()) return
        if (!canBatch()) {
            clear()
            return
        }

        val activeTexture = GlStateManager._getActiveTexture()
        val currentVao = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
        val currentElementArrayBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)

        RenderSystem.activeTexture(GL33.GL_TEXTURE2)
        val texture2 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding
        RenderSystem.bindTexture(HollowModelManager.lightTexture.id)

        RenderSystem.activeTexture(GL33.GL_TEXTURE1)
        val texture1 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding
        Minecraft.getInstance().gameRenderer.overlayTexture().setupOverlayColor()
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(1))
        Minecraft.getInstance().gameRenderer.overlayTexture().teardownOverlayColor()

        RenderSystem.activeTexture(GL33.GL_TEXTURE0)
        val texture0 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding

        drawWithShader(INSTANCED_SHADER) {
            for ((renderer, instances) in batches.entries.asSequence().filter { !it.key.isTranslucent }) {
                renderer.renderInstanced(instances, INSTANCED_SHADER)
            }
            for ((renderer, instances) in batches.entries
                .asSequence()
                .filter { it.key.isTranslucent }
                .sortedByDescending { (_, instances) -> instances.maxOfOrNull(SubmittedInstance::sortKey) ?: Float.NEGATIVE_INFINITY }) {
                renderer.renderInstanced(instances, INSTANCED_SHADER)
            }
        }

        RenderSystem.activeTexture(GL33.GL_TEXTURE2)
        RenderSystem.bindTexture(texture2)
        RenderSystem.activeTexture(GL33.GL_TEXTURE1)
        RenderSystem.bindTexture(texture1)
        RenderSystem.activeTexture(GL33.GL_TEXTURE0)
        RenderSystem.bindTexture(texture0)
        RenderSystem.activeTexture(activeTexture)

        //? if > 1.20.1 {
        /*RenderSystem.glBindVertexArray(currentVao)
        RenderSystem.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer)
        *///?} else {
        RenderSystem.glBindVertexArray { currentVao }
        RenderSystem.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER) { currentElementArrayBuffer }
        //?}
        GlStateManager._glUseProgram(0)

        clear()
    }

    fun clear() {
        batches.clear()
    }
}
