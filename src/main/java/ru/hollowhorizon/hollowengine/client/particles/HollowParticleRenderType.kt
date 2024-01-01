package ru.hollowhorizon.hollowengine.client.particles

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.texture.TextureManager
import ru.hollowhorizon.hollowengine.client.shaders.ModShaders

object HollowParticleRenderType: ParticleRenderType {
    var shader: ShaderInstance = ModShaders.PARTICLE
    var texture = TextureManager.INTENTIONAL_MISSING_TEXTURE

    override fun begin(builder: BufferBuilder, pTextureManager: TextureManager) {
        RenderSystem.enableDepthTest()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.setShader { shader }
        RenderSystem.setShaderTexture(0, texture)
        builder.begin(VertexFormat.Mode.QUADS, shader.vertexFormat)
    }

    override fun end(pTesselator: Tesselator) {
        pTesselator.end()
        RenderSystem.disableBlend()
        RenderSystem.defaultBlendFunc()
        RenderSystem.enableDepthTest()
    }
}