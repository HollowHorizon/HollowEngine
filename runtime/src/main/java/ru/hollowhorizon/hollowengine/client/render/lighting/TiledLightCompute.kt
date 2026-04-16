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
import org.lwjgl.opengl.GL43C
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.function.DoubleSupplier

internal object TiledLightCompute {
    private val SHADER_PATH = "hollowengine:shaders/clustered-lighting/tiled_light_cull.csh".rl

    private var program: ComputeProgram? = null
    private var failed = false

    private var lightCount = 0
    private var tileCountX = 0
    private var tileCountY = 0
    private var viewResolution = Vector2i()
    private var nearPlane = 0.05f
    private var farPlane = 256f
    private var projectionMatrix = Matrix4f()
    private var projectionMatrixInverse = Matrix4f()

    fun isSupported(): Boolean = !failed && IrisRenderSystem.supportsCompute()

    fun invalidate() {
        program?.destroy()
        program = null
        failed = false
    }

    fun dispatch(
        lightCount: Int,
        tileCountX: Int,
        tileCountY: Int,
        viewResolution: Vector2i,
        clipPlanes: ClusterClipPlanes,
        projectionMatrix: Matrix4f,
        visibleLightIndexBuffer: ShaderStorageBuffer,
        coreLightBuffer: ShaderStorageBuffer,
        pointLightBuffer: ShaderStorageBuffer,
        spotLightBuffer: ShaderStorageBuffer,
        clusterIndexBuffer: ShaderStorageBuffer,
        volumetricTileIndexBuffer: ShaderStorageBuffer,
        hasVolumetricLights: Boolean,
    ): Boolean {
        if (!isSupported()) return false

        val computeProgram = ensureProgram() ?: return false
        val depthTextureId = Minecraft.getInstance().mainRenderTarget.depthTextureId
        if (depthTextureId <= 0) return false

        this.lightCount = lightCount
        this.tileCountX = tileCountX
        this.tileCountY = tileCountY
        this.viewResolution.set(viewResolution)
        this.nearPlane = clipPlanes.nearPlane
        this.farPlane = clipPlanes.farPlane
        this.projectionMatrix.set(projectionMatrix)
        this.projectionMatrixInverse.set(projectionMatrix).invert()

        val clusterStride = ClusteredLightingConfig.MAX_LIGHTS_PER_TILE + 1
        val volumetricStride = ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1
        clusterIndexBuffer.ensureCapacity(tileCountX * tileCountY * clusterStride * Int.SIZE_BYTES)
        volumetricTileIndexBuffer.ensureCapacity(
            (if (hasVolumetricLights) tileCountX * tileCountY else 1) * volumetricStride * Int.SIZE_BYTES
        )

        visibleLightIndexBuffer.bindBase()
        coreLightBuffer.bindBase()
        pointLightBuffer.bindBase()
        spotLightBuffer.bindBase()
        clusterIndexBuffer.bindBase()
        volumetricTileIndexBuffer.bindBase()

        computeProgram.use()
        IrisRenderSystem.dispatchCompute(tileCountX, tileCountY, 1)
        IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT or GL43C.GL_TEXTURE_FETCH_BARRIER_BIT)
        ComputeProgram.unbind()
        return true
    }

    private fun ensureProgram(): ComputeProgram? {
        program?.let { return it }
        return buildProgram("he_tiled_light_cull", loadShaderSource(SHADER_PATH)) { builder ->
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uLightCount") { lightCount }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uTileCountX") { tileCountX }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uTileCountY") { tileCountY }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uViewWidth") { viewResolution.x }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uViewHeight") { viewResolution.y }
            builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "uNearPlane", DoubleSupplier { nearPlane.toDouble() })
            builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "uFarPlane", DoubleSupplier { farPlane.toDouble() })
            builder.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "uProjectionMatrix") { Matrix4f(projectionMatrix) }
            builder.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "uProjectionMatrixInverse") { Matrix4f(projectionMatrixInverse) }
            builder.addDynamicSampler({ Minecraft.getInstance().mainRenderTarget.depthTextureId }, "uDepthTexture")
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
            HollowCore.LOGGER.error("Failed to build tiled lighting compute program {}", name, t)
            null
        }
    }

    private fun loadShaderSource(path: ResourceLocation): String {
        return Minecraft.getInstance().resourceManager.getResource(path).orElseThrow {
            IllegalStateException("Missing tiled lighting compute shader resource: $path")
        }.open().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
