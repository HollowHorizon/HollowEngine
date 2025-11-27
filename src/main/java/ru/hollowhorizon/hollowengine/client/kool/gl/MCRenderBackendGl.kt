package ru.hollowhorizon.hollowengine.client.kool.gl

import de.fabmax.kool.FrameData
import de.fabmax.kool.KoolContext
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.configJvm
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.backend.BackendFeatures
import de.fabmax.kool.pipeline.backend.DeviceCoordinates
import de.fabmax.kool.pipeline.backend.gl.GlslGenerator
import de.fabmax.kool.pipeline.backend.gl.GpuBufferGl
import de.fabmax.kool.pipeline.backend.gl.RenderBackendGl
import de.fabmax.kool.pipeline.backend.gl.TimeQuery
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.util.Buffer
import de.fabmax.kool.util.Color
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MCRenderBackendGl(ctx: KoolContext) : RenderBackendGl(KoolSystem.configJvm.numSamples, MCGlApi, ctx) {
    val gl = MCGlApi
    override val features: BackendFeatures
    val mcSceneRenderer = MCSceneRenderPass(numSamples, this)
    override val name = "Minecraft OpenGL"

    init {
        gl.initOpenGl(this)
        mcSceneRenderer.resolveDirect = true
        features = BackendFeatures(
            computeShaders = true,
            cubeMapArrays = true,
            reversedDepth = gl.capabilities.hasClipControl,

            maxSamples = 4,
            readWriteStorageTextures = true,
            depthOnlyShaderColorOutput = Color.BLACK,
            maxComputeWorkGroupsPerDimension = gl.capabilities.maxWorkGroupCount,
            maxComputeWorkGroupSize = gl.capabilities.maxWorkGroupSize,
            maxComputeInvocationsPerWorkgroup = gl.capabilities.maxWorkGroupInvocations
        )
        deviceCoordinates = DeviceCoordinates.OPEN_GL
    }

    override var frameGpuTime: Duration = 0.0.seconds
    private val timer = TimeQuery(gl)

    override val glslGeneratorHints: GlslGenerator.Hints
        get() = GlslGenerator.Hints("#version 330 core")

    override fun cleanup(ctx: KoolContext) {}

    override val isAsyncRendering: Boolean
        get() = true

    override fun renderFrame(frameData: FrameData, ctx: KoolContext) {
        if (timer.isAvailable) {
            frameGpuTime = timer.getQueryResult()
        }

        timer.timedScope {
            renderMCFrame(frameData, ctx)
        }
    }

    private val awaitedStorageBuffers = mutableListOf<ReadbackStorageBuffer>()
    lateinit var currentFrameData: FrameData

    fun renderMCFrame(frameData: FrameData, ctx: KoolContext) {
        BackendStats.resetPerFrameCounts()
        currentFrameData = frameData

        mcSceneRenderer.applySize(ctx.window.size.x, ctx.window.size.y)
        frameData.forEachPass { passData ->
            passData.executePass()
        }

        if (useFloatDepthBuffer) {
            mcSceneRenderer.resolve(gl.DEFAULT_FRAMEBUFFER, gl.COLOR_BUFFER_BIT)
        }

        if (awaitedStorageBuffers.isNotEmpty()) {
            readbackStorageBuffers()
        }
    }
    fun readbackStorageBuffers() {
        gl.memoryBarrier(gl.SHADER_STORAGE_BARRIER_BIT)
        awaitedStorageBuffers.forEach { readback ->
            val gpuBuf = readback.storage.gpuBuffer as GpuBufferGl?
            if (gpuBuf == null || !gl.readBuffer(gpuBuf, readback.resultBuffer)) {
                readback.deferred.completeExceptionally(IllegalStateException("Failed reading buffer"))
            } else {
                readback.deferred.complete(Unit)
            }
        }
        awaitedStorageBuffers.clear()
    }

    private class ReadbackStorageBuffer(val storage: GpuBuffer, val deferred: CompletableDeferred<Unit>, val resultBuffer: Buffer)
}