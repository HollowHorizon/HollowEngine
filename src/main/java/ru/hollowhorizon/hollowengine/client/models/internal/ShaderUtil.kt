package ru.hollowhorizon.hollowengine.client.models.internal

//? if >= 1.21 {
/*import net.minecraft.client.Minecraft
*///?} else {
//?}

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.Util
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.TextureManager
import org.joml.Matrix4f
import org.lwjgl.opengl.GL13
import ru.hollowhorizon.hollowengine.client.utils.shouldOverrideShaders
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
import ru.hollowhorizon.hollowengine.mixins.client.ShaderInstanceAccessor
import java.util.function.Function


inline fun drawWithShader(
    body: () -> Unit,
) {
    val state = RenderType.entityTranslucent(TextureManager.INTENTIONAL_MISSING_TEXTURE)
    val shader = SHADER
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
        RenderSystem.glUniform1i(index, texture)
    }

    body()

    shader.clear()
    state.clearRenderState()
}

val batchingRenderType: Function<Material, RenderType> = Util.memoize<Material, RenderType> { material: Material ->
    val compositeState =
        RenderType.CompositeState.builder().setShaderState(RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityCutoutShader))
            .setTextureState(RenderStateShard.TextureStateShard(material.texture, false, false))
            .setTransparencyState(when(material.blend) {
                Material.Blend.BLEND -> RenderStateShard.TRANSLUCENT_TRANSPARENCY
                Material.Blend.OPAQUE -> RenderStateShard.NO_TRANSPARENCY
            })
            .setCullState(if(material.doubleSided) RenderStateShard.NO_CULL else RenderStateShard.CULL)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .createCompositeState(true)
    RenderType.create(
        "hollowengine:entity_cutout",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.TRIANGLES,
        4096,
        true,
        false,
        compositeState
    )
}

val SHADER
    get() =
        if (shouldOverrideShaders()) GameRenderer.getRendertypeEntityCutoutShader()!!
        else ModShaders.GLTF_ENTITY // Ванильный шейдер не поддерживает матрицу нормалей

const val COLOR_MAP_INDEX = GL13.GL_TEXTURE0
const val NORMAL_MAP_INDEX = GL13.GL_TEXTURE1
const val SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3