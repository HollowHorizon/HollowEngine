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

internal object ClusteredLightCompute {
    private const val COMPUTE_PROPERTY = "hollowengine.clusteredLighting.compute"
    private const val CLEAR_LOCAL_SIZE_X = 256
    private const val ASSIGN_LOCAL_SIZE_X = 64
    private const val FLAG_HAS_VOLUMETRIC_FOG = 1 shl 3
    private val CLEAR_SHADER_PATH = "hollowengine:shaders/clustered-lighting/cluster_clear.csh".rl
    private val ASSIGN_SHADER_PATH = "hollowengine:shaders/clustered-lighting/cluster_assign.csh".rl

    private var clearProgram: ComputeProgram? = null
    private var assignProgram: ComputeProgram? = null
    private var failed = false

    private var clusterCount = 0
    private var clusterStride = 0
    private var volumetricTileCount = 0
    private var volumetricStride = 0
    private var lightCount = 0
    private var tileCountX = 0
    private var tileCountY = 0
    private var viewResolution = Vector2i()
    private var projectionMatrix = Matrix4f()
    private var clipPlanes = ClusterClipPlanes(0.05f, 256f)

    fun isSupported(): Boolean =
        !failed &&
            IrisRenderSystem.supportsCompute() &&
            java.lang.Boolean.getBoolean(COMPUTE_PROPERTY)

    fun invalidate() {
        clearProgram?.destroy()
        assignProgram?.destroy()
        clearProgram = null
        assignProgram = null
        failed = false
    }

    fun dispatch(
        lightCount: Int,
        tileCountX: Int,
        tileCountY: Int,
        clusterCount: Int,
        volumetricTileCount: Int,
        viewResolution: Vector2i,
        projectionMatrix: Matrix4f,
        clipPlanes: ClusterClipPlanes,
        coreLightBuffer: ShaderStorageBuffer,
        clusterIndexBuffer: ShaderStorageBuffer,
        volumetricTileIndexBuffer: ShaderStorageBuffer,
    ): Boolean {
        if (!isSupported()) return false

        val clear = ensureClearProgram() ?: return false
        val assign = ensureAssignProgram() ?: return false

        this.lightCount = lightCount
        this.tileCountX = tileCountX
        this.tileCountY = tileCountY
        this.clusterCount = clusterCount
        this.volumetricTileCount = volumetricTileCount
        this.viewResolution.set(viewResolution)
        this.projectionMatrix.set(projectionMatrix)
        this.clipPlanes = clipPlanes
        this.clusterStride = ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER + 1
        this.volumetricStride = ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1

        clusterIndexBuffer.ensureCapacity(clusterCount * clusterStride * Int.SIZE_BYTES)
        volumetricTileIndexBuffer.ensureCapacity(volumetricTileCount * volumetricStride * Int.SIZE_BYTES)
        coreLightBuffer.bindBase()
        clusterIndexBuffer.bindBase()
        volumetricTileIndexBuffer.bindBase()

        clear.use()
        IrisRenderSystem.dispatchCompute(
            maxOf(
                ceilDiv(clusterCount, CLEAR_LOCAL_SIZE_X),
                ceilDiv(volumetricTileCount, CLEAR_LOCAL_SIZE_X),
            ),
            1,
            1,
        )
        IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT)

        if (lightCount > 0) {
            assign.use()
            IrisRenderSystem.dispatchCompute(ceilDiv(lightCount, ASSIGN_LOCAL_SIZE_X), 1, 1)
            IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT)
        }

        ComputeProgram.unbind()
        return true
    }

    private fun ensureClearProgram(): ComputeProgram? {
        clearProgram?.let { return it }
        return buildProgram("he_cluster_clear", loadShaderSource(CLEAR_SHADER_PATH)) { builder ->
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uClusterCount") { clusterCount }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uClusterStride") { clusterStride }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uVolumetricTileCount") { volumetricTileCount }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uVolumetricStride") { volumetricStride }
        }.also { clearProgram = it }
    }

    private fun ensureAssignProgram(): ComputeProgram? {
        assignProgram?.let { return it }
        return buildProgram("he_cluster_assign", loadShaderSource(ASSIGN_SHADER_PATH)) { builder ->
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uLightCount") { lightCount }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uTileCountX") { tileCountX }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uTileCountY") { tileCountY }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uViewWidth") { viewResolution.x }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uViewHeight") { viewResolution.y }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uTileSize") { ClusteredLightingConfig.TILE_SIZE }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uClusterStride") { clusterStride }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uVolumetricStride") { volumetricStride }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uMaxLightsPerCluster") { ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uMaxVolumetricLightsPerTile") { ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uZSlices") { ClusteredLightingConfig.Z_SLICES }
            builder.uniform1i(UniformUpdateFrequency.PER_FRAME, "uVolumetricFlag") { FLAG_HAS_VOLUMETRIC_FOG }
            builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "uNearPlane", DoubleSupplier { clipPlanes.nearPlane.toDouble() })
            builder.uniform1f(UniformUpdateFrequency.PER_FRAME, "uFarPlane", DoubleSupplier { clipPlanes.farPlane.toDouble() })
            builder.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "uProjectionMatrix") { Matrix4f(projectionMatrix) }
        }.also { assignProgram = it }
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
            HollowCore.LOGGER.error("Failed to build clustered lighting compute program {}", name, t)
            null
        }
    }

    private fun loadShaderSource(path: ResourceLocation): String {
        return Minecraft.getInstance().resourceManager.getResource(path).orElseThrow {
            IllegalStateException("Missing clustered lighting compute shader resource: $path")
        }.open().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun ceilDiv(value: Int, divisor: Int): Int =
        if (value <= 0) 1 else (value + divisor - 1) / divisor
}
