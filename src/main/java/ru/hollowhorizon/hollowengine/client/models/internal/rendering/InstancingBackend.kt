package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ShaderInstance
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.models.internal.INSTANCED_SHADER
import ru.hollowhorizon.hollowengine.client.models.internal.SHADER
import ru.hollowhorizon.hollowengine.client.models.internal.drawWithShader
import ru.hollowhorizon.hollowengine.client.models.internal.opaqueShaderState
import ru.hollowhorizon.hollowengine.client.models.internal.translucentShaderState
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager

interface ModelInstancingBackend {
    fun canBatch(): Boolean = true

    fun flush(batches: Map<PipelineRenderer, List<SubmittedInstance>>)
}

object VanillaInstancingBackend : ModelInstancingBackend {
    override fun flush(batches: Map<PipelineRenderer, List<SubmittedInstance>>) {
        if (batches.isEmpty()) return

        withInstancingRenderState {
            drawWithShader(INSTANCED_SHADER, opaqueShaderState()) {
                renderOpaque(batches, INSTANCED_SHADER) { renderer, instances, shader ->
                    if (renderer.shouldUseInstancing(instances.size)) {
                        renderer.renderInstanced(instances, shader, InstancedShaderLayoutMode.FIXED)
                    } else {
                        fallbackToCapturedDraws(renderer, instances, shader)
                    }
                }
            }
            drawWithShader(INSTANCED_SHADER, translucentShaderState()) {
                renderTranslucent(batches, INSTANCED_SHADER) { renderer, instances, shader ->
                    if (renderer.shouldUseInstancing(instances.size)) {
                        renderer.renderInstanced(instances, shader, InstancedShaderLayoutMode.FIXED)
                    } else {
                        fallbackToCapturedDraws(renderer, instances, shader)
                    }
                }
            }
        }
    }
}

inline fun withInstancingRenderState(body: () -> Unit) {
    val activeTexture = GlStateManager._getActiveTexture()
    val currentVao = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
    val currentElementArrayBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)
    val shaderTexture0 = RenderSystem.getShaderTexture(0)
    val shaderTexture1 = RenderSystem.getShaderTexture(1)
    val shaderTexture2 = RenderSystem.getShaderTexture(2)

    RenderSystem.activeTexture(GL33.GL_TEXTURE2)
    val texture2 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding
    RenderSystem.bindTexture(HollowModelManager.lightTexture.id)
    RenderSystem.setShaderTexture(2, HollowModelManager.lightTexture.id)

    RenderSystem.activeTexture(GL33.GL_TEXTURE1)
    val texture1 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding
    Minecraft.getInstance().gameRenderer.overlayTexture().setupOverlayColor()
    RenderSystem.bindTexture(RenderSystem.getShaderTexture(1))
    RenderSystem.setShaderTexture(1, RenderSystem.getShaderTexture(1))
    Minecraft.getInstance().gameRenderer.overlayTexture().teardownOverlayColor()

    RenderSystem.activeTexture(GL33.GL_TEXTURE0)
    val texture0 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding

    try {
        body()
    } finally {
        RenderSystem.setShaderTexture(0, shaderTexture0)
        RenderSystem.setShaderTexture(1, shaderTexture1)
        RenderSystem.setShaderTexture(2, shaderTexture2)
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
    }
}

inline fun renderOpaque(
    batches: Map<PipelineRenderer, List<SubmittedInstance>>,
    shader: ShaderInstance,
    render: (PipelineRenderer, List<SubmittedInstance>, ShaderInstance) -> Unit,
) {
    for ((renderer, instances) in batches.entries.asSequence().filter { !it.key.isTranslucent }) {
        render(renderer, instances, shader)
    }
}

inline fun renderTranslucent(
    batches: Map<PipelineRenderer, List<SubmittedInstance>>,
    shader: ShaderInstance,
    render: (PipelineRenderer, List<SubmittedInstance>, ShaderInstance) -> Unit,
) {
    for ((renderer, instances) in batches.entries
        .asSequence()
        .filter { it.key.isTranslucent }
        .sortedByDescending { (_, instances) -> instances.maxOfOrNull(SubmittedInstance::sortKey) ?: Float.NEGATIVE_INFINITY }) {
        render(renderer, instances, shader)
    }
}

fun fallbackToCapturedDraws(
    renderer: PipelineRenderer,
    instances: List<SubmittedInstance>,
    shader: ShaderInstance = SHADER,
) {
    val drawInstances = if (renderer.isTranslucent) instances.sortedByDescending(SubmittedInstance::sortKey) else instances
    for (instance in drawInstances) {
        renderer.renderCapturedInstance(instance, shader)
    }
}

enum class InstancedShaderLayoutMode {
    FIXED,
    RUNTIME,
}
