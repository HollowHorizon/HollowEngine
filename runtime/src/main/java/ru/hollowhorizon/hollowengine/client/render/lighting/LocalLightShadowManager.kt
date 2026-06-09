package ru.hollowhorizon.hollowengine.client.render.lighting

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexSorting
import net.irisshaders.batchedentityrendering.impl.DrawCallTrackingRenderBuffers
import net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource
import net.irisshaders.batchedentityrendering.impl.RenderBuffersExt
import net.irisshaders.iris.gl.IrisRenderSystem
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer
import net.irisshaders.iris.gl.sampler.GlSampler
import net.irisshaders.iris.gl.sampler.SamplerHolder
import net.irisshaders.iris.gl.state.ValueUpdateNotifier
import net.irisshaders.iris.gl.texture.TextureType
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency
import net.irisshaders.iris.mixin.LevelRendererAccessor
import net.irisshaders.iris.shadows.CullingDataCache
import net.irisshaders.iris.shadows.ShadowRenderer
import net.irisshaders.iris.shadows.frustum.BoxCuller
import net.irisshaders.iris.shadows.frustum.fallback.BoxCullingFrustum
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderBuffers
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import org.joml.Matrix4f
import org.joml.Vector2i
import org.joml.Vector3f
import org.joml.Vector4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11C
import org.lwjgl.opengl.GL12C
import org.lwjgl.opengl.GL30C
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.bridge.mixins.client.LevelRendererInvoker
import ru.hollowhorizon.hollowengine.client.render.IrisRenderManager
import ru.hollowhorizon.hollowengine.client.utils.popPose
import ru.hollowhorizon.hollowengine.client.utils.pushPose
import ru.hollowhorizon.hollowengine.client.utils.setIdentity
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.RequireMod
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.geary.components.LightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.PointLightComponent
import ru.hollowhorizon.hollowengine.common.geary.components.SpotLightComponent
import kotlin.math.abs
import kotlin.math.max

internal data class PreparedLight(
    val cacheKey: String,
    val component: LightComponent,
    val worldPosition: Vector3f,
    val worldSpaceDirection: Vector3f,
    val viewSpacePosition: Vector3f,
    val viewSpaceDirection: Vector3f,
    val influenceRadius: Float,
    val cameraDistance: Float,
    val flareScreenPosition: ScreenSpaceLightPosition?,
)

internal data class ShadowFrameState(
    val shadowIndexByKey: Map<String, Int>,
    val shadowCount: Int,
) {
    fun shadowIndexFor(cacheKey: String): Int = shadowIndexByKey[cacheKey] ?: -1

    companion object {
        val EMPTY = ShadowFrameState(emptyMap(), 0)
    }
}

@ClientOnly
internal object LocalLightShadowManager {
    private const val MAX_POINT_FACES = 6
    private const val SHADOW_RECORD_STRIDE = 512
    private const val MATRIX_BYTES = 16 * Float.SIZE_BYTES
    private const val SPOT_SAMPLER_NAME = "he_spotShadowAtlas"
    private const val POINT_SAMPLER_NAME = "he_pointShadowAtlas"
    private const val USE_LINEAR_SHADOW_FILTERING = true

    private val shadowAtlasFilter = if (USE_LINEAR_SHADOW_FILTERING) GL11C.GL_LINEAR else GL11C.GL_NEAREST

    private val shadowBuffer = ShaderStorageBuffer(ClusteredLightingConfig.SHADOW_DATA_BINDING)
    private val spotAtlas = ShadowAtlas(
        name = "hollowengine_local_spot_shadows",
        width = ClusteredLightingConfig.SPOT_SHADOW_ATLAS_SIZE,
        height = ClusteredLightingConfig.SPOT_SHADOW_ATLAS_SIZE,
        slotWidth = ClusteredLightingConfig.SPOT_SHADOW_TILE_SIZE,
        slotHeight = ClusteredLightingConfig.SPOT_SHADOW_TILE_SIZE,
        faceWidth = ClusteredLightingConfig.SPOT_SHADOW_TILE_SIZE,
        faceHeight = ClusteredLightingConfig.SPOT_SHADOW_TILE_SIZE,
        columnsPerSlot = 1,
    )
    private val pointAtlas = ShadowAtlas(
        name = "hollowengine_local_point_shadows",
        width = ClusteredLightingConfig.POINT_SHADOW_ATLAS_WIDTH,
        height = ClusteredLightingConfig.POINT_SHADOW_ATLAS_HEIGHT,
        slotWidth = ClusteredLightingConfig.POINT_SHADOW_FACE_SIZE * 3,
        slotHeight = ClusteredLightingConfig.POINT_SHADOW_FACE_SIZE * 2,
        faceWidth = ClusteredLightingConfig.POINT_SHADOW_FACE_SIZE,
        faceHeight = ClusteredLightingConfig.POINT_SHADOW_FACE_SIZE,
        columnsPerSlot = 3,
    )

    private val cacheEntries = LinkedHashMap<String, ShadowCacheEntry>()
    private var shadowUploadBuffer = BufferUtils.createByteBuffer(1)
    private var shadowSampler: GlSampler? = null
    private var localShadowRenderBuffers: RenderBuffers? = null

    private var frameIndex = 0L
    private var worldMutationStamp = 1L
    private var shadowLightCount = 0
    private var spotAtlasResolution = Vector2i(spotAtlas.width, spotAtlas.height)
    private var pointAtlasResolution = Vector2i(pointAtlas.width, pointAtlas.height)
    private var localShadowPassActive = false
    private var activeIrisShadowFramebuffer: GlFramebuffer? = null
    private val currentShadowViewMatrix = Matrix4f()
    private val currentShadowProjectionMatrix = Matrix4f()
    private val currentShadowViewMatrixInverse = Matrix4f()
    private val currentShadowProjectionMatrixInverse = Matrix4f()
    private val reusableOrigin = Vector3f()
    private val localShadowMatrixNotifier = object : ValueUpdateNotifier {
        private var listener: Runnable? = null

        override fun setListener(listener: Runnable?) {
            this.listener = listener
        }

        fun notifyChanged() {
            listener?.run()
        }
    }

    @SubscribeEvent
    @RequireMod("iris")
    fun onNeighborNotify(@Suppress("UNUSED_PARAMETER") event: BlockEvent.NeighborNotify) {
        worldMutationStamp++
    }

    @SubscribeEvent
    @RequireMod("iris")
    fun onBlockPlaced(event: BlockEvent.Placed) {
        if (event.player.level().isClientSide) {
            worldMutationStamp++
        }
    }

    @SubscribeEvent
    @RequireMod("iris")
    fun onBlockBreak(event: BlockEvent.Break) {
        if (event.level.isClientSide) {
            worldMutationStamp++
        }
    }

    internal fun prepareFrame(event: RenderLevelStageEvent, lights: List<PreparedLight>, @Suppress("UNUSED_PARAMETER") currentViewMatrix: Matrix4f): ShadowFrameState {
        frameIndex++
        if (lights.isEmpty()) {
            shadowLightCount = 0
            uploadShadowState(emptyList())
            return ShadowFrameState.EMPTY
        }

        ensureResourcesCreated()

        val selected = selectLights(lights)
        if (selected.isEmpty()) {
            shadowLightCount = 0
            uploadShadowState(emptyList())
            return ShadowFrameState.EMPTY
        }

        val selectedKeys = selected.mapTo(HashSet(selected.size)) { it.cacheKey }
        val required = ArrayList<ShadowWorkItem>(selected.size)
        var staticRefreshBudget = ClusteredLightingConfig.STATIC_SHADOW_UPDATES_PER_FRAME

        selected.forEach { light ->
            val entry = getOrCreateEntry(light) ?: return@forEach
            entry.selectedFrame = frameIndex

            val currentSignature = shadowSignature(light)
            when (shadowUpdateMode(light, entry, currentSignature)) {
                ShadowUpdateMode.FORCE -> required += ShadowWorkItem(light, entry, currentSignature)
                ShadowUpdateMode.REFRESH -> if (staticRefreshBudget > 0) {
                    staticRefreshBudget--
                    required += ShadowWorkItem(light, entry, currentSignature)
                }
                ShadowUpdateMode.SKIP -> Unit
            }
        }

        required.forEach { work ->
            if (!renderEntry(event, work.light, work.entry)) {
                work.entry.valid = false
            } else {
                work.entry.signature = work.signature
            }
        }

        val activeEntries = ArrayList<ShadowCacheEntry>(selected.size)
        val shadowIndices = HashMap<String, Int>(selected.size)
        selected.forEach { light ->
            val entry = cacheEntries[light.cacheKey] ?: return@forEach
            if (!entry.valid || entry.selectedFrame != frameIndex) return@forEach

            shadowIndices[light.cacheKey] = activeEntries.size
            activeEntries += entry
        }

        shadowLightCount = activeEntries.size
        uploadShadowState(activeEntries)

        evictUnusedEntries(selectedKeys)

        return if (shadowIndices.isEmpty()) ShadowFrameState.EMPTY else ShadowFrameState(shadowIndices, activeEntries.size)
    }

    internal fun registerDynamicUniforms(uniforms: DynamicUniformHolder) {
        uniforms.uniform1i(UniformUpdateFrequency.PER_FRAME, "he_shadowLightCount") { shadowLightCount }
        uniforms.uniform2i(UniformUpdateFrequency.PER_FRAME, "he_spotShadowAtlasResolution") { Vector2i(spotAtlasResolution) }
        uniforms.uniform2i(UniformUpdateFrequency.PER_FRAME, "he_pointShadowAtlasResolution") { Vector2i(pointAtlasResolution) }
        uniforms.uniform1i("he_localShadowPassToken", { if (localShadowPassActive) 1 else 0 }, localShadowMatrixNotifier)
        uniforms.uniformMatrix("he_localShadowViewMatrix", { Matrix4f(currentShadowViewMatrix) }, localShadowMatrixNotifier)
        uniforms.uniformMatrix("he_localShadowProjectionMatrix", { Matrix4f(currentShadowProjectionMatrix) }, localShadowMatrixNotifier)
        uniforms.uniformMatrix("he_localShadowViewMatrixInverse", { Matrix4f(currentShadowViewMatrixInverse) }, localShadowMatrixNotifier)
        uniforms.uniformMatrix("he_localShadowProjectionMatrixInverse", { Matrix4f(currentShadowProjectionMatrixInverse) }, localShadowMatrixNotifier)
    }

    internal fun bindShadowDataBuffer() {
        shadowBuffer.bindBase()
    }

    internal fun currentShadowLightCount(): Int = shadowLightCount

    internal fun currentSpotShadowAtlasResolution(): Vector2i = Vector2i(spotAtlasResolution)

    internal fun currentPointShadowAtlasResolution(): Vector2i = Vector2i(pointAtlasResolution)

    internal fun currentSpotShadowAtlasTextureId(): Int {
        ensureResourcesCreated()
        return spotAtlas.textureId()
    }

    internal fun currentPointShadowAtlasTextureId(): Int {
        ensureResourcesCreated()
        return pointAtlas.textureId()
    }

    internal fun addCustomSamplers(samplers: SamplerHolder) {
        ensureResourcesCreated()
        val sampler = shadowSampler ?: return

        samplers.addDynamicSampler(TextureType.TEXTURE_2D, spotAtlas::textureId, sampler, SPOT_SAMPLER_NAME)
        samplers.addDynamicSampler(TextureType.TEXTURE_2D, pointAtlas::textureId, sampler, POINT_SAMPLER_NAME)
    }

    internal fun addCustomImages(@Suppress("UNUSED_PARAMETER") customImages: MutableSet<*>) = Unit

    internal fun resetFrameState() {
        shadowLightCount = 0
        uploadShadowState(emptyList())
    }

    internal fun invalidate() {
        releaseGpuState()
        worldMutationStamp++
    }

    internal fun isLocalShadowPassActive(): Boolean = localShadowPassActive

    internal fun currentIrisShadowFramebuffer(): GlFramebuffer? = activeIrisShadowFramebuffer

    internal fun currentShadowViewMatrix(): Matrix4f = Matrix4f(currentShadowViewMatrix)

    internal fun currentShadowProjectionMatrix(): Matrix4f = Matrix4f(currentShadowProjectionMatrix)

    internal fun markWorldChanged() {
        worldMutationStamp++
    }

    private fun ensureResourcesCreated() {
        if (shadowSampler == null) {
            shadowSampler = GlSampler(USE_LINEAR_SHADOW_FILTERING, false, true, true)
        }
        if (localShadowRenderBuffers == null) {
            val processors = Runtime.getRuntime().availableProcessors()
            localShadowRenderBuffers = RenderBuffers(processors)
        }

        spotAtlas.ensureCreated()
        pointAtlas.ensureCreated()
        spotAtlasResolution = Vector2i(spotAtlas.width, spotAtlas.height)
        pointAtlasResolution = Vector2i(pointAtlas.width, pointAtlas.height)
    }

    private fun releaseGpuState() {
        shadowLightCount = 0
        shadowBuffer.release()
        spotAtlas.release()
        pointAtlas.release()
        shadowSampler?.destroy()
        shadowSampler = null
        localShadowRenderBuffers = null
        cacheEntries.clear()
    }

    private fun selectLights(lights: List<PreparedLight>): List<PreparedLight> {
        val spotLights = ArrayList<PreparedLight>()
        val pointLights = ArrayList<PreparedLight>()

        lights.asSequence()
            .filter { it.component.hasShadow }
            .filter { candidate ->
                val settings = candidate.component.shadow ?: return@filter false
                settings.shadowDistance <= 0f || candidate.cameraDistance <= settings.shadowDistance
            }
            .sortedByDescending(::importanceScore)
            .forEach { light ->
                when (light.component) {
                    is PointLightComponent -> if (pointLights.size < ClusteredLightingConfig.MAX_POINT_SHADOW_LIGHTS) pointLights += light
                    is SpotLightComponent -> if (spotLights.size < ClusteredLightingConfig.MAX_SPOT_SHADOW_LIGHTS) spotLights += light
                }
            }

        return buildList(pointLights.size + spotLights.size) {
            addAll(pointLights)
            addAll(spotLights)
        }
    }

    private fun getOrCreateEntry(light: PreparedLight): ShadowCacheEntry? {
        val desiredType = shadowTypeOf(light.component)
        val existing = cacheEntries[light.cacheKey]
        if (existing != null && existing.shadowType == desiredType && existing.slot != -1) return existing

        existing?.release()
        cacheEntries.remove(light.cacheKey)

        val entry = ShadowCacheEntry(light.cacheKey, desiredType)
        val allocated = when (desiredType) {
            ShadowType.SPOT -> allocateSlot(spotAtlas, desiredType)
            ShadowType.POINT -> allocateSlot(pointAtlas, desiredType)
        } ?: return null

        entry.slot = allocated
        cacheEntries[light.cacheKey] = entry
        return entry
    }

    private fun allocateSlot(atlas: ShadowAtlas, shadowType: ShadowType): Int? {
        atlas.allocateSlot()?.let { return it }

        val victimKey = cacheEntries.entries
            .asSequence()
            .filter { it.value.shadowType == shadowType && it.value.selectedFrame != frameIndex }
            .sortedBy { it.value.selectedFrame }
            .map { it.key }
            .firstOrNull()
            ?: return null

        val victim = cacheEntries.remove(victimKey) ?: return null
        victim.release()
        return atlas.allocateSlot()
    }

    private fun renderEntry(event: RenderLevelStageEvent, light: PreparedLight, entry: ShadowCacheEntry): Boolean {
        val minecraft = Minecraft.getInstance()
        minecraft.level ?: return false
        val renderer = event.renderer as? LevelRendererAccessor ?: return false
        val rendererInvoker = event.renderer as? LevelRendererInvoker ?: return false

        val atlas = when (entry.shadowType) {
            ShadowType.SPOT -> spotAtlas
            ShadowType.POINT -> pointAtlas
        }

        val faceCount = when (entry.shadowType) {
            ShadowType.SPOT -> 1
            ShadowType.POINT -> MAX_POINT_FACES
        }

        val originalCull = minecraft.smartCull
        val regenerateClouds = renderer.shouldRegenerateClouds()
        val cullingCache = event.renderer as? CullingDataCache
        val previousShadowPassActive = ShadowRenderer.ACTIVE
        val lightWorldX = light.worldPosition.x.toDouble()
        val lightWorldY = light.worldPosition.y.toDouble()
        val lightWorldZ = light.worldPosition.z.toDouble()
        val lightCameraPosition = net.minecraft.world.phys.Vec3(lightWorldX, lightWorldY, lightWorldZ)
        val originalRenderBuffers = renderer.renderBuffers
        val processors = Runtime.getRuntime().availableProcessors()
        val shadowRenderBuffers = localShadowRenderBuffers ?: RenderBuffers(processors).also { localShadowRenderBuffers = it }

        atlas.bind()
        GL11C.glEnable(GL11C.GL_SCISSOR_TEST)
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        RenderSystem.disableBlend()
        RenderSystem.disableCull()
        RenderSystem.colorMask(false, false, false, false)
        cullingCache?.saveState()

        try {
            localShadowPassActive = true
            localShadowMatrixNotifier.notifyChanged()
            activeIrisShadowFramebuffer = atlas.irisFramebuffer()
            ShadowRenderer.ACTIVE = true
            minecraft.smartCull = false
            renderer.renderBuffers = shadowRenderBuffers
            rendererInvoker.`hollowengine$needsUpdate`()
            (shadowRenderBuffers as? DrawCallTrackingRenderBuffers)?.resetDrawCounts()
            (shadowRenderBuffers as? RenderBuffersExt)?.beginLevelRendering()
            entry.farPlane = when (val component = light.component) {
                is PointLightComponent -> max(component.radius, 0.5f)
                is SpotLightComponent -> max(component.distance, 0.5f)
            }
            entry.worldPosition.set(light.worldPosition)

            for (face in 0 until faceCount) {
                val viewport = atlas.viewport(entry.slot, face)
                atlas.bind()
                atlas.clearDepth(viewport.x, viewport.y, viewport.width, viewport.height)

                val renderMatrices = when (entry.shadowType) {
                    ShadowType.SPOT -> createSpotMatrices(light)
                    ShadowType.POINT -> createPointMatrices(light, face)
                }
                updateActiveShadowMatrices(renderMatrices)

                entry.localViewProjection[face].set(renderMatrices.localViewProjection)
                entry.tileData[face].set(viewport.biasX, viewport.biasY, viewport.scaleX, viewport.scaleY)

                val frustum = when (entry.shadowType) {
                    ShadowType.SPOT -> Frustum(Matrix4f(renderMatrices.fullViewMatrix), Matrix4f(renderMatrices.projectionMatrix)).apply {
                        prepare(lightWorldX, lightWorldY, lightWorldZ)
                    }
                    ShadowType.POINT -> BoxCullingFrustum(BoxCuller(renderMatrices.farPlane.toDouble())).apply {
                        prepare(lightWorldX, lightWorldY, lightWorldZ)
                    }
                }

                renderer.setShouldRegenerateClouds(regenerateClouds)
                ShadowRenderer.renderDistance = (entry.farPlane / 16f).toInt().coerceAtLeast(1)
                renderer.invokeSetupRender(event.camera, frustum, false, false)

                val stack = RenderSystem.getModelViewStack()
                stack.pushPose()
                stack.setIdentity()
                stack.mul(renderMatrices.fullViewMatrix)

                try {
                    IrisRenderSystem.setShadowProjection(renderMatrices.projectionMatrix)
                    RenderSystem.setProjectionMatrix(renderMatrices.projectionMatrix, VertexSorting.ORTHOGRAPHIC_Z)
                    GL11C.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
                    GL11C.glScissor(viewport.x, viewport.y, viewport.width, viewport.height)

                    renderer.invokeRenderSectionLayer(
                        RenderType.solid(),
                        lightWorldX,
                        lightWorldY,
                        lightWorldZ,
                        stack,
                        renderMatrices.projectionMatrix,
                    )
                    renderer.invokeRenderSectionLayer(
                        RenderType.cutout(),
                        lightWorldX,
                        lightWorldY,
                        lightWorldZ,
                        stack,
                        renderMatrices.projectionMatrix,
                    )
                    renderer.invokeRenderSectionLayer(
                        RenderType.cutoutMipped(),
                        lightWorldX,
                        lightWorldY,
                        lightWorldZ,
                        stack,
                        renderMatrices.projectionMatrix,
                    )
                    atlas.bind()
                    GL11C.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
                    GL11C.glScissor(viewport.x, viewport.y, viewport.width, viewport.height)
                    IrisRenderManager.renderLocalShadowCasters(
                        renderer = renderer,
                        modelView = PoseStack().apply { mulPose(stack) },
                        cameraPosition = lightCameraPosition,
                        partialTick = event.partialTick,
                        frustum = frustum,
                    )
                    atlas.bind()
                    GL11C.glViewport(viewport.x, viewport.y, viewport.width, viewport.height)
                    GL11C.glScissor(viewport.x, viewport.y, viewport.width, viewport.height)
                    (shadowRenderBuffers.bufferSource() as? FullyBufferedMultiBufferSource)?.readyUp()
                    shadowRenderBuffers.bufferSource().endBatch()
                } finally {
                    stack.popPose()
                    IrisRenderSystem.restorePlayerProjection()
                }
            }
        } catch (t: Throwable) {
            HollowCore.LOGGER.error("Failed to render local shadow cache entry {}", light.cacheKey, t)
            return false
        } finally {
            if (event.frustum != null) {
                renderer.setShouldRegenerateClouds(regenerateClouds)
                renderer.invokeSetupRender(event.camera, event.frustum, false, false)
            }
            cullingCache?.restoreState()
            minecraft.smartCull = originalCull
            renderer.setShouldRegenerateClouds(regenerateClouds)
            (shadowRenderBuffers as? RenderBuffersExt)?.endLevelRendering()
            renderer.renderBuffers = originalRenderBuffers
            ShadowRenderer.ACTIVE = previousShadowPassActive
            activeIrisShadowFramebuffer = null
            localShadowPassActive = false
            localShadowMatrixNotifier.notifyChanged()
            RenderSystem.colorMask(true, true, true, true)
            RenderSystem.enableCull()
            GL11C.glDisable(GL11C.GL_SCISSOR_TEST)
            minecraft.mainRenderTarget.bindWrite(false)
            RenderSystem.viewport(0, 0, minecraft.window.width, minecraft.window.height)
        }

        entry.valid = true
        entry.lastWorldMutation = worldMutationStamp
        entry.lastRenderFrame = frameIndex
        return true
    }

    private fun uploadShadowState(entries: List<ShadowCacheEntry>) {
        val requiredBytes = max(entries.size * SHADOW_RECORD_STRIDE, 1)
        val buffer = if (shadowUploadBuffer.capacity() < requiredBytes) {
            BufferUtils.createByteBuffer(requiredBytes)
        } else {
            shadowUploadBuffer.clear()
            shadowUploadBuffer
        }
        shadowUploadBuffer = buffer

        entries.forEach { entry ->
            buffer.putFloat(if (entry.valid) 1f else 0f)
            buffer.putFloat(if (entry.shadowType == ShadowType.POINT) 1f else 0f)
            buffer.putFloat(if (entry.shadowType == ShadowType.POINT) MAX_POINT_FACES.toFloat() else 1f)
            buffer.putFloat(entry.farPlane)

            buffer.putFloat(entry.worldPosition.x)
            buffer.putFloat(entry.worldPosition.y)
            buffer.putFloat(entry.worldPosition.z)
            buffer.putFloat(0f)

            repeat(MAX_POINT_FACES) { face ->
                val matrixByteOffset = buffer.position()
                entry.localViewProjection[face].get(matrixByteOffset, buffer)
                buffer.position(matrixByteOffset + MATRIX_BYTES)
            }
            repeat(MAX_POINT_FACES) { face ->
                val tile = entry.tileData[face]
                buffer.putFloat(tile.x)
                buffer.putFloat(tile.y)
                buffer.putFloat(tile.z)
                buffer.putFloat(tile.w)
            }
        }

        shadowBuffer.upload(buffer)
    }

    private fun evictUnusedEntries(selectedKeys: Set<String>) {
        cacheEntries.values.removeIf { entry ->
            if (entry.selectedFrame == frameIndex || selectedKeys.contains(entry.key)) {
                false
            } else {
                if (frameIndex - entry.selectedFrame > 600L) {
                    entry.release()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun importanceScore(light: PreparedLight): Float {
        val intensityWeight = max(light.component.intensity, 0.25f)
        val radiusWeight = max(light.influenceRadius, 1f)
        val distanceWeight = max(light.cameraDistance, 1f)
        val dynamicBonus = if (light.component.shadow?.dynamic == true) 4f else 1f
        return dynamicBonus * intensityWeight * radiusWeight / distanceWeight
    }

    private fun shadowSignature(light: PreparedLight): Long {
        var hash = 1125899906842597L
        hash = mixHash(hash, light.worldPosition.x)
        hash = mixHash(hash, light.worldPosition.y)
        hash = mixHash(hash, light.worldPosition.z)
        hash = mixHash(hash, light.worldSpaceDirection.x)
        hash = mixHash(hash, light.worldSpaceDirection.y)
        hash = mixHash(hash, light.worldSpaceDirection.z)
        hash = mixHash(hash, light.influenceRadius)
        hash = mixHash(hash, light.component.intensity)
        hash = mixHash(hash, light.component.color.r)
        hash = mixHash(hash, light.component.color.g)
        hash = mixHash(hash, light.component.color.b)
        val shadow = light.component.shadow
        hash = mixHash(hash, shadow?.shadowDistance ?: 0f)
        hash = mixHash(hash, shadow?.fovOffset ?: 0f)
        hash = hash * 31 + if (shadow?.dynamic == true) 1 else 0
        when (val component = light.component) {
            is PointLightComponent -> {
                hash = mixHash(hash, component.radius)
            }
            is SpotLightComponent -> {
                hash = mixHash(hash, component.innerAngle)
                hash = mixHash(hash, component.outerAngle)
                hash = mixHash(hash, component.distance)
            }
        }
        return hash
    }

    private fun shadowUpdateMode(light: PreparedLight, entry: ShadowCacheEntry, currentSignature: Long): ShadowUpdateMode {
        if (!entry.valid || entry.slot == -1) return ShadowUpdateMode.FORCE
        if (entry.signature != currentSignature) return ShadowUpdateMode.FORCE
        if (light.component.shadow?.dynamic == true) return ShadowUpdateMode.FORCE
        if (entry.lastWorldMutation != worldMutationStamp) return ShadowUpdateMode.FORCE
        if (frameIndex - entry.lastRenderFrame >= ClusteredLightingConfig.STATIC_SHADOW_REFRESH_INTERVAL_FRAMES) {
            return ShadowUpdateMode.REFRESH
        }
        return ShadowUpdateMode.SKIP
    }

    private fun createSpotMatrices(light: PreparedLight): ShadowRenderMatrices {
        val component = light.component as SpotLightComponent
        val worldDirection = Vector3f(light.worldSpaceDirection).normalize()
        val farPlane = max(component.distance, 0.5f)
        val fov = (component.outerAngle + (component.shadow?.fovOffset ?: 0f))
            .coerceIn(1f, 175f)

        val projection = Matrix4f().perspective(Math.toRadians(fov.toDouble()).toFloat(), 1f, 0.05f, farPlane)
        val view = lookAt(reusableOrigin, worldDirection, upVector(worldDirection))

        return ShadowRenderMatrices(
            direction = worldDirection,
            fullViewMatrix = view,
            projectionMatrix = projection,
            localViewProjection = Matrix4f(projection).mul(view),
            farPlane = farPlane,
        )
    }

    private fun createPointMatrices(light: PreparedLight, face: Int): ShadowRenderMatrices {
        val component = light.component as PointLightComponent
        val worldDirection = Vector3f(POINT_FACE_DIRECTIONS[face])
        val farPlane = max(component.radius, 0.5f)
        val projection = Matrix4f().perspective(Math.toRadians(90.0).toFloat(), 1f, 0.05f, farPlane)
        val fullView = Matrix4f(POINT_FACE_VIEW_MATRICES[face])

        return ShadowRenderMatrices(
            direction = worldDirection,
            fullViewMatrix = fullView,
            projectionMatrix = projection,
            localViewProjection = Matrix4f(projection).mul(fullView),
            farPlane = farPlane,
        )
    }

    private fun updateActiveShadowMatrices(renderMatrices: ShadowRenderMatrices) {
        currentShadowViewMatrix.set(renderMatrices.fullViewMatrix)
        currentShadowProjectionMatrix.set(renderMatrices.projectionMatrix)
        currentShadowViewMatrixInverse.set(renderMatrices.fullViewMatrix).invert()
        currentShadowProjectionMatrixInverse.set(renderMatrices.projectionMatrix).invert()
        localShadowMatrixNotifier.notifyChanged()
    }

    private fun shadowTypeOf(component: LightComponent): ShadowType = when (component) {
        is PointLightComponent -> ShadowType.POINT
        is SpotLightComponent -> ShadowType.SPOT
    }

    private fun mixHash(current: Long, value: Float): Long = current * 31L + value.toRawBits().toLong()

    private class ShadowAtlas(
        val name: String,
        val width: Int,
        val height: Int,
        private val slotWidth: Int,
        private val slotHeight: Int,
        private val faceWidth: Int,
        private val faceHeight: Int,
        private val columnsPerSlot: Int,
    ) {
        private val columns = max(width / slotWidth, 1)
        private val rows = max(height / slotHeight, 1)
        private val occupancy = BooleanArray(columns * rows)

        private var texture = 0
        private var framebuffer: GlFramebuffer? = null

        fun textureId(): Int = texture

        fun irisFramebuffer(): GlFramebuffer? = framebuffer

        fun ensureCreated() {
            if (texture != 0 && framebuffer != null) {
                return
            }

            texture = GL11C.glGenTextures()
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, texture)
            GL11C.glTexImage2D(
                GL11C.GL_TEXTURE_2D,
                0,
                GL30C.GL_DEPTH_COMPONENT32F,
                width,
                height,
                0,
                GL11C.GL_DEPTH_COMPONENT,
                GL11C.GL_FLOAT,
                null as java.nio.ByteBuffer?,
            )
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, shadowAtlasFilter)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, shadowAtlasFilter)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL12C.GL_CLAMP_TO_EDGE)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL12C.GL_CLAMP_TO_EDGE)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL30C.GL_TEXTURE_COMPARE_MODE, GL30C.GL_COMPARE_REF_TO_TEXTURE)
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL30C.GL_TEXTURE_COMPARE_FUNC, GL11C.GL_LEQUAL)
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, 0)

            framebuffer = GlFramebuffer().apply {
                addDepthAttachment(texture)
                noDrawBuffers()
            }

            val status = framebuffer?.status ?: GL30C.GL_FRAMEBUFFER_UNDEFINED
            check(status == GL30C.GL_FRAMEBUFFER_COMPLETE) { "Framebuffer for $name is incomplete: $status" }

            GL11C.glViewport(0, 0, width, height)
            GL11C.glClearDepth(1.0)
            GL11C.glClear(GL11C.GL_DEPTH_BUFFER_BIT)
            Minecraft.getInstance().mainRenderTarget.bindWrite(false)
        }

        fun allocateSlot(): Int? {
            for (index in occupancy.indices) {
                if (!occupancy[index]) {
                    occupancy[index] = true
                    return index
                }
            }
            return null
        }

        fun freeSlot(slot: Int) {
            if (slot in occupancy.indices) {
                occupancy[slot] = false
            }
        }

        fun bind() {
            ensureCreated()
            framebuffer?.bind()
        }

        fun viewport(slot: Int, face: Int): ShadowViewport {
            val slotX = slot % columns * slotWidth
            val slotY = slot / columns * slotHeight
            val faceX = face % columnsPerSlot
            val faceY = face / columnsPerSlot
            val x = slotX + faceX * faceWidth
            val y = slotY + faceY * faceHeight

            val scaleX = (faceWidth - 1f) / width.toFloat()
            val scaleY = (faceHeight - 1f) / height.toFloat()
            val biasX = (x + 0.5f) / width.toFloat()
            val biasY = (y + 0.5f) / height.toFloat()

            return ShadowViewport(x, y, faceWidth, faceHeight, biasX, biasY, scaleX, scaleY)
        }

        fun clearDepth(x: Int, y: Int, w: Int, h: Int) {
            GL11C.glViewport(x, y, w, h)
            GL11C.glScissor(x, y, w, h)
            GL11C.glClearDepth(1.0)
            GL11C.glClear(GL11C.GL_DEPTH_BUFFER_BIT)
        }

        fun release() {
            occupancy.fill(false)
            framebuffer?.destroy()
            framebuffer = null
            if (texture != 0) {
                GL11C.glDeleteTextures(texture)
                texture = 0
            }
        }
    }

    private data class ShadowViewport(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val biasX: Float,
        val biasY: Float,
        val scaleX: Float,
        val scaleY: Float,
    )

    private data class ShadowWorkItem(
        val light: PreparedLight,
        val entry: ShadowCacheEntry,
        val signature: Long,
    )

    private class ShadowCacheEntry(
        val key: String,
        val shadowType: ShadowType,
    ) {
        var slot: Int = -1
        var signature: Long = 0L
        var valid = false
        var selectedFrame = -1L
        var lastWorldMutation = 0L
        var lastRenderFrame = Long.MIN_VALUE
        var farPlane = 0f
        val worldPosition = Vector3f()
        val localViewProjection = Array(MAX_POINT_FACES) { Matrix4f() }
        val tileData = Array(MAX_POINT_FACES) { Vector4f() }

        fun release() {
            when (shadowType) {
                ShadowType.SPOT -> spotAtlas.freeSlot(slot)
                ShadowType.POINT -> pointAtlas.freeSlot(slot)
            }
            slot = -1
            valid = false
        }
    }

    private enum class ShadowType {
        SPOT,
        POINT,
    }

    private enum class ShadowUpdateMode {
        SKIP,
        REFRESH,
        FORCE,
    }

    private data class ShadowRenderMatrices(
        val direction: Vector3f,
        val fullViewMatrix: Matrix4f,
        val projectionMatrix: Matrix4f,
        val localViewProjection: Matrix4f,
        val farPlane: Float,
    )

    private fun lookAt(origin: Vector3f, direction: Vector3f, up: Vector3f): Matrix4f =
        Matrix4f().lookAt(
            origin.x,
            origin.y,
            origin.z,
            origin.x + direction.x,
            origin.y + direction.y,
            origin.z + direction.z,
            up.x,
            up.y,
            up.z,
        )

    private fun upVector(direction: Vector3f): Vector3f =
        if (abs(direction.y) > 0.95f) Vector3f(0f, 0f, 1f) else Vector3f(0f, 1f, 0f)

    private val POINT_FACE_DIRECTIONS = arrayOf(
        Vector3f(1f, 0f, 0f),
        Vector3f(-1f, 0f, 0f),
        Vector3f(0f, 1f, 0f),
        Vector3f(0f, -1f, 0f),
        Vector3f(0f, 0f, 1f),
        Vector3f(0f, 0f, -1f),
    )

    private val POINT_FACE_VIEW_MATRICES = arrayOf(
        Matrix4f().rotate(Math.toRadians(90.0).toFloat(), 0f, -1f, 0f),
        Matrix4f().rotate(Math.toRadians(90.0).toFloat(), 0f, 1f, 0f),
        Matrix4f().rotate(Math.toRadians(90.0).toFloat(), 1f, 0f, 0f),
        Matrix4f().rotate(Math.toRadians(90.0).toFloat(), -1f, 0f, 0f),
        Matrix4f().rotate(Math.toRadians(180.0).toFloat(), 0f, 0f, 1f),
        Matrix4f().rotate(Math.toRadians(180.0).toFloat(), 0f, 1f, 0f),
    )
}
