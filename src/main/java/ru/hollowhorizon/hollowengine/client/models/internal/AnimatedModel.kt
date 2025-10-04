package ru.hollowhorizon.hollowengine.client.models.internal


import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.ItemInHandRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation
import ru.hollowhorizon.hollowengine.client.models.internal.controller.Controller
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext


typealias NodeRenderer = (LivingEntity, PoseStack, Node, MultiBufferSource, Int) -> Unit

class ModelData(
    val leftHand: ItemStack?,
    val rightHand: ItemStack?,
    val itemInHandRenderer: ItemInHandRenderer?,
    val entity: LivingEntity?,
)

class AnimatedModel(val model: Model, val animations: Map<String, Animation> = mapOf()) {

    val nodes = model.walkNodes()
    var visuals: NodeRenderer = { _, _, _, _, _ -> }

    fun update(controller: Controller, query: MolangContext, time: Float) {
        try {
            nodes.forEach {
                it.transform.set(it.baseTransform)
                controller.update(it, query, time)
            }
        } catch (e: Exception) {
            HollowCore.LOGGER.error("Error while updating animations!", e)
            controller.layers.clear()
        }
    }

    fun render(
        stack: PoseStack,
        modelData: ModelData,
        consumer: (ResourceLocation) -> Int,
        source: MultiBufferSource,
        light: Int,
        overlay: Int,
    ) {

        NODE_GLOBAL_TRANSFORMATION_LOOKUP_CACHE.clear()

        model.scenes.forEach { scene ->
            scene.nodes.forEach { node -> node.renderDecorations(stack, visuals, modelData, source, light) }
        }

        val activeTexture = GlStateManager._getActiveTexture()

        //Получение текущих VAO и IBO
        val currentVAO = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
        val currentElementArrayBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)

        transformSkinning()

        GL33.glVertexAttrib4f(1, 1.0F, 1.0F, 1.0F, 1.0F) // Цвет
        GL33.glVertexAttribI2i(
            3,
            overlay and '\uffff'.code,
            overlay shr 16 and '\uffff'.code
        ) // Оверлей при ударе
        GL33.glVertexAttribI2i(
            4,
            light and '\uffff'.code,
            light shr 16 and '\uffff'.code
        ) // Освещение

        GlStateManager._activeTexture(GL33.GL_TEXTURE2)
        val texture2 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding
        GlStateManager._bindTexture(HollowModelManager.lightTexture.id)
        GlStateManager._activeTexture(GL33.GL_TEXTURE1)
        val texture1 = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding
        Minecraft.getInstance().gameRenderer.overlayTexture().setupOverlayColor()
        GlStateManager._bindTexture(RenderSystem.getShaderTexture(1))
        Minecraft.getInstance().gameRenderer.overlayTexture().teardownOverlayColor()
        GlStateManager._activeTexture(GL33.GL_TEXTURE0)

        val texture = GlStateManager.TEXTURES[GlStateManager.activeTexture].binding

        drawWithShader(SHADER) {
            model.scenes.forEach {
                it.render(stack, visuals, modelData, consumer, light)
            }
        }

        GlStateManager._activeTexture(GL33.GL_TEXTURE2)

        GlStateManager._bindTexture(texture2)
        GlStateManager._activeTexture(GL33.GL_TEXTURE1)
        GlStateManager._bindTexture(texture1)
        GlStateManager._activeTexture(GL33.GL_TEXTURE0)
        GlStateManager._bindTexture(texture)
        GlStateManager._activeTexture(activeTexture)

        GL33.glBindVertexArray(currentVAO)
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer)
        GlStateManager._glUseProgram(0)
    }

    private fun transformSkinning() {
        GL33.glUseProgram(HollowModelManager.glProgramSkinning)
        GL33.glEnable(GL33.GL_RASTERIZER_DISCARD)
        model.scenes.forEach { it.transformSkinning() }
        GL33.glBindBuffer(GL33.GL_TEXTURE_BUFFER, 0)
        GL33.glDisable(GL33.GL_RASTERIZER_DISCARD)
    }

    fun destroy() {
        model.walkNodes().mapNotNull { it.mesh }.flatMap { it.primitives }.forEach(Primitive::destroy)
    }

    companion object {
        val SHADER
            get() =
                if (shouldOverrideShaders()) GameRenderer.getRendertypeEntityCutoutShader()!!
                else ModShaders.GLTF_ENTITY // Ванильный шейдер не поддерживает матрицу нормалей
    }
}