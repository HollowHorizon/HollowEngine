package ru.hollowhorizon.hollowengine.client.models.internal

//? if >= 1.21 {

/*import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
*///?} else {
import org.joml.Matrix4f
//?}

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.client.renderer.texture.TextureManager
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.mixins.client.ShaderInstanceAccessor


inline fun drawWithShader(
    shader: ShaderInstance,
    body: () -> Unit,
) {
    val state = RenderType.entityCutout(TextureManager.INTENTIONAL_MISSING_TEXTURE)
    val accessor = shader as ShaderInstanceAccessor

    state.setupRenderState()
    //? if >= 1.21 {
    /*shader.setDefaultUniforms(
        VertexFormat.Mode.TRIANGLES,
        RenderSystem.getModelViewMatrix(),
        RenderSystem.getProjectionMatrix(),
        Minecraft.getInstance().window
    )
    *///?} else {
    shader.PROJECTION_MATRIX?.set(RenderSystem.getProjectionMatrix())
    shader.MODEL_VIEW_MATRIX?.set(RenderSystem.getModelViewMatrix())
    shader.INVERSE_VIEW_ROTATION_MATRIX?.set(RenderSystem.getInverseViewRotationMatrix())
    shader.FOG_START?.set(RenderSystem.getShaderFogStart())
    shader.FOG_END?.set(RenderSystem.getShaderFogEnd())
    shader.FOG_COLOR?.set(RenderSystem.getShaderFogColor())
    shader.FOG_SHAPE?.set(RenderSystem.getShaderFogShape().index)
    shader.COLOR_MODULATOR?.set(1.0F, 1.0F, 1.0F, 1.0F)
    shader.GAME_TIME?.set(RenderSystem.getShaderGameTime())
    RenderSystem.setupShaderLights(shader)

    shader.TEXTURE_MATRIX?.set(Matrix4f(RenderSystem.getTextureMatrix()).apply { transpose() })
    //?}
    shader.apply()

    accessor.samplerLocations().forEachIndexed { texture, index ->
        GL33.glUniform1i(index, texture)
    }


    body()

    shader.clear()
    state.clearRenderState()
}

const val COLOR_MAP_INDEX = GL13.GL_TEXTURE0
const val NORMAL_MAP_INDEX = GL13.GL_TEXTURE1
const val SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3