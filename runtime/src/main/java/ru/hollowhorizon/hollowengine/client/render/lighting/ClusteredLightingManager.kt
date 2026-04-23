package ru.hollowhorizon.hollowengine.client.render.lighting

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.math.Vec3f
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer
import net.irisshaders.iris.gl.sampler.SamplerHolder
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.phys.AABB
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.render.resolveAnchoredTransform
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.geary.anchor.EntityAnchor
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.LightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.PointLightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.SpotLightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.TransformComponent
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper
import java.nio.ByteBuffer
import kotlin.math.ceil

object ClusteredLightingManager : ResourceManagerReloadListener {
    private const val FLAG_POINT_LIGHT = 1
    private const val FLAG_SPOT_LIGHT = 1 shl 1
    private const val FLAG_HAS_SHADOW = 1 shl 2
    private const val FLAG_HAS_VOLUMETRIC_FOG = 1 shl 3
    private const val FLAG_HAS_FLARE = 1 shl 4
    private val EMPTY_INDEX_LIST = intArrayOf(0)

    private val coreLightBuffer = ShaderStorageBuffer(ClusteredLightingConfig.CORE_LIGHT_BINDING)
    private val pointLightBuffer = ShaderStorageBuffer(ClusteredLightingConfig.POINT_LIGHT_BINDING)
    private val spotLightBuffer = ShaderStorageBuffer(ClusteredLightingConfig.SPOT_LIGHT_BINDING)
    private val shadowSettingsBuffer = ShaderStorageBuffer(ClusteredLightingConfig.SHADOW_SETTINGS_BINDING)
    private val volumetricFogBuffer = ShaderStorageBuffer(ClusteredLightingConfig.VOLUMETRIC_FOG_BINDING)
    private val flareBuffer = ShaderStorageBuffer(ClusteredLightingConfig.FLARE_BINDING)
    private val clusterIndexBuffer = ShaderStorageBuffer(ClusteredLightingConfig.CLUSTER_INDEX_BINDING)
    private val volumetricTileIndexBuffer = ShaderStorageBuffer(ClusteredLightingConfig.VOLUMETRIC_TILE_INDEX_BINDING)
    private val visibleLightIndexBuffer = ShaderStorageBuffer(ClusteredLightingConfig.VISIBLE_LIGHT_INDEX_BINDING)
    private val volumetricComputeOutputBuffer = ShaderStorageBuffer(ClusteredLightingConfig.VOLUMETRIC_COMPUTE_OUTPUT_BINDING)

    private var enabled = false
    private var cullingMode = LightCullingMode.DISABLED
    private var lightCount = 0
    private var clusterCount = 0
    private var volumetricTileCount = 0
    private var volumetricLightCount = 0
    private var flareLightCount = 0
    private var overflowedClusters = 0
    private var overflowedVolumetricTiles = 0
    private var volumetricFogComputeEnabled = false
    private var volumetricFogComputeResolution = Vector2i(1, 1)
    private var volumetricFogComputeDownsample = 1

    private var viewResolution = Vector2i()
    private var cameraPosition = Vector3f()
    private var viewMatrix = Matrix4f()
    private var projectionMatrix = Matrix4f()
    private var viewProjectionMatrix = Matrix4f()
    private var clipPlanes = ClusterClipPlanes(0.05f, 256f)
    private var shadowFrameState = ShadowFrameState.EMPTY

    private var clusterIndexScratch = IntArray(0)
    private var volumetricTileIndexScratch = IntArray(0)
    private var touchedClusterIndices = IntArray(0)
    private var touchedVolumetricTileIndices = IntArray(0)
    private var touchedClusterCount = 0
    private var touchedVolumetricTileCount = 0

    private var coreLightUploadBuffer: ByteBuffer? = null
    private var pointLightUploadBuffer: ByteBuffer? = null
    private var spotLightUploadBuffer: ByteBuffer? = null
    private var shadowUploadBuffer: ByteBuffer? = null
    private var volumetricUploadBuffer: ByteBuffer? = null
    private var flareUploadBuffer: ByteBuffer? = null
    private var clusterUploadBuffer: ByteBuffer? = null
    private var volumetricTileUploadBuffer: ByteBuffer? = null
    private var visibleLightIndexUploadBuffer: ByteBuffer? = null
    private var tiledCullingPending = false
    private var tiledHasVolumetricLights = false

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        invalidate()
    }

    fun invalidate() {
        if (RenderSystem.isOnRenderThread()) {
            releaseGpuState()
        } else {
            RenderSystem.recordRenderCall { releaseGpuState() }
        }
    }

    fun prepareFrame(event: RenderLevelStageEvent) {
        val level = Minecraft.getInstance().level ?: run {
            clearFrameState()
            return
        }

        val cullingSupport =
            if (IrisHelper.isShaderPackInUse()) IrisHelper.currentLightCullingSupport() else LightCullingSupport(
                direct = false,
                tiled = false,
                clustered = false,
            )
        enabled = cullingSupport.anyEnabled()
        if (!enabled) {
            uploadEmptyState()
            return
        }

        updateFrameMatrices(event)

        val collected = collectLights(level, event.partialTick, event.frustum)
        if (collected.isEmpty()) {
            resetClusterScratch()
            resetVolumetricTileScratch()
            shadowFrameState = ShadowFrameState.EMPTY
            cullingMode = LightCullingMode.DISABLED
            tiledCullingPending = false
            tiledHasVolumetricLights = false
            lightCount = 0
            clusterCount = 0
            volumetricTileCount = 0
            volumetricLightCount = 0
            flareLightCount = 0
            overflowedClusters = 0
            overflowedVolumetricTiles = 0
            uploadPackedState(collected)
            return
        }

        shadowFrameState = LocalLightShadowManager.prepareFrame(event, collected, viewMatrix)
        cullingMode = resolveLightCullingMode(collected.size, cullingSupport)

        val tileCountX = maxOf(1, ceil(viewResolution.x / ClusteredLightingConfig.TILE_SIZE.toFloat()).toInt())
        val tileCountY = maxOf(1, ceil(viewResolution.y / ClusteredLightingConfig.TILE_SIZE.toFloat()).toInt())
        val clusterCountLocal = tileCountX * tileCountY * ClusteredLightingConfig.Z_SLICES
        val tiledClusterCountLocal = tileCountX * tileCountY
        val volumetricTileCountLocal = tileCountX * tileCountY
        val hasVolumetricLights = collected.any { it.component.hasVolumetricFog }
        tiledHasVolumetricLights = hasVolumetricLights
        val clusterStride = if (cullingMode == LightCullingMode.TILED) {
            ClusteredLightingConfig.MAX_LIGHTS_PER_TILE + 1
        } else {
            ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER + 1
        }
        val volumetricStride = ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1

        lightCount = collected.size
        clusterCount = when (cullingMode) {
            LightCullingMode.TILED -> tiledClusterCountLocal
            LightCullingMode.CLUSTERED -> clusterCountLocal
            else -> 0
        }
        volumetricTileCount =
            if (hasVolumetricLights && cullingMode != LightCullingMode.DIRECT) volumetricTileCountLocal else 0
        volumetricLightCount = collected.count { it.component.hasVolumetricFog }
        flareLightCount = collected.count { it.component.hasFlare }

        uploadLightState(collected)

        if (cullingMode == LightCullingMode.DIRECT) {
            resetClusterScratch()
            resetVolumetricTileScratch()
            overflowedClusters = 0
            overflowedVolumetricTiles = 0
            tiledCullingPending = false
            uploadClusterState(EMPTY_INDEX_LIST, EMPTY_INDEX_LIST)
            return
        }

        tiledCullingPending = cullingMode == LightCullingMode.TILED

        val usedGpuCompute = cullingMode == LightCullingMode.CLUSTERED && ClusteredLightCompute.dispatch(
            lightCount = collected.size,
            tileCountX = tileCountX,
            tileCountY = tileCountY,
            clusterCount = clusterCountLocal,
            volumetricTileCount = if (hasVolumetricLights) volumetricTileCountLocal else 0,
            viewResolution = Vector2i(viewResolution),
            projectionMatrix = Matrix4f(projectionMatrix),
            clipPlanes = clipPlanes,
            coreLightBuffer = coreLightBuffer,
            clusterIndexBuffer = clusterIndexBuffer,
            volumetricTileIndexBuffer = volumetricTileIndexBuffer,
        )

        if (usedGpuCompute) {
            overflowedClusters = 0
            overflowedVolumetricTiles = 0
            return
        }

        val clusterList = prepareClusterScratch(clusterCountLocal)
        val volumetricTileList = if (hasVolumetricLights) {
            prepareVolumetricTileScratch(volumetricTileCountLocal)
        } else {
            resetVolumetricTileScratch()
            EMPTY_INDEX_LIST
        }

        overflowedClusters = 0
        overflowedVolumetricTiles = 0

        collected.forEachIndexed { lightIndex, light ->
            val bounds = projectLightBounds(
                viewSpaceCenter = light.viewSpacePosition,
                influenceRadius = light.influenceRadius,
                projectionMatrix = projectionMatrix,
                viewWidth = viewResolution.x,
                viewHeight = viewResolution.y,
                nearPlane = clipPlanes.nearPlane,
                farPlane = clipPlanes.farPlane,
            ) ?: return@forEachIndexed

            val minSlice = if (cullingMode == LightCullingMode.CLUSTERED) bounds.minSlice else 0
            val maxSlice = if (cullingMode == LightCullingMode.CLUSTERED) bounds.maxSlice else 0

            for (slice in minSlice..maxSlice) {
                for (tileY in bounds.minTileY..bounds.maxTileY) {
                    for (tileX in bounds.minTileX..bounds.maxTileX) {
                        val clusterIndex = ((slice * tileCountY) + tileY) * tileCountX + tileX
                        val base = clusterIndex * clusterStride
                        val currentCount = clusterList[base]
                        if (currentCount == 0) {
                            touchedClusterIndices[touchedClusterCount++] = clusterIndex
                        }
                        val maxIndexedLights = if (cullingMode == LightCullingMode.TILED) {
                            ClusteredLightingConfig.MAX_LIGHTS_PER_TILE
                        } else {
                            ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER
                        }
                        if (currentCount < maxIndexedLights) {
                            clusterList[base] = currentCount + 1
                            clusterList[base + currentCount + 1] = lightIndex
                        } else {
                            overflowedClusters++
                        }
                    }
                }
            }

            if (hasVolumetricLights && light.component.hasVolumetricFog) {
                for (tileY in bounds.minTileY..bounds.maxTileY) {
                    for (tileX in bounds.minTileX..bounds.maxTileX) {
                        val tileIndex = tileY * tileCountX + tileX
                        val base = tileIndex * volumetricStride
                        val currentCount = volumetricTileList[base]
                        if (currentCount == 0) {
                            touchedVolumetricTileIndices[touchedVolumetricTileCount++] = tileIndex
                        }
                        if (currentCount < ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE) {
                            volumetricTileList[base] = currentCount + 1
                            volumetricTileList[base + currentCount + 1] = lightIndex
                        } else {
                            overflowedVolumetricTiles++
                        }
                    }
                }
            }
        }

        uploadClusterState(clusterList, if (hasVolumetricLights) volumetricTileList else EMPTY_INDEX_LIST)
    }

    fun dispatchDeferredCulling(event: RenderLevelStageEvent) {
        if (event.stage != ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage.AFTER_CUTOUT_BLOCKS) return
        if (!enabled) return

        val tileCountX = maxOf(1, ceil(viewResolution.x / ClusteredLightingConfig.TILE_SIZE.toFloat()).toInt())
        val tileCountY = maxOf(1, ceil(viewResolution.y / ClusteredLightingConfig.TILE_SIZE.toFloat()).toInt())

        if (cullingMode == LightCullingMode.TILED && tiledCullingPending && lightCount > 0) {
            val usedGpuCompute = TiledLightCompute.dispatch(
                lightCount = lightCount,
                tileCountX = tileCountX,
                tileCountY = tileCountY,
                viewResolution = Vector2i(viewResolution),
                clipPlanes = clipPlanes,
                projectionMatrix = Matrix4f(projectionMatrix),
                visibleLightIndexBuffer = visibleLightIndexBuffer,
                coreLightBuffer = coreLightBuffer,
                pointLightBuffer = pointLightBuffer,
                spotLightBuffer = spotLightBuffer,
                clusterIndexBuffer = clusterIndexBuffer,
                volumetricTileIndexBuffer = volumetricTileIndexBuffer,
                hasVolumetricLights = tiledHasVolumetricLights,
            )

            if (usedGpuCompute) {
                overflowedClusters = 0
                overflowedVolumetricTiles = 0
            }
            tiledCullingPending = false
        }

        dispatchVolumetricFogCompute(tileCountX, tileCountY)
    }

    fun isEnabled(): Boolean = enabled
    fun currentLightCullingMode(): Int = cullingMode.id
    fun isDirectLightingEnabled(): Boolean = cullingMode == LightCullingMode.DIRECT
    fun isTiledLightingEnabled(): Boolean = cullingMode == LightCullingMode.TILED
    fun isClusteredLightingEnabled(): Boolean = cullingMode == LightCullingMode.CLUSTERED
    fun currentLightCount(): Int = lightCount
    fun currentClusterCount(): Int = clusterCount
    fun currentVolumetricTileCount(): Int = volumetricTileCount
    fun currentVolumetricLightCount(): Int = volumetricLightCount
    fun currentFlareLightCount(): Int = flareLightCount
    fun isVolumetricFogComputeEnabled(): Boolean = volumetricFogComputeEnabled
    fun currentVolumetricFogComputeResolution(): Vector2i = Vector2i(volumetricFogComputeResolution)
    fun currentVolumetricFogComputeDownsample(): Int = volumetricFogComputeDownsample
    fun configuredTileSize(): Int = ClusteredLightingConfig.TILE_SIZE
    fun configuredZSlices(): Int =
        if (cullingMode == LightCullingMode.CLUSTERED) ClusteredLightingConfig.Z_SLICES else 1

    fun currentNearPlane(): Float = clipPlanes.nearPlane
    fun currentFarPlane(): Float = clipPlanes.farPlane
    fun currentViewResolution(): Vector2i = Vector2i(viewResolution)

    fun registerDynamicUniforms(uniforms: DynamicUniformHolder) {
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "he_clusteredLightingEnabled", ::isEnabled)
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "he_tiledLightingEnabled", ::isTiledLightingEnabled)
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "he_directLightingEnabled", ::isDirectLightingEnabled)
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "he_localLightsEnabled", ::isEnabled)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_lightCullingMode", ::currentLightCullingMode)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_lightCount", ::currentLightCount)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_clusterCount", ::currentClusterCount)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_volumetricTileCount", ::currentVolumetricTileCount)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_volumetricLightCount", ::currentVolumetricLightCount)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_flareLightCount", ::currentFlareLightCount)
        uniforms.uniform1b(
            UniformUpdateFrequency.PER_FRAME,
            "he_volumetricFogComputeEnabled",
            ::isVolumetricFogComputeEnabled
        )
        uniforms.uniform2i(
            UniformUpdateFrequency.PER_FRAME,
            "he_volumetricFogBufferResolution",
            ::currentVolumetricFogComputeResolution
        )
        uniforms.uniform1i(
            UniformUpdateFrequency.PER_FRAME,
            "he_volumetricFogDownsample",
            ::currentVolumetricFogComputeDownsample
        )
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_tileSize", ::configuredTileSize)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_zSlices", ::configuredZSlices)
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "he_nearPlane", ::currentNearPlane)
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "he_farPlane", ::currentFarPlane)
        uniforms.uniform2i(UniformUpdateFrequency.PER_FRAME, "he_viewResolution", ::currentViewResolution)
        LocalLightShadowManager.registerDynamicUniforms(uniforms)
    }

    fun addCustomSamplers(samplers: SamplerHolder) {
        LocalLightShadowManager.addCustomSamplers(samplers)
    }

    fun addCustomImages(customImages: MutableSet<*>) {
        LocalLightShadowManager.addCustomImages(customImages)
    }

    fun isLocalShadowPassActive(): Boolean = LocalLightShadowManager.isLocalShadowPassActive()

    fun getIrisLocalShadowViewMatrix(): Matrix4f = LocalLightShadowManager.currentShadowViewMatrix()

    fun getIrisLocalShadowProjectionMatrix(): Matrix4f = LocalLightShadowManager.currentShadowProjectionMatrix()

    fun getIrisLocalShadowFramebuffer(): GlFramebuffer? = LocalLightShadowManager.currentIrisShadowFramebuffer()

    fun markLocalShadowWorldChanged() {
        LocalLightShadowManager.markWorldChanged()
    }

    private fun dispatchVolumetricFogCompute(tileCountX: Int, tileCountY: Int) {
        if (!enabled || lightCount <= 0 || volumetricLightCount <= 0) {
            volumetricFogComputeEnabled = false
            volumetricFogComputeResolution.set(1, 1)
            volumetricFogComputeDownsample = 1
            volumetricComputeOutputBuffer.ensureCapacity(4 * Float.SIZE_BYTES)
            return
        }

        val useTileLists = cullingMode != LightCullingMode.DIRECT && volumetricTileCount > 0
        val usedGpuCompute = VolumetricFogCompute.dispatch(
            lightCount = lightCount,
            tileCountX = tileCountX,
            tileCountY = tileCountY,
            viewResolution = Vector2i(viewResolution),
            clipPlanes = clipPlanes,
            viewMatrix = Matrix4f(viewMatrix),
            cameraPosition = Vector3f(cameraPosition),
            projectionMatrix = Matrix4f(projectionMatrix),
            useTileLists = useTileLists,
            coreLightBuffer = coreLightBuffer,
            pointLightBuffer = pointLightBuffer,
            spotLightBuffer = spotLightBuffer,
            volumetricFogBuffer = volumetricFogBuffer,
            volumetricTileIndexBuffer = volumetricTileIndexBuffer,
            outputBuffer = volumetricComputeOutputBuffer,
        )

        volumetricFogComputeEnabled = usedGpuCompute
        if (usedGpuCompute) {
            volumetricFogComputeResolution.set(VolumetricFogCompute.currentFogResolution())
            volumetricFogComputeDownsample = VolumetricFogCompute.currentDownsample()
        } else {
            volumetricFogComputeResolution.set(1, 1)
            volumetricFogComputeDownsample = 1
        }
    }

    private fun updateFrameMatrices(event: RenderLevelStageEvent) {
        val minecraft = Minecraft.getInstance()
        val cameraPos = event.camera.position

        viewResolution = Vector2i(minecraft.window.width, minecraft.window.height)
        cameraPosition = Vector3f(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())
        viewMatrix = IrisHelper.currentGbufferModelViewMatrix()
        projectionMatrix = IrisHelper.currentGbufferProjectionMatrix(event.projectionMatrix)
        viewProjectionMatrix = Matrix4f(projectionMatrix).mul(viewMatrix)
        clipPlanes = extractClipPlanes(projectionMatrix)
    }

    private fun collectLights(
        level: net.minecraft.client.multiplayer.ClientLevel,
        partialTick: Float,
        frustum: Frustum?,
    ): List<PreparedLight> {
        val materialization = MaterializationRuntimeState.service(level)
        val collected = ArrayList<PreparedLight>()

        materialization.forEachLightRecord { record ->
            if ((record.anchor as? EntityAnchor)?.primary == true) return@forEachLightRecord

            with(level.geary) {
                val entity = record.runtimeId.toGeary()
                val pointLight = entity.get<PointLightComponent>()
                val spotLight = entity.get<SpotLightComponent>()
                if (pointLight != null && spotLight != null) {
                    HollowCore.LOGGER.warn(
                        "Skipping anchored light {} because both point and spot components are present",
                        record.stableKey,
                    )
                    return@with
                }

                val component = pointLight ?: spotLight ?: return@with
                if (!component.enabled) return@with

                val transform = entity.get<TransformComponent>() ?: TransformComponent()
                val resolved = resolveAnchoredTransform(level, record.anchor, transform, partialTick) ?: return@with
                val worldPosition = Vector3f(
                    resolved.transform.translation.x,
                    resolved.transform.translation.y,
                    resolved.transform.translation.z,
                )
                val worldSpaceDirection = when (component) {
                    is PointLightComponent -> Vector3f(0f, 0f, 0f)
                    is SpotLightComponent -> spotLightDirection(transform.rotation).toJoml().normalize()
                }
                val cameraRelativePosition = Vector3f(worldPosition).sub(cameraPosition)
                val viewSpacePosition = Vector3f(cameraRelativePosition)
                viewMatrix.transformPosition(viewSpacePosition)

                val influenceRadius = when (component) {
                    is PointLightComponent -> component.radius
                    is SpotLightComponent -> component.distance
                }
                if (frustum != null && !frustum.isVisible(buildLightBounds(worldPosition, influenceRadius))) return@with

                if (viewSpacePosition.z > influenceRadius) return@with
                val flarePosition = if (component.hasFlare) {
                    projectToScreen(cameraRelativePosition, viewProjectionMatrix, viewResolution.x, viewResolution.y)
                } else {
                    null
                }

                collected += PreparedLight(
                    cacheKey = record.stableKey.toString(),
                    component = component,
                    worldPosition = worldPosition,
                    worldSpaceDirection = worldSpaceDirection,
                    viewSpacePosition = viewSpacePosition,
                    viewSpaceDirection = when (component) {
                        is PointLightComponent -> Vector3f(0f, 0f, 0f)
                        is SpotLightComponent -> Vector3f(worldSpaceDirection).apply {
                            viewMatrix.transformDirection(this)
                            normalize()
                        }
                    },
                    influenceRadius = influenceRadius,
                    cameraDistance = cameraRelativePosition.length(),
                    flareScreenPosition = flarePosition,
                )
            }
        }

        return collected
    }

    private fun uploadLightState(lights: List<PreparedLight>) {
        val coreLightBytes =
            prepareUploadBuffer(coreLightUploadBuffer, lights.size * ClusteredLightingConfig.CORE_LIGHT_STRIDE)
        val pointLightBytes =
            prepareUploadBuffer(pointLightUploadBuffer, lights.size * ClusteredLightingConfig.POINT_LIGHT_STRIDE)
        val spotLightBytes =
            prepareUploadBuffer(spotLightUploadBuffer, lights.size * ClusteredLightingConfig.SPOT_LIGHT_STRIDE)
        val shadowBytes =
            prepareUploadBuffer(shadowUploadBuffer, lights.size * ClusteredLightingConfig.SHADOW_SETTINGS_STRIDE)
        val volumetricBytes =
            prepareUploadBuffer(volumetricUploadBuffer, lights.size * ClusteredLightingConfig.VOLUMETRIC_FOG_STRIDE)
        val flareBytes = prepareUploadBuffer(flareUploadBuffer, lights.size * ClusteredLightingConfig.FLARE_STRIDE)
        val visibleLightIndexBytes = prepareUploadBuffer(visibleLightIndexUploadBuffer, lights.size * Int.SIZE_BYTES)

        coreLightUploadBuffer = coreLightBytes
        pointLightUploadBuffer = pointLightBytes
        spotLightUploadBuffer = spotLightBytes
        shadowUploadBuffer = shadowBytes
        volumetricUploadBuffer = volumetricBytes
        flareUploadBuffer = flareBytes
        visibleLightIndexUploadBuffer = visibleLightIndexBytes

        lights.forEachIndexed { index, light ->
            val flags = buildFlags(light.component)

            coreLightBytes.putFloat(light.viewSpacePosition.x)
            coreLightBytes.putFloat(light.viewSpacePosition.y)
            coreLightBytes.putFloat(light.viewSpacePosition.z)
            coreLightBytes.putFloat(light.influenceRadius)
            coreLightBytes.putFloat(light.component.color.r)
            coreLightBytes.putFloat(light.component.color.g)
            coreLightBytes.putFloat(light.component.color.b)
            coreLightBytes.putFloat(light.component.intensity)
            coreLightBytes.putInt(
                when (light.component) {
                    is PointLightComponent -> 0
                    is SpotLightComponent -> 1
                }
            )
            coreLightBytes.putInt(index)
            coreLightBytes.putInt(if (light.component.hasShadow) shadowFrameState.shadowIndexFor(light.cacheKey) else -1)
            coreLightBytes.putInt(flags)

            when (val component = light.component) {
                is PointLightComponent -> {
                    pointLightBytes.putFloat(component.radius)
                    pointLightBytes.putFloat(0f)
                    pointLightBytes.putFloat(0f)
                    pointLightBytes.putFloat(0f)

                    repeat(8) { spotLightBytes.putFloat(0f) }
                }

                is SpotLightComponent -> {
                    pointLightBytes.putFloat(0f)
                    pointLightBytes.putFloat(0f)
                    pointLightBytes.putFloat(0f)
                    pointLightBytes.putFloat(0f)

                    spotLightBytes.putFloat(light.viewSpaceDirection.x)
                    spotLightBytes.putFloat(light.viewSpaceDirection.y)
                    spotLightBytes.putFloat(light.viewSpaceDirection.z)
                    spotLightBytes.putFloat(component.innerAngle)
                    spotLightBytes.putFloat(component.outerAngle)
                    spotLightBytes.putFloat(component.distance)
                    spotLightBytes.putFloat(0f)
                    spotLightBytes.putFloat(0f)
                }
            }

            val shadow = light.component.shadow
            shadowBytes.putFloat(shadow?.shadowDistance ?: 0f)
            shadowBytes.putFloat(shadow?.fovOffset ?: 0f)
            shadowBytes.putFloat(if (shadow?.dynamic == true) 1f else 0f)
            shadowBytes.putFloat(if (shadow?.enabled == true) 1f else 0f)

            val fog = light.component.volumetricFog
            volumetricBytes.putInt(fog?.sampleCount ?: 0)
            volumetricBytes.putFloat(fog?.scattering ?: 0f)
            volumetricBytes.putFloat(fog?.density ?: 0f)
            volumetricBytes.putFloat(fog?.anisotropy ?: 0f)

            val flare = light.component.flare
            flareBytes.putFloat(flare?.sizeOffset ?: 0f)
            flareBytes.putFloat(flare?.falloffDistance ?: 0f)
            flareBytes.putFloat(flare?.startAngle ?: 0f)
            flareBytes.putFloat(flare?.endAngle ?: 0f)
            flareBytes.putFloat(flare?.angleFactorOffset ?: 0f)
            flareBytes.putFloat(flare?.intensity ?: 0f)
            flareBytes.putFloat(light.flareScreenPosition?.x ?: -1f)
            flareBytes.putFloat(light.flareScreenPosition?.y ?: -1f)
            visibleLightIndexBytes.putInt(index)
        }

        coreLightBuffer.upload(coreLightBytes)
        pointLightBuffer.upload(pointLightBytes)
        spotLightBuffer.upload(spotLightBytes)
        shadowSettingsBuffer.upload(shadowBytes)
        volumetricFogBuffer.upload(volumetricBytes)
        flareBuffer.upload(flareBytes)
        visibleLightIndexBuffer.upload(visibleLightIndexBytes)
    }

    private fun uploadClusterState(clusterList: IntArray, volumetricTileList: IntArray) {
        val clusterBytes = prepareUploadBuffer(clusterUploadBuffer, clusterList.size * Int.SIZE_BYTES)
        val volumetricTileBytes =
            prepareUploadBuffer(volumetricTileUploadBuffer, volumetricTileList.size * Int.SIZE_BYTES)
        clusterUploadBuffer = clusterBytes
        volumetricTileUploadBuffer = volumetricTileBytes
        putIntArray(clusterBytes, clusterList)
        putIntArray(volumetricTileBytes, volumetricTileList)
        clusterIndexBuffer.upload(clusterBytes)
        volumetricTileIndexBuffer.upload(volumetricTileBytes)
    }

    private fun uploadPackedState(lights: List<PreparedLight>) {
        uploadLightState(lights)
        uploadClusterState(EMPTY_INDEX_LIST, EMPTY_INDEX_LIST)
    }

    private fun buildFlags(component: LightComponent): Int {
        var flags = 0
        flags = flags or when (component) {
            is PointLightComponent -> FLAG_POINT_LIGHT
            is SpotLightComponent -> FLAG_SPOT_LIGHT
        }
        if (component.hasShadow) flags = flags or FLAG_HAS_SHADOW
        if (component.hasVolumetricFog) flags = flags or FLAG_HAS_VOLUMETRIC_FOG
        if (component.hasFlare) flags = flags or FLAG_HAS_FLARE
        return flags
    }

    private fun uploadEmptyState() {
        clearFrameState()
        resetClusterScratch()
        resetVolumetricTileScratch()
        uploadPackedState(emptyList())
    }

    private fun clearFrameState() {
        enabled = false
        cullingMode = LightCullingMode.DISABLED
        shadowFrameState = ShadowFrameState.EMPTY
        LocalLightShadowManager.resetFrameState()
        tiledCullingPending = false
        tiledHasVolumetricLights = false
        lightCount = 0
        clusterCount = 0
        volumetricTileCount = 0
        volumetricLightCount = 0
        flareLightCount = 0
        overflowedClusters = 0
        overflowedVolumetricTiles = 0
        volumetricFogComputeEnabled = false
        volumetricFogComputeResolution.set(1, 1)
        volumetricFogComputeDownsample = 1
    }

    private fun releaseGpuState() {
        clearFrameState()
        resetClusterScratch()
        resetVolumetricTileScratch()
        coreLightBuffer.release()
        pointLightBuffer.release()
        spotLightBuffer.release()
        shadowSettingsBuffer.release()
        volumetricFogBuffer.release()
        flareBuffer.release()
        clusterIndexBuffer.release()
        volumetricTileIndexBuffer.release()
        visibleLightIndexBuffer.release()
        volumetricComputeOutputBuffer.release()
        ClusteredLightCompute.invalidate()
        TiledLightCompute.invalidate()
        VolumetricFogCompute.invalidate()
        LocalLightShadowManager.invalidate()
    }

    private fun prepareClusterScratch(clusterCountLocal: Int): IntArray {
        val clusterStride = if (cullingMode == LightCullingMode.TILED) {
            ClusteredLightingConfig.MAX_LIGHTS_PER_TILE + 1
        } else {
            ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER + 1
        }
        val requiredSize = clusterCountLocal * clusterStride
        if (clusterIndexScratch.size < requiredSize) {
            clusterIndexScratch = IntArray(requiredSize)
        } else {
            resetClusterScratch()
        }
        if (touchedClusterIndices.size < clusterCountLocal) {
            touchedClusterIndices = IntArray(clusterCountLocal)
        }
        touchedClusterCount = 0
        return clusterIndexScratch
    }

    private fun prepareVolumetricTileScratch(volumetricTileCountLocal: Int): IntArray {
        val volumetricStride = ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1
        val requiredSize = volumetricTileCountLocal * volumetricStride
        if (volumetricTileIndexScratch.size < requiredSize) {
            volumetricTileIndexScratch = IntArray(requiredSize)
        } else {
            resetVolumetricTileScratch()
        }
        if (touchedVolumetricTileIndices.size < volumetricTileCountLocal) {
            touchedVolumetricTileIndices = IntArray(volumetricTileCountLocal)
        }
        touchedVolumetricTileCount = 0
        return volumetricTileIndexScratch
    }

    private fun resetClusterScratch() {
        if (touchedClusterCount == 0) return
        val clusterStride = if (cullingMode == LightCullingMode.TILED) {
            ClusteredLightingConfig.MAX_LIGHTS_PER_TILE + 1
        } else {
            ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER + 1
        }
        for (index in 0 until touchedClusterCount) {
            clusterIndexScratch[touchedClusterIndices[index] * clusterStride] = 0
        }
        touchedClusterCount = 0
    }

    private fun resetVolumetricTileScratch() {
        if (touchedVolumetricTileCount == 0) return
        val volumetricStride = ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1
        for (index in 0 until touchedVolumetricTileCount) {
            volumetricTileIndexScratch[touchedVolumetricTileIndices[index] * volumetricStride] = 0
        }
        touchedVolumetricTileCount = 0
    }

    private fun prepareUploadBuffer(current: ByteBuffer?, requiredBytes: Int): ByteBuffer {
        val capacity = maxOf(requiredBytes, 1)
        val buffer = if (current == null || current.capacity() < capacity) {
            BufferUtils.createByteBuffer(capacity)
        } else {
            current
        }
        buffer.clear()
        return buffer
    }

    private fun putIntArray(buffer: ByteBuffer, values: IntArray) {
        buffer.clear()
        buffer.asIntBuffer().put(values, 0, values.size)
        buffer.position(values.size * Int.SIZE_BYTES)
    }

}

private fun Vec3f.toJoml(): Vector3f = Vector3f(x, y, z)

private fun buildLightBounds(position: Vector3f, radius: Float): AABB {
    val radiusDouble = radius.toDouble()
    return AABB(
        position.x - radiusDouble,
        position.y - radiusDouble,
        position.z - radiusDouble,
        position.x + radiusDouble,
        position.y + radiusDouble,
        position.z + radiusDouble,
    )
}
