package ru.hollowhorizon.hollowengine.client.particles

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.file.BedrockParticleFile

interface VertexConsumerProvider {
    fun provide(renderPass: ParticleEffect.RenderPass, block: (VertexConsumer) -> Unit)
}

object ParticleVertexConsumerProvider : VertexConsumerProvider {
    override fun provide(renderPass: ParticleEffect.RenderPass, block: (VertexConsumer) -> Unit) {
        val texture = renderPass.texture

        val prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        RenderSystem.setShaderTexture(0, texture.id)
        RenderSystem.setShaderTexture(2, HollowModelManager.lightTexture.id)
        val old = RenderSystem.getShader()
        RenderSystem.setShader(GameRenderer::getParticleShader)


        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
        when (renderPass.material) {
            BedrockParticleFile.Material.Add -> {
                RenderSystem.blendEquation(GL33.GL_FUNC_ADD)
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE)
            }

            BedrockParticleFile.Material.Cutout -> RenderSystem.disableBlend()
            BedrockParticleFile.Material.Blend -> RenderSystem.defaultBlendFunc()
        }

        if (!prevCull) GlStateManager._enableCull()
        Tesselator.getInstance().apply {
            val builder = begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
            block(builder)
            BufferUploader.drawWithShader(builder.buildOrThrow())
        }


        RenderSystem.setShader { old }

        if (!prevCull) GlStateManager._disableCull()
        RenderSystem.defaultBlendFunc()

        RenderType.PARTICLES_TARGET.clearRenderState()

    }
}