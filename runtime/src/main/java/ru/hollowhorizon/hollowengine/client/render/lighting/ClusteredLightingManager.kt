package ru.hollowhorizon.hollowengine.client.render.lighting

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.math.Vec3f
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency
import net.irisshaders.iris.gl.image.GlImage
import net.irisshaders.iris.gl.texture.InternalTextureFormat
import net.irisshaders.iris.gl.texture.PixelFormat
import net.irisshaders.iris.gl.texture.PixelType
import net.irisshaders.iris.gl.texture.TextureType
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GL43
import org.lwjgl.opengl.GL44
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

    private val coreLightBuffer = ShaderStorageBuffer(ClusteredLightingConfig.CORE_LIGHT_BINDING)
    private val pointLightBuffer = ShaderStorageBuffer(ClusteredLightingConfig.POINT_LIGHT_BINDING)
    private val spotLightBuffer = ShaderStorageBuffer(ClusteredLightingConfig.SPOT_LIGHT_BINDING)
    private val shadowSettingsBuffer = ShaderStorageBuffer(ClusteredLightingConfig.SHADOW_SETTINGS_BINDING)
    private val volumetricFogBuffer = ShaderStorageBuffer(ClusteredLightingConfig.VOLUMETRIC_FOG_BINDING)
    private val flareBuffer = ShaderStorageBuffer(ClusteredLightingConfig.FLARE_BINDING)
    private val clusterIndexBuffer = ShaderStorageBuffer(ClusteredLightingConfig.CLUSTER_INDEX_BINDING)
    private val volumetricTileIndexBuffer = ShaderStorageBuffer(ClusteredLightingConfig.VOLUMETRIC_TILE_INDEX_BINDING)

    private var volumetricsImage: GlImage? = null

    private var enabled = false
    private var lightCount = 0
    private var clusterCount = 0
    private var volumetricTileCount = 0
    private var overflowedClusters = 0
    private var overflowedVolumetricTiles = 0

    private var viewResolution = Vector2i()
    private var cameraPosition = Vector3f()
    private var viewMatrix = Matrix4f()
    private var projectionMatrix = Matrix4f()
    private var viewProjectionMatrix = Matrix4f()
    private var viewMatrixInverse = Matrix4f()
    private var projectionMatrixInverse = Matrix4f()
    private var clipPlanes = ClusterClipPlanes(0.05f, 256f)

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

        enabled = IrisHelper.isShaderPackInUse() && IrisHelper.isClusteredLightingCompatible()
        if (!enabled) {
            uploadEmptyState()
            return
        }

        updateFrameMatrices(event)
        ensureVolumetricsImage(viewResolution.x, viewResolution.y)

        val collected = collectLights(level, event.partialTick, event.frustum)
        val tileCountX = maxOf(1, ceil(viewResolution.x / ClusteredLightingConfig.TILE_SIZE.toFloat()).toInt())
        val tileCountY = maxOf(1, ceil(viewResolution.y / ClusteredLightingConfig.TILE_SIZE.toFloat()).toInt())
        val clusterCountLocal = tileCountX * tileCountY * ClusteredLightingConfig.Z_SLICES
        val volumetricTileCountLocal = tileCountX * tileCountY

        val clusterList = IntArray(clusterCountLocal * (ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER + 1))
        val volumetricTileList =
            IntArray(volumetricTileCountLocal * (ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1))

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

            for (slice in bounds.minSlice..bounds.maxSlice) {
                for (tileY in bounds.minTileY..bounds.maxTileY) {
                    for (tileX in bounds.minTileX..bounds.maxTileX) {
                        val clusterIndex = ((slice * tileCountY) + tileY) * tileCountX + tileX
                        val base = clusterIndex * (ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER + 1)
                        val currentCount = clusterList[base]
                        if (currentCount < ClusteredLightingConfig.MAX_LIGHTS_PER_CLUSTER) {
                            clusterList[base] = currentCount + 1
                            clusterList[base + currentCount + 1] = lightIndex
                        } else {
                            overflowedClusters++
                        }
                    }
                }
            }

            if (light.component.hasVolumetricFog || light.component.hasFlare) {
                for (tileY in bounds.minTileY..bounds.maxTileY) {
                    for (tileX in bounds.minTileX..bounds.maxTileX) {
                        val tileIndex = tileY * tileCountX + tileX
                        val base = tileIndex * (ClusteredLightingConfig.MAX_VOLUMETRIC_LIGHTS_PER_TILE + 1)
                        val currentCount = volumetricTileList[base]
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

        clusterCount = clusterCountLocal
        volumetricTileCount = volumetricTileCountLocal
        lightCount = collected.size

        uploadPackedState(collected, clusterList, volumetricTileList)
        clearVolumetricsImage()
    }

    fun isEnabled(): Boolean = enabled
    fun currentLightCount(): Int = lightCount
    fun currentClusterCount(): Int = clusterCount
    fun currentVolumetricTileCount(): Int = volumetricTileCount
    fun configuredTileSize(): Int = ClusteredLightingConfig.TILE_SIZE
    fun configuredZSlices(): Int = ClusteredLightingConfig.Z_SLICES
    fun currentNearPlane(): Float = clipPlanes.nearPlane
    fun currentFarPlane(): Float = clipPlanes.farPlane
    fun currentViewResolution(): Vector2i = Vector2i(viewResolution)
    fun currentCameraPosition(): Vector3f = Vector3f(cameraPosition)
    fun currentViewMatrix(): Matrix4f = Matrix4f(viewMatrix)
    fun currentProjectionMatrix(): Matrix4f = Matrix4f(projectionMatrix)
    fun currentViewMatrixInverse(): Matrix4f = Matrix4f(viewMatrixInverse)
    fun currentProjectionMatrixInverse(): Matrix4f = Matrix4f(projectionMatrixInverse)
    fun volumetricsImageOrNull(): GlImage? = volumetricsImage

    fun registerDynamicUniforms(uniforms: DynamicUniformHolder) {
        uniforms.uniform1b(UniformUpdateFrequency.PER_FRAME, "he_clusteredLightingEnabled", ::isEnabled)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_lightCount", ::currentLightCount)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_clusterCount", ::currentClusterCount)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_volumetricTileCount", ::currentVolumetricTileCount)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_tileSize", ::configuredTileSize)
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_zSlices", ::configuredZSlices)
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "he_nearPlane", ::currentNearPlane)
        uniforms.uniform1f(UniformUpdateFrequency.PER_FRAME, "he_farPlane", ::currentFarPlane)
        uniforms.uniform2i(UniformUpdateFrequency.PER_FRAME, "he_viewResolution", ::currentViewResolution)
        uniforms.uniform3f(UniformUpdateFrequency.PER_FRAME, "he_cameraPosition", ::currentCameraPosition)
        uniforms.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "he_viewMatrix", ::currentViewMatrix)
        uniforms.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "he_projectionMatrix", ::currentProjectionMatrix)
        uniforms.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "he_viewMatrixInverse", ::currentViewMatrixInverse)
        uniforms.uniformMatrix(UniformUpdateFrequency.PER_FRAME, "he_projectionMatrixInverse", ::currentProjectionMatrixInverse)
    }

    fun addCustomImages(customImages: MutableSet<GlImage>) {
        volumetricsImage?.let(customImages::add)
    }

    private fun updateFrameMatrices(event: RenderLevelStageEvent) {
        val minecraft = Minecraft.getInstance()
        val cameraPos = event.camera.position

        viewResolution = Vector2i(minecraft.window.width, minecraft.window.height)
        cameraPosition = Vector3f(cameraPos.x.toFloat(), cameraPos.y.toFloat(), cameraPos.z.toFloat())
        viewMatrix = Matrix4f(event.poseStack.last().pose())
        projectionMatrix = Matrix4f(event.projectionMatrix)
        viewProjectionMatrix = Matrix4f(projectionMatrix).mul(viewMatrix)
        viewMatrixInverse = Matrix4f(viewMatrix).invert()
        projectionMatrixInverse = Matrix4f(projectionMatrix).invert()
        clipPlanes = extractClipPlanes(projectionMatrix)
    }

    private fun collectLights(
        level: net.minecraft.client.multiplayer.ClientLevel,
        partialTick: Float,
        frustum: Frustum?,
    ): List<PreparedLight> {
        val materialization = MaterializationRuntimeState.service(level)
        val collected = ArrayList<PreparedLight>(materialization.records.size)

        materialization.records.forEach { record ->
            if ((record.anchor as? EntityAnchor)?.primary == true) return@forEach

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
                val cameraRelativePosition = Vector3f(worldPosition).sub(cameraPosition)
                val viewSpacePosition = Vector3f(cameraRelativePosition)
                viewMatrix.transformPosition(viewSpacePosition)

                val influenceRadius = when (component) {
                    is PointLightComponent -> component.radius
                    is SpotLightComponent -> component.distance
                }

                if (viewSpacePosition.z > influenceRadius) return@with
                val flarePosition = if (component.hasFlare) {
                    projectToScreen(cameraRelativePosition, viewProjectionMatrix, viewResolution.x, viewResolution.y)
                } else {
                    null
                }

                collected += PreparedLight(
                    stableKey = record.stableKey.toString(),
                    component = component,
                    worldPosition = worldPosition,
                    viewSpacePosition = viewSpacePosition,
                    direction = when (component) {
                        is PointLightComponent -> Vector3f(0f, 0f, 0f)
                        is SpotLightComponent -> spotLightDirection(transform.rotation).toJoml()
                    },
                    influenceRadius = influenceRadius,
                    flareScreenPosition = flarePosition,
                )
            }
        }

        return collected
    }

    private fun uploadPackedState(
        lights: List<PreparedLight>,
        clusterList: IntArray,
        volumetricTileList: IntArray,
    ) {
        val coreLightBytes = BufferUtils.createByteBuffer(lights.size * ClusteredLightingConfig.CORE_LIGHT_STRIDE)
        val pointLightBytes = BufferUtils.createByteBuffer(lights.size * ClusteredLightingConfig.POINT_LIGHT_STRIDE)
        val spotLightBytes = BufferUtils.createByteBuffer(lights.size * ClusteredLightingConfig.SPOT_LIGHT_STRIDE)
        val shadowBytes = BufferUtils.createByteBuffer(lights.size * ClusteredLightingConfig.SHADOW_SETTINGS_STRIDE)
        val volumetricBytes = BufferUtils.createByteBuffer(lights.size * ClusteredLightingConfig.VOLUMETRIC_FOG_STRIDE)
        val flareBytes = BufferUtils.createByteBuffer(lights.size * ClusteredLightingConfig.FLARE_STRIDE)
        val clusterBytes = BufferUtils.createByteBuffer(clusterList.size * Int.SIZE_BYTES)
        val volumetricTileBytes = BufferUtils.createByteBuffer(volumetricTileList.size * Int.SIZE_BYTES)

        lights.forEachIndexed { index, light ->
            val flags = buildFlags(light.component)

            coreLightBytes.putFloat(light.worldPosition.x)
            coreLightBytes.putFloat(light.worldPosition.y)
            coreLightBytes.putFloat(light.worldPosition.z)
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
            coreLightBytes.putInt(if (light.component.hasShadow) index else -1)
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

                    spotLightBytes.putFloat(light.direction.x)
                    spotLightBytes.putFloat(light.direction.y)
                    spotLightBytes.putFloat(light.direction.z)
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
        }

        clusterList.forEach(clusterBytes::putInt)
        volumetricTileList.forEach(volumetricTileBytes::putInt)

        coreLightBuffer.upload(coreLightBytes)
        pointLightBuffer.upload(pointLightBytes)
        spotLightBuffer.upload(spotLightBytes)
        shadowSettingsBuffer.upload(shadowBytes)
        volumetricFogBuffer.upload(volumetricBytes)
        flareBuffer.upload(flareBytes)
        clusterIndexBuffer.upload(clusterBytes)
        volumetricTileIndexBuffer.upload(volumetricTileBytes)
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

    private fun clearVolumetricsImage() {
        val image = volumetricsImage ?: return
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, image.id)
        val zeroPixel = BufferUtils.createByteBuffer(4 * java.lang.Float.BYTES)
        zeroPixel.putFloat(0f).putFloat(0f).putFloat(0f).putFloat(0f).flip()
        GL44.glClearTexImage(image.id, 0, GL11.GL_RGBA, GL11.GL_FLOAT, zeroPixel)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
    }

    private fun ensureVolumetricsImage(width: Int, height: Int) {
        val targetWidth = maxOf(1, width)
        val targetHeight = maxOf(1, height)
        val existing = volumetricsImage
        if (existing == null) {
            volumetricsImage = GlImage(
                "heVolumetricsImg",
                "heVolumetrics",
                TextureType.TEXTURE_2D,
                PixelFormat.RGBA,
                InternalTextureFormat.RGBA16F,
                PixelType.FLOAT,
                false,
                targetWidth,
                targetHeight,
                1,
            )
            return
        }

        existing.updateNewSize(targetWidth, targetHeight)
    }

    private fun uploadEmptyState() {
        clearFrameState()
        ensureVolumetricsImage(1, 1)
        coreLightBuffer.upload(BufferUtils.createByteBuffer(0))
        pointLightBuffer.upload(BufferUtils.createByteBuffer(0))
        spotLightBuffer.upload(BufferUtils.createByteBuffer(0))
        shadowSettingsBuffer.upload(BufferUtils.createByteBuffer(0))
        volumetricFogBuffer.upload(BufferUtils.createByteBuffer(0))
        flareBuffer.upload(BufferUtils.createByteBuffer(0))
        clusterIndexBuffer.upload(BufferUtils.createByteBuffer(Int.SIZE_BYTES))
        volumetricTileIndexBuffer.upload(BufferUtils.createByteBuffer(Int.SIZE_BYTES))
        clearVolumetricsImage()
    }

    private fun clearFrameState() {
        enabled = false
        lightCount = 0
        clusterCount = 0
        volumetricTileCount = 0
        overflowedClusters = 0
        overflowedVolumetricTiles = 0
    }

    private fun releaseGpuState() {
        clearFrameState()
        coreLightBuffer.release()
        pointLightBuffer.release()
        spotLightBuffer.release()
        shadowSettingsBuffer.release()
        volumetricFogBuffer.release()
        flareBuffer.release()
        clusterIndexBuffer.release()
        volumetricTileIndexBuffer.release()
        volumetricsImage?.destroy()
        volumetricsImage = null
    }

    private data class PreparedLight(
        val stableKey: String,
        val component: LightComponent,
        val worldPosition: Vector3f,
        val viewSpacePosition: Vector3f,
        val direction: Vector3f,
        val influenceRadius: Float,
        val flareScreenPosition: ScreenSpaceLightPosition?,
    )

    private class ShaderStorageBuffer(private val binding: Int) {
        private var id: Int = 0

        fun upload(data: ByteBuffer) {
            ensureCreated()
            data.flip()
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id)
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, data, GL15.GL_DYNAMIC_DRAW)
            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, binding, id)
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0)
        }

        fun release() {
            if (id == 0) return
            GL15.glDeleteBuffers(id)
            id = 0
        }

        private fun ensureCreated() {
            if (id == 0) {
                id = GL15.glGenBuffers()
            }
        }
    }
}

private fun Vec3f.toJoml(): Vector3f = Vector3f(x, y, z)
