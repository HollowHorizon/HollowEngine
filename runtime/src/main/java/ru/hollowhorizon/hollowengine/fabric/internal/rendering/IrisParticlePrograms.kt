package ru.hollowhorizon.hollowengine.fabric.internal.rendering

import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.irisshaders.iris.gl.blending.AlphaTests
import net.irisshaders.iris.gl.state.FogMode
import net.irisshaders.iris.pipeline.IrisRenderingPipeline
import net.irisshaders.iris.shaderpack.loading.ProgramId
import net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver
import net.irisshaders.iris.shaderpack.programs.ProgramSource
import net.minecraft.client.renderer.ShaderInstance
import ru.hollowhorizon.hollowengine.LOGGER
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.IrisRenderingPipelineAccessor
import ru.hollowhorizon.hollowengine.fabric.internal.accessors.ProgramSourceAccessor

/**
 * The shader pack's own particle programs, patched to read effect particles as instances.
 */
object IrisParticlePrograms {
    private var pipeline: IrisRenderingPipeline? = null
    private val programs = HashMap<Boolean, ShaderInstance>()
    private val failures = HashSet<Boolean>()
    private var counter = 0

    fun shaderFor(translucent: Boolean): ShaderInstance? {
        val current = IrisHelper.currentPipeline() as? IrisRenderingPipeline ?: return null
        if (pipeline !== current) {
            invalidate()
            pipeline = current
        }

        programs[translucent]?.let { return it }
        if (translucent in failures) return null

        val created = create(current, translucent)
        if (created == null) failures += translucent else programs[translucent] = created
        return created
    }

    fun invalidate() {
        programs.clear()
        failures.clear()
        pipeline = null
    }

    private fun create(pipeline: IrisRenderingPipeline, translucent: Boolean): ShaderInstance? {
        val accessor = pipeline as? IrisRenderingPipelineAccessor ?: return null
        val programId = if (translucent) ProgramId.ParticlesTrans else ProgramId.Particles
        val source = ProgramFallbackResolver(accessor.programSet).resolve(programId).orElse(null) ?: return null
        val vertex = source.vertexSource.orElse(null)?.let(IrisParticleShaderPatcher::patch) ?: return null

        return try {
            accessor.`hollowengine$createShader`(
                "hollowengine_vfx_particles_${if (translucent) "trans" else "solid"}_${counter++}",
                recreate(source, vertex),
                programId,
                AlphaTests.ONE_TENTH_ALPHA,
                DefaultVertexFormat.PARTICLE,
                FogMode.PER_VERTEX,
                false,
                false,
                false,
                false,
                false,
            )
        } catch (t: Throwable) {
            LOGGER.warn("HollowEngine Iris particles: failed to compile the {} program", programId, t)
            null
        }
    }

    private fun recreate(source: ProgramSource, vertex: String): ProgramSource {
        val accessor = source as ProgramSourceAccessor
        return ProgramSource(
            source.name + "_hollowengine_vfx",
            vertex,
            source.geometrySource.orElse(null),
            source.tessControlSource.orElse(null),
            source.tessEvalSource.orElse(null),
            source.fragmentSource.orElse(null),
            source.parent,
            accessor.shaderPropertiesValue,
            accessor.blendModeOverrideValue,
        )
    }
}

/**
 * Rewrites a pack particle vertex shader to build its quad from instance data.
 */
object IrisParticleShaderPatcher {
    private val versionPattern = Regex("#version\\s+(\\d+)([^\\n]*)")
    private val extensionPattern = Regex("(?m)^\\s*#extension[^\\n]*$")
    private val coreInputPattern =
        Regex("(?m)^\\s*(in|attribute)\\s+\\w+\\s+(vaPosition|vaColor|vaUV0|vaUV1|vaUV2|vaNormal)\\s*;\\s*$")

    fun patch(source: String): String {
        val match = versionPattern.find(source) ?: return source
        val version = match.groupValues[1].toIntOrNull() ?: return source
        val input = if (version >= 130) "in" else "attribute"

        val header = buildString {
            appendLine()
            appendLine("$input vec3 Position;")
            appendLine("$input vec2 UV0;")
            appendLine("$input vec3 InstanceCenter;")
            appendLine("$input vec3 InstanceRight;")
            appendLine("$input vec3 InstanceUp;")
            appendLine("$input vec4 InstanceColor;")
            appendLine("$input vec4 InstanceUv;")
            appendLine("$input vec2 InstanceLight;")
            appendLine("vec3 _he_Position() {")
            appendLine("    return InstanceCenter + InstanceRight * Position.x + InstanceUp * Position.y;")
            appendLine("}")
            appendLine("vec2 _he_Uv() {")
            appendLine("    return InstanceUv.xy + UV0 * InstanceUv.zw;")
            appendLine("}")
        }

        val body = coreInputPattern.replace(source, "")
        val insertion = extensionPattern.findAll(body).lastOrNull()?.range?.last?.plus(1)
            ?: (versionPattern.find(body)!!.range.last + 1)
        var patched = body.substring(0, insertion) + header + body.substring(insertion)

        patched = patched.replace("ftransform()", "(gl_ModelViewProjectionMatrix * vec4(_he_Position(), 1.0))")
        patched = replaceToken(patched, "gl_Vertex", "vec4(_he_Position(), 1.0)")
        patched = replaceToken(patched, "gl_Color", "InstanceColor")
        patched = replaceToken(patched, "gl_MultiTexCoord0", "vec4(_he_Uv(), 0.0, 1.0)")
        patched = replaceToken(patched, "gl_MultiTexCoord1", "vec4(InstanceLight, 0.0, 1.0)")
        patched = replaceToken(patched, "gl_MultiTexCoord2", "vec4(InstanceLight, 0.0, 1.0)")
        patched = replaceToken(patched, "gl_Normal", "vec3(0.0, 0.0, 1.0)")

        patched = replaceToken(patched, "vaPosition", "_he_Position()")
        patched = replaceToken(patched, "vaColor", "InstanceColor")
        patched = replaceToken(patched, "vaUV0", "_he_Uv()")
        patched = replaceToken(patched, "vaUV1", "ivec2(0, 10)")
        patched = replaceToken(patched, "vaUV2", "ivec2(InstanceLight)")
        patched = replaceToken(patched, "vaNormal", "vec3(0.0, 0.0, 1.0)")
        return patched
    }

    private fun replaceToken(source: String, token: String, replacement: String): String =
        Regex("(?<![A-Za-z0-9_])${Regex.escape(token)}(?![A-Za-z0-9_])").replace(source, replacement)
}
