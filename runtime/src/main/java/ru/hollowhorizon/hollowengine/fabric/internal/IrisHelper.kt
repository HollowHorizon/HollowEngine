package ru.hollowhorizon.hollowengine.fabric.internal

import com.mojang.blaze3d.systems.RenderSystem
import net.irisshaders.iris.Iris
import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.pipeline.IrisRenderingPipeline
import net.irisshaders.iris.pipeline.ShaderRenderingPipeline
import net.irisshaders.iris.pipeline.WorldRenderingPipeline
import net.irisshaders.iris.shaderpack.loading.ProgramId
import net.irisshaders.iris.shaderpack.properties.ShaderProperties
import net.irisshaders.iris.uniforms.CapturedRenderingState
import org.joml.Matrix4f
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ModelInstancingBackend
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.PipelineRenderer
import ru.hollowhorizon.hollowengine.client.render.lighting.LightCullingSupport
import ru.hollowhorizon.hollowengine.client.render.lighting.detectLightCullingSupport
import ru.hollowhorizon.hollowengine.client.utils.InstancingEntityInfo
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.IrisRenderingPipelineAccessor
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.ProgramSourceAccessor
import ru.hollowhorizon.hollowengine.fabric.internal.rendering.IrisInstancingBackend
import ru.hollowhorizon.hollowengine.fabric.internal.rendering.IrisInstancingPrograms

object IrisHelper {
    @JvmStatic
    fun shouldOverrideShaders() =
        (Iris.getPipelineManager().pipelineNullable as? ShaderRenderingPipeline)?.shouldOverrideShaders() == true

    val hasIris = ModList.isLoaded("iris") || ModList.isLoaded("oculus")

    fun isShadowRendering() = hasIris && IrisApi.getInstance().isRenderingShadowPass

    fun isShaderPackInUse() = hasIris && IrisApi.getInstance().isShaderPackInUse

    fun currentPipeline(): WorldRenderingPipeline? = if (hasIris) Iris.getPipelineManager().pipelineNullable else null

    fun currentShaderProperties(): ShaderProperties? {
        val pipeline = currentPipeline() as? IrisRenderingPipeline ?: return null
        val accessor = pipeline as? IrisRenderingPipelineAccessor ?: return null
        val programSet = accessor.programSet
        val candidatePrograms = arrayOf(
            ProgramId.Entities,
            ProgramId.Terrain,
            ProgramId.Textured,
            ProgramId.Basic,
        )

        for (programId in candidatePrograms) {
            val source = programSet.get(programId).orElse(null) ?: continue
            val properties = (source as? ProgramSourceAccessor)?.shaderPropertiesValue ?: continue
            return properties
        }

        return null
    }

    fun currentLightCullingSupport(): LightCullingSupport {
        val shaderProperties = currentShaderProperties() ?: return LightCullingSupport(
            direct = false,
            tiled = false,
            clustered = false,
        )
        return detectLightCullingSupport(
            requiredFlags = shaderProperties.requiredFeatureFlags,
            optionalFlags = shaderProperties.optionalFeatureFlags,
        )
    }

    fun isClusteredLightingCompatible(): Boolean = currentLightCullingSupport().clustered

    fun currentGbufferModelViewMatrix(): Matrix4f = if (isShaderPackInUse()) {
        Matrix4f(CapturedRenderingState.INSTANCE.gbufferModelView)
    } else {
        Matrix4f(RenderSystem.getModelViewMatrix())
    }

    fun currentGbufferProjectionMatrix(fallback: Matrix4f): Matrix4f = if (isShaderPackInUse()) {
        Matrix4f(CapturedRenderingState.INSTANCE.gbufferProjection)
    } else {
        Matrix4f(fallback)
    }

    fun instancingBackend(): ModelInstancingBackend = IrisInstancingBackend

    fun capturedEntityInfo(): InstancingEntityInfo = InstancingEntityInfo(
        entity = CapturedRenderingState.INSTANCE.currentRenderedEntity,
        blockEntity = CapturedRenderingState.INSTANCE.currentRenderedBlockEntity,
        item = CapturedRenderingState.INSTANCE.currentRenderedItem
    )

    @JvmStatic
    fun invalidateInstancingPrograms() {
        IrisInstancingPrograms.invalidate()
        PipelineRenderer.invalidateRuntimeInstancedBindings()
    }
}
