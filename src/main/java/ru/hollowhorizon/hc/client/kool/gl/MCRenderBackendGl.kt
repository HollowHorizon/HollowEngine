package ru.hollowhorizon.hc.client.kool.gl

import de.fabmax.kool.KoolContext
import de.fabmax.kool.KoolSystem
import de.fabmax.kool.configJvm
import de.fabmax.kool.pipeline.ComputePass
import de.fabmax.kool.pipeline.GpuPass
import de.fabmax.kool.pipeline.OffscreenPass2d
import de.fabmax.kool.pipeline.OffscreenPassCube
import de.fabmax.kool.pipeline.backend.BackendFeatures
import de.fabmax.kool.pipeline.backend.DeviceCoordinates
import de.fabmax.kool.pipeline.backend.gl.GlImpl
import de.fabmax.kool.pipeline.backend.gl.GlslGenerator
import de.fabmax.kool.pipeline.backend.gl.RenderBackendGl
import de.fabmax.kool.pipeline.backend.gl.TimeQuery
import de.fabmax.kool.pipeline.backend.stats.BackendStats
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.KoolHooks.impl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MCRenderBackendGl(ctx: KoolContext) : RenderBackendGl(KoolSystem.configJvm.numSamples, MCGlApi, ctx) {
    val gl = MCGlApi
    override val features: BackendFeatures
    val mcSceneRenderer = MCSceneRenderPass(numSamples, this)

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
        get() = GlslGenerator.Hints("#version 430 core")

    override fun cleanup(ctx: KoolContext) {}

    override fun renderFrame(ctx: KoolContext) {
        if (timer.isAvailable) {
            frameGpuTime = timer.getQueryResult()
        }

        timer.timedScope {
            renderMCFrame(ctx)
        }
    }

    fun renderMCFrame(ctx: KoolContext) {
        BackendStats.resetPerFrameCounts()

        mcSceneRenderer.applySize(ctx.windowWidth, ctx.windowHeight)
        ctx.backgroundScene.executePasses()

        for (i in ctx.scenes.indices) {
            val scene = ctx.scenes[i]
            if (scene.isVisible) {
                scene.executePasses()
            }
        }

        if (useFloatDepthBuffer) {
            mcSceneRenderer.resolve(gl.DEFAULT_FRAMEBUFFER, gl.COLOR_BUFFER_BIT)
        }

        if (awaitedStorageBuffers.isNotEmpty()) {
            readbackStorageBuffers()
        }
    }

    override fun GpuPass.execute() {
        when (this) {
            is Scene.ScreenPass -> mcSceneRenderer.draw(this)
            is OffscreenPass2d -> impl(this).draw()
            is OffscreenPassCube -> impl(this).draw()
            is ComputePass -> impl(this).dispatch()
            else -> error("Gpu pass type not implemented: $this")
        }
    }
}