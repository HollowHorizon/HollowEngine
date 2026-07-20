package ru.hollowhorizon.hollowengine.fabric.internal.rendering

import net.irisshaders.iris.gl.blending.AlphaTest
import net.irisshaders.iris.gl.blending.AlphaTests
import net.irisshaders.iris.gl.state.FogMode
import net.irisshaders.iris.pipeline.IrisRenderingPipeline
import net.irisshaders.iris.shaderpack.loading.ProgramId
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver
import net.irisshaders.iris.shaderpack.programs.ProgramSource
import net.irisshaders.iris.vertices.IrisVertexFormats
import net.minecraft.client.renderer.ShaderInstance
import ru.hollowhorizon.hollowengine.LOGGER
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.IrisRenderingPipelineAccessor
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.ProgramSourceAccessor
import java.util.*

private enum class IrisProgramVariant(
    val sourceProgram: ProgramId,
    val shadow: Boolean,
    val fallbackAlpha: AlphaTest,
) {
    OPAQUE(ProgramId.Entities, false, AlphaTests.OFF),
    BLEND(ProgramId.EntitiesTrans, false, AlphaTests.ONE_TENTH_ALPHA),
    SHADOW(ProgramId.Shadow, true, AlphaTests.ONE_TENTH_ALPHA),
}

object IrisInstancingPrograms {
    private var pipeline: IrisRenderingPipeline? = null
    private val programs = EnumMap<IrisProgramVariant, ShaderInstance>(IrisProgramVariant::class.java)
    private val failures = EnumSet.noneOf(IrisProgramVariant::class.java)
    private var shaderCounter = 0

    fun shaderFor(translucent: Boolean, shadow: Boolean): ShaderInstance? {
        val currentPipeline = IrisHelper.currentPipeline() as? IrisRenderingPipeline ?: return null
        ensurePipeline(currentPipeline)

        val variant = when {
            shadow -> IrisProgramVariant.SHADOW
            translucent -> IrisProgramVariant.BLEND
            else -> IrisProgramVariant.OPAQUE
        }

        programs[variant]?.let { return it }
        if (variant in failures) return null

        val created = createProgram(currentPipeline, variant)
        if (created == null) {
            failures += variant
        } else {
            programs[variant] = created
        }
        return created
    }

    fun invalidate() {
        programs.clear()
        failures.clear()
        pipeline = null
    }

    private fun ensurePipeline(currentPipeline: IrisRenderingPipeline) {
        if (pipeline === currentPipeline) return
        invalidate()
        pipeline = currentPipeline
    }

    private fun createProgram(pipeline: IrisRenderingPipeline, variant: IrisProgramVariant): ShaderInstance? {
        val accessor = pipeline as? IrisRenderingPipelineAccessor ?: return null
        val source = ProgramFallbackResolver(accessor.programSet).resolve(variant.sourceProgram).orElse(null)
            ?: run {
                LOGGER.warn("HollowEngine Iris instancing: no program source for {}", variant.sourceProgram)
                return null
            }

        val patchedVertexSource = source.vertexSource.orElse(null)?.let(IrisInstancingShaderPatcher::patch) ?: return null
        val recreated = recreateProgramSource(source, patchedVertexSource)
        val name = "hollowengine_instanced_${variant.name.lowercase()}_${shaderCounter++}"

        return try {
            if (variant.shadow) {
                callCreateShadowShader(
                    accessor,
                    name,
                    recreated,
                    variant.sourceProgram,
                    false,
                    false,
                    false,
                    variant.fallbackAlpha
                )
            } else {
                callCreateShader(
                    accessor,
                    name,
                    recreated,
                    variant.sourceProgram,
                    false,
                    false,
                    false,
                    false,
                    variant.fallbackAlpha
                )
            }
        } catch (t: Throwable) {
            LOGGER.warn("HollowEngine Iris instancing: failed to compile {}", variant.name.lowercase(), t)
            null
        }
    }

    private fun recreateProgramSource(source: ProgramSource, patchedVertexSource: String): ProgramSource {
        val accessor = source as ProgramSourceAccessor
        return ProgramSource(
            source.name + "_hollowengine_instanced",
            patchedVertexSource,
            source.geometrySource.orElse(null),
            source.tessControlSource.orElse(null),
            source.tessEvalSource.orElse(null),
            source.fragmentSource.orElse(null),
            source.parent,
            accessor.shaderPropertiesValue,
            accessor.blendModeOverrideValue
        )
    }

    private fun callCreateShader(
        pipeline: IrisRenderingPipelineAccessor,
        name: String,
        source: ProgramSource,
        programId: ProgramId,
        isIntensity: Boolean,
        isFullbright: Boolean,
        isGlint: Boolean,
        isText: Boolean,
        fallbackAlpha: AlphaTest,
    ): ShaderInstance {
        return pipeline.`hollowengine$createShader`(
            name,
            source,
            programId,
            fallbackAlpha,
            IrisVertexFormats.ENTITY,
            FogMode.PER_VERTEX,
            isIntensity,
            isFullbright,
            isGlint,
            isText,
            false,
        )
    }

    private fun callCreateShadowShader(
        pipeline: IrisRenderingPipelineAccessor,
        name: String,
        source: ProgramSource,
        programId: ProgramId,
        isIntensity: Boolean,
        isFullbright: Boolean,
        isText: Boolean,
        fallbackAlpha: AlphaTest,
    ): ShaderInstance {
        return pipeline.`hollowengine$createShadowShader`(
            name,
            source,
            programId,
            fallbackAlpha,
            IrisVertexFormats.ENTITY,
            isIntensity,
            isFullbright,
            isText,
            false,
        )
    }
}
