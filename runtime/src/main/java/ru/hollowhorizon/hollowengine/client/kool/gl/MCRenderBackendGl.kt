package ru.hollowhorizon.hollowengine.client.kool.gl

import de.fabmax.kool.*
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.backend.BackendFeatures
import de.fabmax.kool.pipeline.backend.DeviceCoordinates
import de.fabmax.kool.pipeline.backend.gl.*
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Buffer
import de.fabmax.kool.util.Color
import kotlinx.coroutines.CompletableDeferred
import org.lwjgl.opengl.GL30
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MCRenderBackendGl(ctx: KoolContext) : RenderBackendGl(KoolSystem.configJvm.numSamples, MCGlApi, ctx) {
    val gl = MCGlApi
    private val koolContext = ctx
    override val features: BackendFeatures
    val mcSceneRenderer = MCSceneRenderPass(numSamples, this)
    private val pendingScreenPasses = mutableMapOf<Scene, PassData>()
    private val awaitedStorageBuffers = mutableListOf<ReadbackStorageBuffer>()
    lateinit var currentFrameData: FrameData

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

    override val name = "Minecraft OpenGL"
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
            prepareMCFrame(frameData, ctx)
        }
    }

    fun prepareMCFrame(frameData: FrameData, ctx: KoolContext) {
        BackendStats.resetPerFrameCounts()
        currentFrameData = frameData

        pendingScreenPasses.clear()

        mcSceneRenderer.applySize(ctx.window.size.x, ctx.window.size.y)
        frameData.forEachPass { passData ->
            val pass = passData.gpuPass
            if (pass is Scene.ScreenPass) {
                pass.parentScene?.let { scene ->
                    pendingScreenPasses[scene] = passData
                }
            } else {
                passData.executePass()
            }
        }

        if (awaitedStorageBuffers.isNotEmpty()) {
            readbackStorageBuffers()
        }
    }

    fun renderScene(scene: Scene) {
        val passData = pendingScreenPasses[scene] ?: return
        val targetFbo = currentTargetFramebuffer()
        mcSceneRenderer.draw(passData)
        pendingScreenPasses.remove(scene)
        mcSceneRenderer.resolve(targetFbo, gl.COLOR_BUFFER_BIT)
    }

    fun collectScene(scene: Scene, passData: PassData = PassData()): PassData {
        passData.reset(scene.mainRenderPass)
        scene.mainRenderPass.collect(passData, koolContext)
        return passData
    }

    fun renderCollectedScene(passData: PassData) {
        val targetFbo = currentTargetFramebuffer()
        passData.updatePipelineData()
        mcSceneRenderer.draw(passData)
        mcSceneRenderer.resolve(targetFbo, gl.COLOR_BUFFER_BIT)
    }

    fun renderSceneLate(scene: Scene, passData: PassData = PassData()) {
        pendingScreenPasses.remove(scene)
        renderCollectedScene(collectScene(scene, passData))
    }

    private fun currentTargetFramebuffer(): GlFramebuffer =
        GlFramebuffer(GL30.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING))

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

    private fun PassData.updatePipelineData() {
        for (vi in viewData.indices) {
            val viewData = viewData[vi]
            viewData.drawQueue.forEach {
                it.updatePipelineData()
                it.captureData()
            }
            viewData.drawQueue.view.viewPipelineData.captureBuffer()
        }
    }

    private class ReadbackStorageBuffer(
        val storage: GpuBuffer,
        val deferred: CompletableDeferred<Unit>,
        val resultBuffer: Buffer,
    )
}
