package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.render.render
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.text.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutResult
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.bold
import ru.hollowhorizon.hollowengine.client.ui.widgets.code
import ru.hollowhorizon.hollowengine.client.ui.widgets.color
import ru.hollowhorizon.hollowengine.client.ui.widgets.fontFamily
import ru.hollowhorizon.hollowengine.client.ui.widgets.italic
import ru.hollowhorizon.hollowengine.client.ui.widgets.link
import ru.hollowhorizon.hollowengine.client.ui.widgets.strikethrough
import ru.hollowhorizon.hollowengine.client.ui.widgets.underline
import ru.hollowhorizon.hollowengine.client.utils.popPose
import ru.hollowhorizon.hollowengine.client.utils.pushPose
import ru.hollowhorizon.hollowengine.client.utils.setIdentity
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sqrt

private data class SvgRasterKey(
    val location: ResourceLocation,
    val revision: Long,
    val width: Int,
    val height: Int,
)

private data class SvgRasterTexture(
    val location: ResourceLocation,
    val texture: DynamicTexture,
)

private data class SvgRasterQuad(
    val texture: ResourceLocation,
    val width: Float,
    val height: Float,
    val transform: UiMatrix4,
)

private const val MinSvgRasterSize = 16
private const val MaxSvgRasterSize = 4096

internal fun svgRasterPixelSize(size: Float, scale: Float): Int {
    val requiredSize = ceil(size * scale).toInt().coerceIn(1, MaxSvgRasterSize)
    if (requiredSize <= MinSvgRasterSize) return MinSvgRasterSize
    return Integer.highestOneBit(requiredSize - 1).shl(1).coerceAtMost(MaxSvgRasterSize)
}

class MinecraftUiRenderer {
    private val commandRenderer = UiCommandRenderer()
    private val segment = ArrayList<UiRenderCommand>()
    private val framebuffers = UiFramebufferPool()
    private val widgets = UiWidgetRenderer(::drawImage, ::markTextBatchDirty, ::flushTextBatch)
    private val layerStack = ArrayDeque<LayerState>()
    private val clipStack = ArrayDeque<UiRect>()
    private val shapeBatch = mutableListOf<UiBatchedTriangle>()
    private val layerRequests = mutableListOf<UiLayerRequest>()
    private val brokenSvgSources = mutableSetOf<String>()
    private val svgRasterTextures = ConcurrentHashMap<SvgRasterKey, SvgRasterTexture>()
    private var layerProjectionActive = false
    private var renderTarget: UiRenderTarget? = null
    private var textBatchDirty = false

    /**
     * Renders a frame by recursively walking the resolved node tree; each node's draw
     * commands are produced on the fly and executed immediately (keeping the segment
     * batching), so no frame-wide command list is retained.
     */
    fun render(frame: HollowUiFrame, target: UiRenderTarget? = null) {
        val previousTarget = renderTarget
        renderTarget = target
        try {
            prepareFramebuffers(frame.layout)
            RenderSystem.enableBlend()
            configureUiBlend()
            RenderSystem.disableDepthTest()
            GL11.glDepthMask(false)
            renderTarget?.let {
                bindTarget(it.toState())
                configureLayerProjection(it.logicalWidth, it.logicalHeight)
            }
            segment.clear()
            commandRenderer.render(frame.root, frame.layout, frame.nowMillis, frame.typingState, ::submit)
            renderSegment(segment)
            segment.clear()
            flushTextBatch()
            disableScissor()
            while (layerStack.isNotEmpty()) finishLayer()
            GL11.glDepthMask(true)
        } finally {
            renderTarget = previousTarget
        }
    }

    private fun submit(command: UiRenderCommand) {
        if (isSegmentCommand(command)) {
            segment += command
            return
        }
        renderSegment(segment)
        segment.clear()
        render(command)
        flushShapeBatch()
        flushTextBatch()
    }

    fun close() {
        while (layerStack.isNotEmpty()) finishLayer()
        svgRasterTextures.values.forEach { it.texture.close() }
        svgRasterTextures.clear()
        framebuffers.close()
    }

    private fun render(command: UiRenderCommand) {
        when (command) {
            is BeginLayerCommand -> beginLayer(command)
            is EndLayerCommand -> finishLayer()
            is DrawBackdropFilterCommand -> drawBackdropFilter(command)
            is DrawShadowCommand -> drawShadow(command)
            is PushClipCommand -> pushClip(command.rect, command.transform)
            is PopClipCommand -> popClip()
            is DrawBoxCommand -> drawBox(command)
            is DrawShapeCommand -> drawShape(command)
            is DrawTextCommand -> drawText(command)
            is DrawImageCommand -> drawImage(command)
            is DrawItemCommand -> drawItem(command)
            is DrawEntityCommand -> drawEntity(command)
            is DrawSliderCommand -> drawWidget(command)
            is DrawCheckboxCommand -> drawWidget(command)
            is DrawTextFieldChromeCommand -> drawWidget(command)
        }
    }

    private fun isSegmentCommand(command: UiRenderCommand): Boolean = when (command) {
        is DrawBoxCommand -> !command.renderToFramebuffer
        is DrawShapeCommand -> true
        is DrawImageCommand -> !command.renderToFramebuffer && command.filter == UiFilterChain.Empty
        is DrawTextCommand -> true
        else -> false
    }

    private fun renderSegment(commands: List<UiRenderCommand>) {
        if (commands.isEmpty()) return
        for (phase in UiRenderPhase.entries) renderPhase(commands, phase)
        flushShapeBatch()
        flushTextBatch()
    }

    private fun renderPhase(commands: List<UiRenderCommand>, phase: UiRenderPhase) {
        val imageBatches = linkedMapOf<ResourceLocation, MutableList<UiTexturedQuad>>()

        commands.forEach { command ->
            when (command) {
                is DrawBoxCommand -> if (command.phase == phase) {
                    flushImageBatches(imageBatches)
                    if (!appendBatchedShapes(command)) {
                        flushShapeBatch()
                        flushTextBatch()
                        drawBox(command)
                    }
                }

                is DrawShapeCommand -> if (command.phase == phase) {
                    flushImageBatches(imageBatches)
                    if (!appendBatchedShapes(command)) {
                        flushShapeBatch()
                        flushTextBatch()
                        drawShape(command)
                    }
                }

                is DrawImageCommand -> if (command.phase == phase) {
                    flushShapeBatch()
                    flushTextBatch()
                    if (!appendSvgImage(command, imageBatches)) appendImageBatch(command, imageBatches)
                }

                is DrawTextCommand -> if (command.phase == phase) {
                    flushImageBatches(imageBatches)
                    flushShapeBatch()
                    drawText(command)
                }

                else -> Unit
            }
        }

        flushImageBatches(imageBatches)
        flushShapeBatch()
        flushTextBatch()
    }

    private fun flushImageBatches(imageBatches: MutableMap<ResourceLocation, MutableList<UiTexturedQuad>>) {
        if (imageBatches.isEmpty()) return
        imageBatches.forEach { (texture, quads) -> UiTextureEffects.drawTexturedQuads(texture, quads) }
        imageBatches.clear()
    }

    private fun appendImageBatch(
        command: DrawImageCommand,
        batches: MutableMap<ResourceLocation, MutableList<UiTexturedQuad>>,
    ) {
        if (command.rect.width <= 0f || command.rect.height <= 0f || command.opacity <= 0f) return
        val location = ResourceLocation.tryParse(command.source) ?: return
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        batches.getOrPut(location) { mutableListOf() } += UiTexturedQuad(
            width = command.rect.width,
            height = command.rect.height,
            transform = transform,
            opacity = command.opacity,
            fit = command.fit,
            slice = command.slice,
            tint = command.tint,
        )
    }

    private fun appendSvgImage(
        command: DrawImageCommand,
        batches: MutableMap<ResourceLocation, MutableList<UiTexturedQuad>>,
    ): Boolean {
        val quad = svgRasterQuad(
            width = command.rect.width,
            height = command.rect.height,
            source = command.source,
            opacity = command.opacity,
            transform = effective(command.transform),
            fit = command.fit,
            backfaceVisibility = command.backfaceVisibility,
        ) ?: return svgLocation(command.source) != null
        batches.getOrPut(quad.texture) { mutableListOf() } += UiTexturedQuad(
            width = quad.width,
            height = quad.height,
            transform = quad.transform,
            opacity = command.opacity,
            fit = command.fit.svgRasterDrawFit(),
            slice = command.slice,
            tint = command.tint,
        )
        return true
    }

    private fun svgRasterQuad(
        width: Float,
        height: Float,
        source: String,
        opacity: Float,
        transform: UiMatrix4,
        fit: UiImageFit,
        backfaceVisibility: UiBackfaceVisibility = UiBackfaceVisibility.VISIBLE,
    ): SvgRasterQuad? {
        val location = svgLocation(source) ?: return null
        if (width <= 0f || height <= 0f || opacity <= 0f) return null
        if (isBackfaceHidden(width, height, transform, backfaceVisibility)) return null
        val revision = HollowUiResourceAccess.version(location)
        return runCatching {
            val intrinsicSize = UiSvgRasterizer.intrinsicSize(location, revision)
            val placement = imagePlacement(width, height, fit, intrinsicSize)
            val pixelWidth = rasterPixelSize(placement.width)
            val pixelHeight = rasterPixelSize(placement.height)
            val texture =
                svgRasterTextures.computeIfAbsent(SvgRasterKey(location, revision, pixelWidth, pixelHeight)) { key ->
                    createSvgRasterTexture(key)
                }
            SvgRasterQuad(
                texture = texture.location,
                width = placement.width,
                height = placement.height,
                transform = transform * UiMatrix4.translation(placement.x, placement.y, 0f),
            )
        }.getOrElse { error ->
            if (brokenSvgSources.add(source)) {
                HollowEngine.LOGGER.error("Error while loading SVG image $source", error)
            }
            null
        }
    }

    private fun rasterPixelSize(size: Float): Int {
        return svgRasterPixelSize(size, layerScale())
    }

    private fun createSvgRasterTexture(key: SvgRasterKey): SvgRasterTexture {
        val image = UiSvgRasterizer.rasterize(key.location, key.revision, key.width, key.height)
        val texture = DynamicTexture(image)
        val location = svgDynamicTextureLocation(key)
        Minecraft.getInstance().textureManager.register(location, texture)
        return SvgRasterTexture(location, texture)
    }

    private fun svgDynamicTextureLocation(key: SvgRasterKey): ResourceLocation {
        val hash = key.hashCode().toString().replace("-", "n")
        return ResourceLocation.fromNamespaceAndPath(HollowEngine.MODID, "generated/ui/svg/$hash")
    }

    private fun flushShapeBatch() {
        if (shapeBatch.isEmpty()) return
        drawBatchedTriangles(shapeBatch)
        shapeBatch.clear()
    }

    private fun markTextBatchDirty() {
        textBatchDirty = true
    }

    private fun flushTextBatch() {
        if (!textBatchDirty) return
        Minecraft.getInstance().renderBuffers().bufferSource().endBatch()
        textBatchDirty = false
    }

    private fun appendBatchedShapes(command: UiRenderCommand): Boolean {
        if (command is DrawShapeCommand) return appendBatchedShape(command)
        if (command !is DrawBoxCommand) return false
        if (command.rect.width <= 0f || command.rect.height <= 0f) return false
        if (command.renderToFramebuffer) return false
        val transform = effective(command.transform)
        if (isBackfaceHidden(
                command.rect.width, command.rect.height, transform, command.backfaceVisibility
            )
        ) return true
        when (val paint = command.paint) {
            UiResolvedPaint.None -> Unit
            is UiResolvedPaint.Image,
            is UiResolvedPaint.Shader,
                -> return false

            is UiResolvedPaint.Color -> shapeBatch.appendLocalPaint(
                width = command.rect.width,
                height = command.rect.height,
                radius = command.border.radius,
                color = paint.color.withOpacity(command.opacity),
                transform = transform,
                filter = command.filter,
            )

            is UiResolvedPaint.LinearGradient -> shapeBatch.appendLocalGradient(
                width = command.rect.width,
                height = command.rect.height,
                radius = command.border.radius,
                angleDegrees = paint.angleDegrees,
                stops = paint.stops,
                opacity = command.opacity,
                transform = transform,
                filter = command.filter,
            )

            is UiResolvedPaint.RadialGradient -> shapeBatch.appendLocalRadialGradient(
                width = command.rect.width,
                height = command.rect.height,
                radius = command.border.radius,
                gradient = paint.gradient,
                opacity = command.opacity,
                transform = transform,
                filter = command.filter,
            )
        }
        appendBorderShapes(command, transform)
        return true
    }

    private fun appendBatchedShape(command: DrawShapeCommand): Boolean {
        if (command.rect.width <= 0f || command.rect.height <= 0f || command.opacity <= 0f) return true
        val transform = effective(command.transform)
        if (isBackfaceHidden(
                command.rect.width, command.rect.height, transform, command.backfaceVisibility
            )
        ) return true
        return shapeBatch.appendLocalShape(
            shape = command.shape,
            width = command.rect.width,
            height = command.rect.height,
            fill = command.fill,
            stroke = command.stroke,
            strokeWidth = command.strokeWidth,
            opacity = command.opacity,
            transform = transform,
            filter = command.filter,
        )
    }

    private fun appendBorderShapes(command: DrawBoxCommand, transform: UiMatrix4) {
        val borderWidth = command.border.width.left.resolve(command.rect.width)
        if (borderWidth <= 0f || command.border.color.alpha <= 0f) return
        val width = command.rect.width
        val height = command.rect.height
        val thickness = borderWidth.coerceAtLeast(1f).coerceAtMost(minOf(width, height) * 0.5f)
        val color = command.border.color.withOpacity(command.opacity).filtered(command.filter)
        shapeBatch.appendLocalBorder(width, height, command.border.radius, thickness, color, transform)
    }

    private fun prepareFramebuffers(layout: UiLayoutResult) {
        val scale = layerScale()
        layerRequests.clear()
        for (node in layout.traversalOrder) {
            val layoutNode = layout.nodes[node] ?: continue
            if (!layoutNode.needsFramebuffer) continue
            val padding = layerPadding(node.resolvedSnapshot.filter)
            val width = ceil((layoutNode.rect.width + padding * 2f) * scale).toInt().coerceAtLeast(1)
            val height = ceil((layoutNode.rect.height + padding * 2f) * scale).toInt().coerceAtLeast(1)
            layerRequests += UiLayerRequest(width, height)
        }
        framebuffers.beginFrame(
            layerRequests,
            1,
            1,
        )
    }

    private fun beginLayer(command: BeginLayerCommand) {
        val scale = layerScale()
        val padding = layerPadding(command)
        val logicalWidth = command.rect.width + padding * 2f
        val logicalHeight = command.rect.height + padding * 2f
        val width = ceil(logicalWidth * scale).toInt().coerceAtLeast(1)
        val height = ceil(logicalHeight * scale).toInt().coerceAtLeast(1)
        val framebuffer = framebuffers.acquireLayer(width, height)
        val parentClips = clipStack.toList()
        if (!layerProjectionActive) {
            RenderSystem.backupProjectionMatrix()
            RenderSystem.getModelViewStack().pushPose()
            layerProjectionActive = true
        }
        framebuffer.clear()
        framebuffer.bind()
        configureLayerProjection(logicalWidth, logicalHeight)
        configureUiBlend()
        RenderSystem.disableDepthTest()
        layerStack.addLast(
            LayerState(
                rect = command.rect,
                radius = command.radius,
                clipShape = command.clipShape,
                transform = command.transform,
                framebuffer = framebuffer,
                parentClips = parentClips,
                scale = scale,
                filter = command.filter,
                backfaceVisibility = command.backfaceVisibility,
                padding = padding,
                opacity = command.opacity,
            )
        )
        clipStack.clear()
    }

    private class RenderSource(
        val texture: Int,
        val width: Int,
        val height: Int,
        val u0: Float, val v0: Float,
        val u1: Float, val v1: Float,
    )

    private fun finishLayer() {
        val layer = layerStack.removeLast()
        val parentLayer = layerStack.lastOrNull()

        restoreClips(layer.parentClips)

        val copiedSource =
            if (parentLayer != null || layer.filter.blurRadius() > 0f) copyLayerToScratch(layer) else null
        val blurredSource = blurIfNeeded(copiedSource, layer.filter.blurRadius())

        val source = resolveRenderSource(layer, copiedSource, blurredSource)
        val compositeFilter = layer.filter.withoutBlur()

        val transform = if (parentLayer != null) {
            setupParentLayerContext(parentLayer)
            calculateParentTransform(parentLayer, layer)
        } else {
            setupRootLayerContext()
            calculateRootTransform(layer)
        }

        drawLayerTexture(
            layer,
            source.texture,
            source.width,
            source.height,
            source.u0,
            source.v0,
            source.u1,
            source.v1,
            compositeFilter,
            transform
        )

        if (parentLayer == null) restoreMainProjection()

        blurredSource?.let(framebuffers::release)
        copiedSource?.let(framebuffers::release)
        RenderSystem.disableDepthTest()
    }


    private fun blurIfNeeded(copiedSource: UiFramebuffer?, blurRadius: Float): UiFramebuffer? {
        if (blurRadius <= 0f || copiedSource == null) return null
        return blurTexture(
            framebuffers, copiedSource.texture, copiedSource.width, copiedSource.height, blurRadius, copiedSource
        )
    }

    private fun resolveRenderSource(layer: LayerState, copied: UiFramebuffer?, blurred: UiFramebuffer?): RenderSource {
        val activeBuffer = blurred ?: copied

        return if (activeBuffer != null) {
            RenderSource(
                texture = activeBuffer.texture,
                width = activeBuffer.width,
                height = activeBuffer.height,
                u0 = 0f,
                v0 = 0f,
                u1 = 1f,
                v1 = 1f
            )
        } else {
            RenderSource(
                texture = layer.framebuffer.texture,
                width = layer.framebuffer.atlas.width,
                height = layer.framebuffer.atlas.height,
                u0 = layer.framebuffer.u0(),
                v0 = layer.framebuffer.v0(),
                u1 = layer.framebuffer.u1(),
                v1 = layer.framebuffer.v1()
            )
        }
    }

    private fun setupParentLayerContext(parentLayer: LayerState) {
        parentLayer.framebuffer.bind()
        configureLayerProjection(
            parentLayer.rect.width + parentLayer.padding * 2f, parentLayer.rect.height + parentLayer.padding * 2f
        )
        restoreActiveClip()
    }

    private fun setupRootLayerContext() {
        bindRootTarget()
        restoreActiveClip()
    }

    private fun calculateParentTransform(parentLayer: LayerState, layer: LayerState): UiMatrix4 {
        val parentInverse =
            parentLayer.transform.inverse() ?: UiMatrix4.translation(-parentLayer.rect.x, -parentLayer.rect.y, 0f)

        return UiMatrix4.translation(
            parentLayer.padding,
            parentLayer.padding,
            0f
        ) * parentInverse * layer.transform * UiMatrix4.translation(-layer.padding, -layer.padding, 0f)
    }

    private fun calculateRootTransform(layer: LayerState): UiMatrix4 {
        return layer.transform * UiMatrix4.translation(-layer.padding, -layer.padding, 0f)
    }

    private fun drawLayerTexture(
        layer: LayerState,
        texture: Int,
        textureWidth: Int,
        textureHeight: Int,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        filter: UiFilterChain,
        transform: UiMatrix4,
    ) {
        val width = layer.rect.width + layer.padding * 2f
        val height = layer.rect.height + layer.padding * 2f
        if (isBackfaceHidden(width, height, transform, layer.backfaceVisibility)) return
        val clipShape = layer.clipShape
        if (clipShape != null) {
            val horizontalPadding = layer.padding / width.coerceAtLeast(0.0001f)
            val verticalPadding = layer.padding / height.coerceAtLeast(0.0001f)
            val clippedTransform = transform * UiMatrix4.translation(layer.padding, layer.padding, 0f)
            UiTextureEffects.drawTexturedShapeRegion(
                texture = texture,
                width = layer.rect.width,
                height = layer.rect.height,
                shape = clipShape,
                transform = clippedTransform,
                opacity = layer.opacity,
                u0 = u0 + (u1 - u0) * horizontalPadding,
                v0 = v0 + (v1 - v0) * verticalPadding,
                u1 = u1 - (u1 - u0) * horizontalPadding,
                v1 = v1 - (v1 - v0) * verticalPadding,
                flipY = true,
                filter = filter,
                textureWidth = textureWidth,
                textureHeight = textureHeight,
            )
        } else {
            UiTextureEffects.drawTexturedRegion(
                texture = texture,
                width = width,
                height = height,
                transform = transform,
                opacity = layer.opacity,
                u0 = u0,
                v0 = v0,
                u1 = u1,
                v1 = v1,
                flipY = true,
                filter = filter,
                textureWidth = textureWidth,
                textureHeight = textureHeight,
                subdivisions = LayerTextureSubdivisions,
                maskRadius = layer.radius,
                maskPadding = layer.padding,
                maskScale = layer.scale,
            )
        }
    }

    private fun copyLayerToScratch(layer: LayerState): UiFramebuffer {
        val scratch = framebuffers.acquire(layer.framebuffer.width, layer.framebuffer.height)
        disableScissor()
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, layer.framebuffer.framebuffer)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratch.framebuffer)
        GL30.glBlitFramebuffer(
            layer.framebuffer.region.x,
            layer.framebuffer.region.y,
            layer.framebuffer.region.x + layer.framebuffer.width,
            layer.framebuffer.region.y + layer.framebuffer.height,
            0,
            0,
            scratch.width,
            scratch.height,
            GL11.GL_COLOR_BUFFER_BIT,
            GL11.GL_NEAREST,
        )
        return scratch
    }

    private fun drawBackdropFilter(command: DrawBackdropFilterCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        val target = currentTarget()
        val scratch = framebuffers.acquire(target.width, target.height, exclude = target.framebuffer)
        disableScissor()
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.framebufferId)
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scratch.framebuffer)
        GL30.glBlitFramebuffer(
            target.x,
            target.y,
            target.x + target.width,
            target.y + target.height,
            0,
            0,
            target.width,
            target.height,
            GL11.GL_COLOR_BUFFER_BIT,
            GL11.GL_NEAREST,
        )
        bindTarget(target)
        if (target.logicalWidth > 0f && target.logicalHeight > 0f) {
            configureLayerProjection(target.logicalWidth, target.logicalHeight)
        }

        val blurred = command.filter.blurRadius().takeIf { it > 0f }
            ?.let { blurTexture(framebuffers, scratch.texture, target.width, target.height, it, scratch) }
        val sourceTexture = blurred?.texture ?: scratch.texture
        val compositeFilter = command.filter.withoutBlur()
        bindTarget(target)
        if (target.logicalWidth > 0f && target.logicalHeight > 0f) {
            configureLayerProjection(target.logicalWidth, target.logicalHeight)
        }
        restoreActiveClip()
        val local = localRect(command.rect)
        val u0 = (local.x * target.scale / target.width.toFloat()).coerceIn(0f, 1f)
        val u1 = ((local.x + local.width) * target.scale / target.width.toFloat()).coerceIn(0f, 1f)
        val v0 = (1f - local.y * target.scale / target.height.toFloat()).coerceIn(0f, 1f)
        val v1 = (1f - (local.y + local.height) * target.scale / target.height.toFloat()).coerceIn(0f, 1f)
        UiTextureEffects.drawTexturedRegion(
            texture = sourceTexture,
            width = command.rect.width,
            height = command.rect.height,
            transform = transform,
            opacity = command.opacity,
            u0 = u0,
            v0 = v0,
            u1 = u1,
            v1 = v1,
            flipY = false,
            filter = compositeFilter,
            textureWidth = target.width,
            textureHeight = target.height,
            subdivisions = LayerTextureSubdivisions,
            maskRadius = command.radius,
            maskScale = target.scale,
        )
        blurred?.let(framebuffers::release)
        framebuffers.release(scratch)
    }

    private fun drawShadow(command: DrawShadowCommand) {
        val transform = effective(command.transform)
        if (!isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) {
            command.shadows.forEach {
                drawProjectedShadow(
                    command.rect.width,
                    command.rect.height,
                    command.radius,
                    it,
                    command.opacity,
                    transform,
                    command.filter
                )
            }
        }
    }

    private fun drawBox(command: DrawBoxCommand) {
        RenderSystem.enableBlend()

        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        when (val paint = command.paint) {
            UiResolvedPaint.None -> Unit
            is UiResolvedPaint.Color -> drawLocalPaint(
                command.rect.width,
                command.rect.height,
                command.border.radius,
                paint.color.withOpacity(command.opacity),
                transform,
                command.filter
            )

            is UiResolvedPaint.LinearGradient -> drawLocalGradient(
                command.rect.width,
                command.rect.height,
                command.border.radius,
                paint.angleDegrees,
                paint.stops,
                command.opacity,
                transform,
                command.filter,
            )

            is UiResolvedPaint.RadialGradient -> drawLocalRadialGradient(
                command.rect.width,
                command.rect.height,
                command.border.radius,
                paint.gradient,
                command.opacity,
                transform,
                command.filter,
            )

            is UiResolvedPaint.Image -> drawImage(
                command.rect.width,
                command.rect.height,
                paint.source,
                command.opacity,
                transform,
                fit = command.fit,
                slice = command.slice,
                filter = command.filter,
                tint = command.tint,
            )

            is UiResolvedPaint.Shader -> drawLocalPaint(
                command.rect.width,
                command.rect.height,
                command.border.radius,
                UiColor(0.2f, 0.2f, 0.24f, command.opacity),
                transform,
                command.filter,
            )
        }
        if (command.border.width.left.resolve(command.rect.width) > 0f && command.border.color.alpha > 0f) {
            val borderWidth = command.border.width.left.resolve(command.rect.width).coerceAtLeast(1f)
            val borderColor = command.border.color.withOpacity(command.opacity).filtered(command.filter)
            drawLocalBorder(
                command.rect.width, command.rect.height, command.border.radius, borderWidth, borderColor, transform
            )
        }
    }

    private fun drawShape(command: DrawShapeCommand) {
        if (appendBatchedShape(command)) {
            flushShapeBatch()
            return
        }
        val transform = effective(command.transform)
        drawLocalPaint(
            command.rect.width,
            command.rect.height,
            0f,
            UiColor(0.2f, 0.2f, 0.24f, command.opacity),
            transform,
            command.filter,
        )
    }

    private fun drawText(command: DrawTextCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        val xAxis = transform.transform(1f, 0f)
        val origin = transform.transform(0f, 0f)
        val yAxis = transform.transform(0f, 1f)
        val scaleX = sqrt((xAxis.x - origin.x) * (xAxis.x - origin.x) + (xAxis.y - origin.y) * (xAxis.y - origin.y))
        val scaleY = sqrt((yAxis.x - origin.x) * (yAxis.x - origin.x) + (yAxis.y - origin.y) * (yAxis.y - origin.y))
        val now = TickHandler.time / 20f
        val clipped = command.overflow != UiTextOverflow.SHOW
        if (clipped) {
            flushTextBatch()
            pushClip(UiRect(0f, 0f, command.rect.width, command.rect.height), command.transform)
        }
        command.layout.visibleLineItems(command.scrollOffset.y, command.rect.height).forEach { (_, line) ->
            val displayLine = if (command.overflow == UiTextOverflow.DOTS) UiTextOverflowResolver.ellipsizeLine(
                command, line
            ) else line
            drawTextLine(command, displayLine, transform, scaleX, scaleY, now)
        }
        if (clipped) {
            flushTextBatch()
            popClip()
        }
    }

    private fun drawTextLine(
        command: DrawTextCommand,
        line: UiTextLine,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
        now: Float,
    ) {
        if (line.fragments.isNotEmpty()) {
            line.fragments.forEach { fragment ->
                when (fragment) {
                    is UiInlineImageRun -> drawInlineImage(command, fragment, line, transform)
                    is UiInlineWidgetRun -> Unit
                    is UiTextSpaceRun -> Unit
                    is UiTextRun -> drawTextRun(command, fragment, line, transform, scaleX, scaleY, now)
                }
            }
        } else {
            val fragment = UiTextRun(
                text = line.text,
                style = UiInlineStyle(),
                x = line.x,
                y = 0f,
                width = line.naturalWidth,
                height = command.fontSize,
            )
            drawTextRun(command, fragment, line, transform, scaleX, scaleY, now)
        }
    }

    private fun drawTextRun(
        command: DrawTextCommand,
        fragment: UiTextRun,
        line: UiTextLine,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
        now: Float,
    ) {
        val fontSize = fragment.style.resolvedFontSize(command.fontSize)
        val effects = fragment.style.effects + command.textEffects
        val fontFamily = fragment.style.fontFamily ?: command.fontFamily
        val localX = line.x + fragment.x - command.scrollOffset.x
        val localY = line.y + fragment.y - command.scrollOffset.y

        drawTextBackground(command, fragment, transform, localX, localY)

        val hasLayer = effects.hasLayerEffects()
        val hasAnimated = effects.hasAnimatedEffects()

        if (!hasLayer && !hasAnimated) {
            drawSingleTextRun(
                command, fragment, transform, scaleX, scaleY,
                localX,
                localY,
                fontSize,
                fontFamily,
                fragment.style.color,
                1f,
            )
            return
        }

        val layerEffects = effects.filter { it.isLayer }
        val animatedEffects = if (hasAnimated) effects.filter { it.isAnimated } else emptyList()

        if (hasAnimated) {
            drawAnimatedTextRun(
                command,
                fragment,
                transform,
                scaleX,
                scaleY,
                now,
                fontSize,
                fontFamily,
                localX,
                localY,
                animatedEffects,
                layerEffects,
            )
            return
        }

        val effectiveColor = fragment.style.color ?: if (fragment.style.link != null) {
            val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
            if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
        } else {
            command.color
        }

        for (layerEffect in layerEffects) {
            val passes = UiTextEffectApplier.getLayerPasses(layerEffect)
            for (pass in passes) {
                val passColor = pass.colorOverride?.textShadowColor(layerEffect, effectiveColor) ?: effectiveColor
                drawSingleTextRun(
                    command,
                    fragment,
                    transform,
                    scaleX,
                    scaleY,
                    localX + pass.offsetX,
                    localY + pass.offsetY,
                    fontSize,
                    fontFamily,
                    passColor,
                    pass.alphaMultiplier,
                )
            }
        }

        drawSingleTextRun(
            command, fragment, transform, scaleX, scaleY,
            localX, localY, fontSize,
            fontFamily,
            fragment.style.color, 1f,
        )
    }

    private fun drawTextBackground(
        command: DrawTextCommand,
        fragment: UiTextRun,
        transform: UiMatrix4,
        localX: Float,
        localY: Float,
    ) {
        val background = fragment.style.background ?: return
        if (fragment.width <= 0f || fragment.height <= 0f || background.alpha <= 0f) return
        flushTextBatch()
        drawLocalPaint(
            fragment.width,
            fragment.height,
            2f,
            background.withOpacity(command.opacity).filtered(command.filter),
            transform * UiMatrix4.translation(localX, localY, 0f),
            command.filter,
        )
    }

    private fun drawSingleTextRun(
        command: DrawTextCommand,
        fragment: UiTextRun,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
        localX: Float,
        localY: Float,
        fontSize: Float,
        fontFamily: String?,
        colorOverride: UiColor?,
        alphaMultiplier: Float,
    ) {
        if (fragment.style.code) {
            flushTextBatch()
            drawLocalPaint(
                fragment.width,
                fragment.height,
                2f,
                UiColor(0f, 0f, 0f, 0.28f * command.opacity * alphaMultiplier),
                transform * UiMatrix4.translation(localX, localY, 0f),
                command.filter,
            )
        }

        if (!fontFamily.isNullOrBlank() && drawMsdfTextRun(
                command,
                fragment,
                transform,
                localX,
                localY,
                fontSize,
                fontFamily,
                colorOverride,
                alphaMultiplier,
            )
        ) {
            return
        }

        val mc = Minecraft.getInstance()
        val fontScale = fontSize / mc.font.lineHeight.toFloat()
        val origin = transform.transform(localX, localY)
        val pose = PoseStack()
        pose.translate(
            origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble() - 10
        )
        pose.scale(scaleX * fontScale, scaleY * fontScale, 1f)
        val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
        val color = colorOverride ?: fragment.style.color ?: if (fragment.style.link != null) {
            if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
        } else {
            command.color
        }
        val finalAlpha = command.opacity * alphaMultiplier * color.alpha
        val finalColor = UiColor(color.red, color.green, color.blue, finalAlpha)
        val component = Component.literal(fragment.text).withStyle { style ->
            style.withBold(fragment.style.bold).withItalic(fragment.style.italic)
                .withUnderlined(fragment.style.underline || fragment.style.link != null)
                .withStrikethrough(fragment.style.strikethrough)
                .withColor(TextColor.fromRgb(finalColor.argb() and 0xFFFFFF))
        }
        mc.font.drawInBatch(
            component.visualOrderText,
            0f,
            0f,
            finalColor.filtered(command.filter).argb(),
            false,
            pose.last().pose(),
            mc.renderBuffers().bufferSource(),
            DisplayMode.SEE_THROUGH,
            0,
            15728880,
        )
        markTextBatchDirty()
    }

    private fun drawAnimatedTextRun(
        command: DrawTextCommand,
        fragment: UiTextRun,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
        now: Float,
        fontSize: Float,
        fontFamily: String?,
        baseLocalX: Float,
        baseLocalY: Float,
        animatedEffects: List<UiTextEffect>,
        layerEffects: List<UiTextEffect>,
    ) {
        val totalChars = fragment.text.length

        for (charIndex in fragment.text.indices) {
            val char = fragment.text[charIndex]
            val charWidth = UiTextLayouter.measureTextWidth(char.toString(), fontSize, fontFamily)
            val charPos = charIndex.toFloat() / totalChars.coerceAtLeast(1).toFloat()
            val prefixText = fragment.text.substring(0, charIndex)
            val prefixWidth = UiTextLayouter.measureTextWidth(prefixText, fontSize, fontFamily)
            val charLocalX = baseLocalX + prefixWidth
            val charFragment = fragment.copy(text = char.toString(), x = charLocalX, width = charWidth)

            val ctx = UiEffectContext(
                time = now,
                charIndex = charIndex,
                totalChars = totalChars,
                charPos = charPos,
                runWidth = fragment.width,
                runHeight = fragment.height,
                random = 0f,
            )

            var charOffsetX = 0f
            var charOffsetY = 0f
            var colorOverride: UiColor? = fragment.style.color
            var alphaMul = 1f

            for (effect in animatedEffects) {
                val result = UiTextEffectApplier.apply(effect, ctx)
                charOffsetX += result.offsetX
                charOffsetY += result.offsetY
                if (result.colorOverride != null) colorOverride = result.colorOverride
                alphaMul *= result.alphaMultiplier
            }

            if (layerEffects.isNotEmpty()) {
                val effectiveColor = colorOverride ?: if (fragment.style.link != null) {
                    val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
                    if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
                } else {
                    command.color
                }
                for (layerEffect in layerEffects) {
                    val passes = UiTextEffectApplier.getLayerPasses(layerEffect)
                    for (pass in passes) {
                        val passColor =
                            pass.colorOverride?.textShadowColor(layerEffect, effectiveColor) ?: effectiveColor
                        drawSingleTextRun(
                            command,
                            charFragment,
                            transform,
                            scaleX,
                            scaleY,
                            charLocalX + charOffsetX + pass.offsetX,
                            baseLocalY + charOffsetY + pass.offsetY,
                            fontSize,
                            fontFamily,
                            passColor,
                            command.opacity * alphaMul,
                        )
                    }
                }
            }

            val finalLocalX = charLocalX + charOffsetX
            val finalLocalY = baseLocalY + charOffsetY

            val finalColor = colorOverride ?: if (fragment.style.link != null) {
                val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
                if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
            } else {
                command.color
            }
            drawSingleTextRun(
                command,
                charFragment,
                transform,
                scaleX,
                scaleY,
                finalLocalX,
                finalLocalY,
                fontSize,
                fontFamily,
                finalColor,
                command.opacity * alphaMul,
            )
        }
    }

    private fun drawMsdfTextRun(
        command: DrawTextCommand,
        fragment: UiTextRun,
        transform: UiMatrix4,
        localX: Float,
        localY: Float,
        fontSize: Float,
        fontFamily: String,
        colorOverride: UiColor?,
        alphaMultiplier: Float,
    ): Boolean {
        val fontData = UiMsdfFont.getOrLoadFontData(fontFamily) ?: return false
        val shader = ModShaders.MSDF_TEXT ?: return false
        flushTextBatch()
        val atlasInfo = fontData.meta.atlas
        val metrics = fontData.meta.metrics
        val distanceRange = atlasInfo.distanceRange
        val atlasWidth = atlasInfo.width.toFloat()
        val atlasHeight = atlasInfo.height.toFloat()
        val pxPerEm = fontSize
        val baselineY = metrics.ascender * pxPerEm

        val tessellator = Tesselator.getInstance()
        val bufferBuilder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)

        val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
        val baseColor = colorOverride ?: fragment.style.color ?: if (fragment.style.link != null) {
            if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
        } else {
            command.color
        }
        val alpha = command.opacity * alphaMultiplier
        val color = baseColor.withOpacity(alpha).filtered(command.filter)

        var penX = 0f
        for (char in fragment.text) {
            val glyph = fontData.glyphMap[char]
            if (glyph == null) {
                penX += fontData.metrics.advance(char, fontSize)
                continue
            }

            val ab = glyph.atlasBounds
            val pb = glyph.planeBounds
            val u0 = ab.left / atlasWidth
            val v0 = ab.bottom / atlasHeight
            val u1 = ab.right / atlasWidth
            val v1 = ab.top / atlasHeight

            val x0 = penX + pb.left * pxPerEm
            val x1 = penX + pb.right * pxPerEm

            val yTop = baselineY - pb.top * pxPerEm
            val yBottom = baselineY - pb.bottom * pxPerEm

            val bottomLeft = transform.transform(localX + x0, localY + yBottom)
            val topLeft = transform.transform(localX + x0, localY + yTop)
            val topRight = transform.transform(localX + x1, localY + yTop)
            val bottomRight = transform.transform(localX + x1, localY + yBottom)

            val r = color.red
            val g = color.green
            val b = color.blue
            val a = color.alpha

            bufferBuilder.addVertex(bottomRight.x, bottomRight.y, bottomRight.z).setColor(r, g, b, a).setUv(u1, v0)
            bufferBuilder.addVertex(topRight.x, topRight.y, topRight.z).setColor(r, g, b, a).setUv(u1, v1)
            bufferBuilder.addVertex(topLeft.x, topLeft.y, topLeft.z).setColor(r, g, b, a).setUv(u0, v1)
            bufferBuilder.addVertex(bottomLeft.x, bottomLeft.y, bottomLeft.z).setColor(r, g, b, a).setUv(u0, v0)

            penX += glyph.advance * pxPerEm
        }

        RenderSystem.setShader { shader }
        RenderSystem.setShaderTexture(0, fontData.textureId)

        shader.safeGetUniform("DistanceRange")?.set(distanceRange)
        shader.safeGetUniform("Softness")?.set(0.15f)
        shader.safeGetUniform("OutlineWidth")?.set(0f)
        shader.safeGetUniform("OutlineColor")?.set(0f, 0f, 0f, 0f)
        shader.safeGetUniform("GlowRadius")?.set(0f)
        shader.safeGetUniform("GlowColor")?.set(0f, 0f, 0f, 0f)
        shader.safeGetUniform("ShadowOffset")?.set(0f, 0f)
        shader.safeGetUniform("ShadowColor")?.set(0f, 0f, 0f, 0f)
        shader.safeGetUniform("AtlasSize")?.set(atlasWidth, atlasHeight)
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())
        return true
    }

    private fun drawInlineImage(
        command: DrawTextCommand,
        fragment: UiInlineImageRun,
        line: UiTextLine,
        transform: UiMatrix4,
    ) {
        flushTextBatch()
        drawImage(
            fragment.width,
            fragment.height,
            fragment.image.source,
            command.opacity,
            transform * UiMatrix4.translation(
                line.x + fragment.x - command.scrollOffset.x, line.y + fragment.y - command.scrollOffset.y, 0f
            ),
            UiImageFit.CONTAIN,
            command.filter,
        )
    }

    private fun drawImage(command: DrawImageCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        drawImage(
            command.rect.width,
            command.rect.height,
            command.source,
            command.opacity,
            transform,
            command.fit,
            command.filter,
            command.slice,
            command.tint,
        )
    }

    private fun drawImage(
        width: Float,
        height: Float,
        source: String,
        opacity: Float,
        transform: UiMatrix4,
        fit: UiImageFit = UiImageFit.STRETCH,
        filter: UiFilterChain = UiFilterChain.Empty,
        slice: UiInsets = UiInsets.Zero,
        tint: UiColor = UiColor.White,
    ) {
        val svgQuad = svgRasterQuad(width, height, source, opacity, transform, fit)
        if (svgQuad != null) {
            RenderSystem.setShaderTexture(0, svgQuad.texture)
            UiTextureEffects.drawTexturedQuad(
                svgQuad.width,
                svgQuad.height,
                svgQuad.transform,
                opacity,
                flipY = false,
                fit = fit.svgRasterDrawFit(),
                texture = svgQuad.texture,
                filter = filter,
                slice = slice,
                tint = tint,
            )
            return
        }
        if (svgLocation(source) != null) return
        val location = ResourceLocation.tryParse(source) ?: return
        RenderSystem.setShaderTexture(0, location)
        UiTextureEffects.drawTexturedQuad(
            width,
            height,
            transform,
            opacity,
            flipY = false,
            fit = fit,
            texture = location,
            filter = filter,
            slice = slice,
            tint = tint,
        )
    }

    private fun svgLocation(source: String): ResourceLocation? {
        val location = ResourceLocation.tryParse(source) ?: return null
        return location.takeIf { it.path.endsWith(".svg", ignoreCase = true) }
    }

    private fun UiImageFit.svgRasterDrawFit(): UiImageFit {
        return when (this) {
            UiImageFit.NINE_SLICE,
            UiImageFit.THREE_SLICE_VERTICAL,
            UiImageFit.THREE_SLICE_HORIZONTAL,
                -> this

            UiImageFit.STRETCH,
            UiImageFit.NONE,
            UiImageFit.CONTAIN,
            UiImageFit.COVER,
                -> UiImageFit.STRETCH
        }
    }

    private fun UiColor.textShadowColor(effect: UiTextEffect, textColor: UiColor): UiColor {
        if (effect !is Shadow) return this
        return UiColor(
            red = red * textColor.red,
            green = green * textColor.green,
            blue = blue * textColor.blue,
            alpha = alpha * textColor.alpha,
        )
    }

    private fun drawItem(command: DrawItemCommand) {
        if (layerStack.isNotEmpty()) return
        if (isBackfaceHidden(
                command.rect.width, command.rect.height, effective(command.transform), command.backfaceVisibility
            )
        ) return
        val location = ResourceLocation.tryParse(command.item) ?: return
        val item = BuiltInRegistries.ITEM.getOptional(location).orElse(null) ?: return
        ItemStack(item).render(command.rect.x, command.rect.y, command.rect.width, command.rect.height)
    }

    private fun drawEntity(command: DrawEntityCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        if (layerStack.isEmpty()) {
            val entity = when (command.entity) {
                "player" -> Minecraft.getInstance().player
                else -> null
            }
            if (entity != null) {
                renderEntity(entity, command.rect)
                return
            }
        }
        drawEntityPlaceholder(command)
    }

    private fun drawEntityPlaceholder(command: DrawEntityCommand) {
        val transform = effective(command.transform)
        drawLocalPaint(
            command.rect.width,
            command.rect.height,
            0f,
            UiColor(0.09f, 0.12f, 0.16f, command.opacity),
            transform,
            command.filter
        )
        drawLocalBorder(
            command.rect.width,
            command.rect.height,
            0f,
            UiColor(0.28f, 0.42f, 0.58f, command.opacity).filtered(command.filter),
            transform
        )
    }

    private fun renderEntity(entity: LivingEntity, rect: UiRect) {
        val stack = PoseStack()
        val xOffset = rect.x + rect.width / 2f
        val yOffset = rect.y + rect.height
        stack.translate(xOffset.toDouble(), yOffset.toDouble(), 0.0)
        val scale = min(rect.width / entity.bbWidth, rect.height / entity.bbHeight) * 0.92f
        stack.mulPose(Axis.ZP.rotationDegrees(180f))
        stack.scale(scale, scale, scale)
        stack.mulPose(Quaternionf().rotateY(25f * Mth.DEG_TO_RAD))

        val light0 = Vector3f(-0.3f, 1f, 1f).normalize()
        val light1 = Vector3f(0.3f, -1f, -1f).normalize()
        RenderSystem.setShaderLights(light0, light1)

        val mc = Minecraft.getInstance()
        val dispatcher = mc.entityRenderDispatcher
        val buffers = mc.renderBuffers().bufferSource()
        dispatcher.setRenderShadow(false)
        RenderSystem.runAsFancy {
            dispatcher.render(entity, 0.0, 0.0, 0.0, 0f, 1f, stack, buffers, LightTexture.FULL_BRIGHT)
        }
        buffers.endBatch()
        dispatcher.setRenderShadow(true)
        Lighting.setupFor3DItems()
    }

    private fun drawWidget(command: DrawSliderCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        widgets.drawSlider(command, transform)
    }

    private fun drawWidget(command: DrawCheckboxCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        widgets.drawCheckbox(command, transform)
    }

    private fun drawWidget(command: DrawTextFieldChromeCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        widgets.drawTextFieldChrome(command, transform)
    }

    private fun pushClip(rect: UiRect, transform: UiMatrix4) {
        val local = transformedLocalRect(rect, effective(transform))
        clipStack.addLast(clipStack.lastOrNull()?.intersect(local) ?: local)
        applyScissor(clipStack.last())
    }

    private fun popClip() {
        if (clipStack.isNotEmpty()) clipStack.removeLast()
        clipStack.lastOrNull()?.let(::applyScissor) ?: disableScissor()
    }

    private fun applyScissor(rect: UiRect) {
        val layer = layerStack.lastOrNull()
        if (layer != null) {
            val bounds = rect.toScissorBounds(layer.scale, layer.scale)
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(
                layer.framebuffer.region.x + bounds.x,
                layer.framebuffer.region.y + layer.framebuffer.height - bounds.y - bounds.height,
                bounds.width,
                bounds.height,
            )
            return
        }
        val target = renderTarget
        if (target != null) {
            val scaleX = target.width / target.logicalWidth.coerceAtLeast(1f)
            val scaleY = target.height / target.logicalHeight.coerceAtLeast(1f)
            val bounds = rect.toScissorBounds(scaleX, scaleY)
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(
                target.x + bounds.x,
                target.y + target.height - bounds.y - bounds.height,
                bounds.width,
                bounds.height,
            )
            return
        }
        val window = Minecraft.getInstance().window
        val scaleX = window.width / window.guiScaledWidth.toFloat()
        val scaleY = window.height / window.guiScaledHeight.toFloat()
        val bounds = rect.toScissorBounds(scaleX, scaleY)
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            bounds.x,
            window.height - bounds.y - bounds.height,
            bounds.width,
            bounds.height,
        )
    }

    private fun UiRect.toScissorBounds(scaleX: Float, scaleY: Float): ScissorBounds {
        val left = floor(x * scaleX).toInt()
        val top = floor(y * scaleY).toInt()
        val right = ceil((x + width) * scaleX).toInt()
        val bottom = ceil((y + height) * scaleY).toInt()
        return ScissorBounds(
            x = left,
            y = top,
            width = (right - left).coerceAtLeast(0),
            height = (bottom - top).coerceAtLeast(0),
        )
    }

    private fun bindRootTarget() {
        val target = renderTarget
        if (target == null) {
            Minecraft.getInstance().mainRenderTarget.bindWrite(true)
            val window = Minecraft.getInstance().window
            GL11.glViewport(0, 0, window.width, window.height)
            restoreMainProjection()
            return
        }

        bindTarget(target.toState())
        configureLayerProjection(target.logicalWidth, target.logicalHeight)
    }

    private fun effective(transform: UiMatrix4): UiMatrix4 {
        val layer = layerStack.lastOrNull() ?: return transform
        return UiMatrix4.translation(layer.padding, layer.padding, 0f) * (layer.transform.inverse()
            ?: UiMatrix4.translation(-layer.rect.x, -layer.rect.y, 0f)) * transform
    }

    private fun localRect(rect: UiRect): UiRect = layerStack.lastOrNull()?.let {
            rect.copy(
                x = rect.x - it.rect.x + it.padding,
                y = rect.y - it.rect.y + it.padding,
            )
        } ?: rect

    private fun transformedLocalRect(rect: UiRect, transform: UiMatrix4): UiRect {
        val topLeft = transform.transform(rect.x, rect.y)
        val bottomLeft = transform.transform(rect.x, rect.y + rect.height)
        val bottomRight = transform.transform(rect.x + rect.width, rect.y + rect.height)
        val topRight = transform.transform(rect.x + rect.width, rect.y)
        val left = minOf(topLeft.x, bottomLeft.x, bottomRight.x, topRight.x)
        val top = minOf(topLeft.y, bottomLeft.y, bottomRight.y, topRight.y)
        val right = maxOf(topLeft.x, bottomLeft.x, bottomRight.x, topRight.x)
        val bottom = maxOf(topLeft.y, bottomLeft.y, bottomRight.y, topRight.y)
        return UiRect(left, top, (right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f))
    }

    private fun layerScale(): Float {
        return (renderTarget?.scale ?: Minecraft.getInstance().window.guiScale.toFloat()) * LayerSupersampling
    }

    private fun restoreClips(clips: List<UiRect>) {
        clipStack.clear()
        clipStack.addAll(clips)
        restoreActiveClip()
    }

    private fun restoreActiveClip() {
        clipStack.lastOrNull()?.let(::applyScissor) ?: disableScissor()
    }

    private fun bindTarget(target: RenderTargetState) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.framebufferId)
        GL11.glViewport(target.x, target.y, target.width, target.height)
    }

    private fun configureLayerProjection(width: Float, height: Float) {
        RenderSystem.setProjectionMatrix(
            Matrix4f().setOrtho(0f, width, height, 0f, -1000f, 1000f),
            VertexSorting.ORTHOGRAPHIC_Z,
        )
        val stack = RenderSystem.getModelViewStack()
        stack.setIdentity()
        RenderSystem.applyModelViewMatrix()
    }

    private fun restoreMainProjection() {
        if (!layerProjectionActive || layerStack.isNotEmpty()) return
        RenderSystem.restoreProjectionMatrix()
        val stack = RenderSystem.getModelViewStack()
        stack.popPose()
        RenderSystem.applyModelViewMatrix()
        layerProjectionActive = false
    }

    private fun currentTarget(): RenderTargetState {
        val layer = layerStack.lastOrNull()
        if (layer != null) {
            return RenderTargetState(
                framebufferId = layer.framebuffer.framebuffer,
                framebuffer = layer.framebuffer.atlas,
                x = layer.framebuffer.region.x,
                y = layer.framebuffer.region.y,
                width = layer.framebuffer.width,
                height = layer.framebuffer.height,
                logicalWidth = layer.rect.width + layer.padding * 2f,
                logicalHeight = layer.rect.height + layer.padding * 2f,
                scale = layer.scale,
            )
        }
        renderTarget?.let { return it.toState() }
        val window = Minecraft.getInstance().window
        val target = Minecraft.getInstance().mainRenderTarget
        return RenderTargetState(
            framebufferId = target.frameBufferId,
            framebuffer = null,
            x = 0,
            y = 0,
            width = window.width,
            height = window.height,
            logicalWidth = window.guiScaledWidth.toFloat(),
            logicalHeight = window.guiScaledHeight.toFloat(),
            scale = window.width / window.guiScaledWidth.toFloat(),
        )
    }

    private fun UiRenderTarget.toState(): RenderTargetState {
        return RenderTargetState(
            framebufferId = framebufferId,
            framebuffer = null,
            x = x,
            y = y,
            width = width,
            height = height,
            logicalWidth = logicalWidth,
            logicalHeight = logicalHeight,
            scale = scale,
        )
    }

}
