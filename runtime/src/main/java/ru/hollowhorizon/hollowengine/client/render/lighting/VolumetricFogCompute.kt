package ru.hollowhorizon.hollowengine.client.render.lighting

import com.google.common.collect.ImmutableSet
import net.irisshaders.iris.gl.IrisRenderSystem
import net.irisshaders.iris.gl.program.ComputeProgram
import net.irisshaders.iris.gl.program.ProgramBuilder
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import org.lwjgl.opengl.GL43C
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.function.DoubleSupplier

internal object VolumetricFogCompute {
    private const val ENABLE_PROPERTY = "hollowengine.volumetricFog.compute"
    private const val DOWNSAMPLE_PROPERTY = "hollowengine.volumetricFog.compute.downsample"
    private const val MAX_LIGHTS_PROPERTY = "hollowengine.volumetricFog.compute.maxLights"
    private const val MAX_STEPS_PROPERTY = "hollowengine.volumetricFog.compute.maxSteps"

    private val SHADER_PATH = "hollowengine:shaders/clustered-lighting/volumetric_fog.csh".rl

    private var program: ComputeProgram? = null
    private var failed = false

    private var lightCount = 0
    private var tileCountX = 0
    private var tileCountY = 0
    private var viewResolution = Vector2i()
    private var fogResolution = Vector2i(1, 1)
    private var projectionMatrixInverse = Matrix4f()
    private var viewMatrixInverse = Matrix4f()
    private var cameraPosition = Vector3f()
    private var nearPlane = 0.05f
    private var farPlane = 256f
    private var useTileLists = false
    private var frameIndex = 0
    private var shadowLightCount = 0
    private var spotShadowAtlasResolution = Vector2i(1, 1)
    private var pointShadowAtlasResolution = Vector2i(1, 1)
    private var downsample = 2
    private var maxLights = 16
    private var maxSteps = 48

    fun isSupported(): Boolean {
        if (failed || !IrisRenderSystem.supportsCompute()) return false
        val enabled = System.getProperty(ENABLE_PROPERTY)?.toBooleanStrictOrNull() ?: true
        return enabled
    }

    fun invalidate() {
        program?.destroy()
        program = null
        failed = false
        frameIndex = 0
        fogResolution.set(1, 1)
    }

    fun currentFogResolution(): Vector2i = Vector2i(fogResolution)

    fun currentDownsample(): Int = downsample

    fun dispatch(
        lightCount: Int,
        tileCountX: Int,
        tileCountY: Int,
        viewResolution: Vector2i,
        clipPlanes: ClusterClipPlanes,
        viewMatrix: Matrix4f,
        cameraPosition: Vector3f,
        projectionMatrix: Matrix4f,
        useTileLists: Boolean,
        coreLightBuffer: ShaderStorageBuffer,
        pointLightBuffer: ShaderStorageBuffer,
        spotLightBuffer: ShaderStorageBuffer,
        volumetricFogBuffer: ShaderStorageBuffer,
        volumetricTileIndexBuffer: ShaderStorageBuffer,
        outputBuffer: ShaderStorageBuffer,
    ): Boolean {
        if (!isSupported()) return false

        val computeProgram = ensureProgram() ?: return false
        val depthTextureId = Minecraft.getInstance().mainRenderTarget.depthTextureId
        if (depthTextureId <= 0) return false

        this.downsample = sanitizeDownsample(System.getProperty(DOWNSAMPLE_PROPERTY)?.toIntOrNull() ?: 2)
        this.maxLights = (System.getProperty(MAX_LIGHTS_PROPERTY)?.toIntOrNull() ?: 16).coerceIn(1, 64)
        this.maxSteps = (System.getProperty(MAX_STEPS_PROPERTY)?.toIntOrNull() ?: 48).coerceIn(4, 128)
        this.lightCount = lightCount
        this.tileCountX = tileCountX
        this.tileCountY = tileCountY
        this.useTileLists = useTileLists
        this.viewResolution.set(viewResolution)
        this.nearPlane = clipPlanes.nearPlane
        this.farPlane = clipPlanes.farPlane
        this.viewMatrixInverse.set(viewMatrix).invert()
        this.cameraPosition.set(cameraPosition)
        this.projectionMatrixInverse.set(projectionMatrix).invert()
        this.shadowLightCount = LocalLightShadowManager.currentShadowLightCount()
        this.spotShadowAtlasResolution.set(LocalLightShadowManager.currentSpotShadowAtlasResolution())
        this.pointShadowAtlasResolution.set(LocalLightShadowManager.currentPointShadowAtlasResolution())
        val newFogResolution = Vector2i(
            ceilDiv(maxOf(1, viewResolution.x), downsample),
            ceilDiv(maxOf(1, viewResolution.y), downsample),
        )
        if (newFogResolution != fogResolution) {
            fogResolution.set(newFogResolution)
        }

        outputBuffer.ensureCapacity(fogResolution.x * fogResolution.y * 4 * Float.SIZE_BYTES)
        coreLightBuffer.bindBase()
        pointLightBuffer.bindBase()
        spotLightBuffer.bindBase()
        volumetricFogBuffer.bindBase()
        volumetricTileIndexBuffer.bindBase()
        LocalLightShadowManager.bindShadowDataBuffer()
        outputBuffer.bindBase()

        computeProgram.use()
        IrisRenderSystem.dispatchCompute(ceilDiv(fogResolution.x, 8), ceilDiv(fogResolution.y, 8), 1)
        IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT or GL43C.GL_TEXTURE_FETCH_BARRIER_BIT)
        ComputeProgram.unbind()
        frameIndex = (frameIndex + 1) and Int.MAX_VALUE
        return true
    }

    private fun ensureProgram(): ComputeProgram? {
        program?.let { return it }
        return buildProgram("he_volumetric_fog", loadShaderSource(SHADER_PATH)) { builder ->
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uLightCount") { lightCount }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uTileCountX") { tileCountX }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uTileCountY") { tileCountY }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uUseTileLists") { if (useTileLists) 1 else 0 }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uFrameIndex") { frameIndex }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uViewWidth") { viewResolution.x }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uViewHeight") { viewResolution.y }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uFogWidth") { fogResolution.x }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uFogHeight") { fogResolution.y }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uDownsample") { downsample }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uMaxLights") { maxLights }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uMaxSteps") { maxSteps }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uShadowLightCount") { shadowLightCount }
            builder.uniform2i(UniformUpdateFrequency.PER_FRAME, "uSpotShadowAtlasResolution") {
                Vector2i(spotShadowAtlasResolution)
            }
            builder.uniform2i(UniformUpdateFrequency.PER_FRAME, "uPointShadowAtlasResolution") {
                Vector2i(pointShadowAtlasResolution)
            }
            builder.uniform3f(UniformUpdateFrequency.PER_FRAME, "uCameraPosition") { Vector3f(cameraPosition) }
            builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "uNearPlane", DoubleSupplier { nearPlane.toDouble() })
            builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "uFarPlane", DoubleSupplier { farPlane.toDouble() })
            builder.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "uViewMatrixInverse") { Matrix4f(viewMatrixInverse) }
            builder.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "uProjectionMatrixInverse") { Matrix4f(projectionMatrixInverse) }
            builder.addDynamicSampler({ Minecraft.getInstance().mainRenderTarget.depthTextureId }, "uDepthTexture")
            builder.addDynamicSampler(LocalLightShadowManager::currentSpotShadowAtlasTextureId, "uSpotShadowAtlas")
            builder.addDynamicSampler(LocalLightShadowManager::currentPointShadowAtlasTextureId, "uPointShadowAtlas")
        }.also { program = it }
    }

    private fun buildProgram(
        name: String,
        source: String,
        configure: (ProgramBuilder) -> Unit = {},
    ): ComputeProgram? {
        return try {
            ProgramBuilder.beginCompute(name, source, ImmutableSet.of()).also(configure).buildCompute()
        } catch (t: Throwable) {
            failed = true
            HollowCore.LOGGER.error("Failed to build volumetric fog compute program {}", name, t)
            null
        }
    }

    private fun loadShaderSource(path: ResourceLocation): String {
        return Minecraft.getInstance().resourceManager.getResource(path).orElseThrow {
            IllegalStateException("Missing volumetric fog compute shader resource: $path")
        }.open().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun sanitizeDownsample(value: Int): Int = when {
        value <= 1 -> 1
        value <= 2 -> 2
        value <= 3 -> 3
        else -> 4
    }

    private fun ceilDiv(value: Int, divisor: Int): Int = if (value <= 0) 1 else (value + divisor - 1) / divisor
}
