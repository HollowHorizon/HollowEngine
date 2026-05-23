package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexSorting
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.client.renderer.LightTexture
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
import ru.hollowhorizon.hollowengine.client.render.render
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.utils.popPose
import ru.hollowhorizon.hollowengine.client.utils.pushPose
import ru.hollowhorizon.hollowengine.client.utils.setIdentity
import java.util.*
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

class MinecraftUiRenderer {
    private val framebuffers = UiFramebufferPool()
    private val layerStack = ArrayDeque<LayerState>()
    private val clipStack = ArrayDeque<UiRect>()
    private var layerProjectionActive = false
    private var renderTarget: UiRenderTarget? = null

    fun render(commands: List<UiRenderCommand>, target: UiRenderTarget? = null) {
        val previousTarget = renderTarget
        renderTarget = target
        try {
            prepareFramebuffers(commands)
            RenderSystem.enableBlend()
            configureUiBlend()
            RenderSystem.disableDepthTest()
            GL11.glDepthMask(false)
            renderTarget?.let {
                bindTarget(it.toState())
                configureLayerProjection(it.logicalWidth, it.logicalHeight)
            }
            commands.forEach(::render)
            disableScissor()
            while (layerStack.isNotEmpty()) finishLayer()
            GL11.glDepthMask(true)
        } finally {
            renderTarget = previousTarget
        }
    }

    fun close() {
        while (layerStack.isNotEmpty()) finishLayer()
        framebuffers.close()
    }

    private fun render(command: UiRenderCommand) {
        when (command) {
            is BeginLayerCommand -> beginLayer(command)
            is EndLayerCommand -> finishLayer()
            is DrawBackdropFilterCommand -> drawBackdropFilter(command)
            is DrawShadowCommand -> drawShadow(command)
            is PushClipCommand -> pushClip(command.rect)
            is PopClipCommand -> popClip()
            is DrawBoxCommand -> drawBox(command)
            is DrawTextCommand -> drawText(command)
            is DrawImageCommand -> drawImage(command)
            is DrawItemCommand -> drawItem(command)
            is DrawEntityCommand -> drawEntity(command)
            is DrawCanvasCommand -> drawCanvasPlaceholder(command)
            is DrawScrollbarCommand -> drawScrollbar(command)
        }
    }

    private fun prepareFramebuffers(commands: List<UiRenderCommand>) {
        val scale = layerScale()
        val requests = commands.mapNotNull { command ->
            if (command !is BeginLayerCommand) return@mapNotNull null
            val padding = layerPadding(command)
            val width = ceil((command.rect.width + padding * 2f) * scale).toInt().coerceAtLeast(1)
            val height = ceil((command.rect.height + padding * 2f) * scale).toInt().coerceAtLeast(1)
            UiLayerRequest(width, height)
        }
        framebuffers.beginFrame(
            requests,
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
                transform = command.transform,
                framebuffer = framebuffer,
                parentClips = parentClips,
                scale = scale,
                filter = command.filter,
                backfaceVisibility = command.backfaceVisibility,
                padding = padding,
            )
        )
        clipStack.clear()
    }

    private fun finishLayer() {
        val layer = layerStack.removeLast()
        val parentLayer = layerStack.lastOrNull()
        restoreClips(layer.parentClips)
        val blurRadius = layer.filter.blurRadius()
        val copiedSource = if (parentLayer != null || blurRadius > 0f) copyLayerToScratch(layer) else null
        val blurredSource = if (blurRadius > 0f && copiedSource != null) blurTexture(
            framebuffers,
            copiedSource.texture,
            copiedSource.width,
            copiedSource.height,
            blurRadius,
            copiedSource
        ) else null
        val sourceTexture = blurredSource?.texture ?: copiedSource?.texture ?: layer.framebuffer.texture
        val sourceWidth = blurredSource?.width ?: copiedSource?.width ?: layer.framebuffer.atlas.width
        val sourceHeight = blurredSource?.height ?: copiedSource?.height ?: layer.framebuffer.atlas.height
        val sourceU0 = if (copiedSource == null) layer.framebuffer.u0() else 0f
        val sourceV0 = if (copiedSource == null) layer.framebuffer.v0() else 0f
        val sourceU1 = if (copiedSource == null) layer.framebuffer.u1() else 1f
        val sourceV1 = if (copiedSource == null) layer.framebuffer.v1() else 1f
        val compositeFilter = layer.filter.withoutBlur()
        if (parentLayer != null) {
            finishWithParent(
                parentLayer, layer,
                sourceTexture, sourceWidth, sourceHeight,
                sourceU0, sourceV0, sourceU1, sourceV1,
                compositeFilter
            )
        } else {
            finishWithoutParent(
                layer, sourceTexture, sourceWidth, sourceHeight,
                sourceU0, sourceV0, sourceU1, sourceV1, compositeFilter
            )
        }
        blurredSource?.let(framebuffers::release)
        copiedSource?.let(framebuffers::release)
        RenderSystem.disableDepthTest()
    }

    private fun finishWithoutParent(
        layer: LayerState,
        sourceTexture: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        sourceU0: Float,
        sourceV0: Float,
        sourceU1: Float,
        sourceV1: Float,
        compositeFilter: UiFilterChain,
    ) {
        bindRootTarget()
        restoreActiveClip()
        val transform = layer.transform * UiMatrix4.translation(-layer.padding, -layer.padding, 0f)
        drawLayerTexture(
            layer,
            sourceTexture,
            sourceWidth,
            sourceHeight,
            sourceU0,
            sourceV0,
            sourceU1,
            sourceV1,
            compositeFilter,
            transform,
        )
        restoreMainProjection()
    }

    private fun finishWithParent(
        parentLayer: LayerState,
        layer: LayerState,
        sourceTexture: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        sourceU0: Float,
        sourceV0: Float,
        sourceU1: Float,
        sourceV1: Float,
        compositeFilter: UiFilterChain,
    ) {
        parentLayer.framebuffer.bind()
        configureLayerProjection(
            parentLayer.rect.width + parentLayer.padding * 2f,
            parentLayer.rect.height + parentLayer.padding * 2f
        )
        restoreActiveClip()
        val parentInverse =
            parentLayer.transform.inverse() ?: UiMatrix4.translation(-parentLayer.rect.x, -parentLayer.rect.y, 0f)
        val transform = UiMatrix4.translation(parentLayer.padding, parentLayer.padding, 0f) *
                parentInverse *
                layer.transform *
                UiMatrix4.translation(-layer.padding, -layer.padding, 0f)
        drawLayerTexture(
            layer,
            sourceTexture,
            sourceWidth,
            sourceHeight,
            sourceU0,
            sourceV0,
            sourceU1,
            sourceV1,
            compositeFilter,
            transform,
        )
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
        UiTextureEffects.drawTexturedRegion(
            texture = texture,
            width = width,
            height = height,
            transform = transform,
            opacity = 1f,
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

            is UiResolvedPaint.Image -> drawImage(
                command.rect.width,
                command.rect.height,
                paint.source,
                command.opacity,
                transform,
                fit = command.fit,
                slice = command.slice,
                filter = command.filter
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
                command.rect.width,
                command.rect.height,
                command.border.radius,
                borderWidth,
                borderColor,
                transform
            )
        }
    }

    private fun drawText(command: DrawTextCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        val mc = Minecraft.getInstance()
        val xAxis = transform.transform(1f, 0f)
        val origin = transform.transform(0f, 0f)
        val yAxis = transform.transform(0f, 1f)
        val scaleX = sqrt((xAxis.x - origin.x) * (xAxis.x - origin.x) + (xAxis.y - origin.y) * (xAxis.y - origin.y))
        val scaleY = sqrt((yAxis.x - origin.x) * (yAxis.x - origin.x) + (yAxis.y - origin.y) * (yAxis.y - origin.y))
        command.layout.lines.forEach { line ->
            drawTextLine(command, line, transform, scaleX, scaleY)
        }
        mc.renderBuffers().bufferSource().endBatch()
    }

    private fun drawTextLine(
        command: DrawTextCommand,
        line: UiTextLine,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
    ) {
        if (line.fragments.isNotEmpty()) {
            line.fragments.forEach { fragment ->
                when (fragment) {
                    is UiInlineImageRun -> drawInlineImage(command, fragment, line, transform)
                    is UiTextRun -> drawTextRun(command, fragment, line, transform, scaleX, scaleY)
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
            drawTextRun(command, fragment, line, transform, scaleX, scaleY)
        }
    }

    private fun drawTextRun(
        command: DrawTextCommand,
        fragment: UiTextRun,
        line: UiTextLine,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
    ) {
        val mc = Minecraft.getInstance()
        val fontSize = fragment.style.resolvedFontSize(command.fontSize)
        val fontScale = fontSize / mc.font.lineHeight.toFloat()
        val localX = fragment.x - command.scrollOffset.x
        val localY = line.y + fragment.y - command.scrollOffset.y
        val origin = transform.transform(localX, localY)
        val pose = PoseStack()
        pose.translate(
            origin.x.toDouble(),
            origin.y.toDouble(),
            origin.z.toDouble()
        )
        pose.scale(scaleX * fontScale, scaleY * fontScale, 1f)
        if (fragment.style.code) {
            drawLocalPaint(
                fragment.width,
                fragment.height,
                2f,
                UiColor(0f, 0f, 0f, 0.28f * command.opacity),
                transform * UiMatrix4.translation(localX, localY, 0f),
                command.filter,
            )
        }
        val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
        val color = fragment.style.color
            ?: if (fragment.style.link != null) {
                if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
            } else {
                command.color
            }
        val component = Component.literal(fragment.text).withStyle { style ->
            style
                .withBold(fragment.style.bold)
                .withItalic(fragment.style.italic)
                .withUnderlined(fragment.style.underline || fragment.style.link != null)
                .withStrikethrough(fragment.style.strikethrough)
                .withColor(TextColor.fromRgb(color.argb() and 0xFFFFFF))
        }
        mc.font.drawInBatch(
            component.visualOrderText,
            0f,
            0f,
            color.withOpacity(command.opacity).filtered(command.filter).argb(),
            false,
            pose.last().pose(),
            mc.renderBuffers().bufferSource(),
            DisplayMode.SEE_THROUGH,
            0,
            15728880,
        )
    }

    private fun drawInlineImage(
        command: DrawTextCommand,
        fragment: UiInlineImageRun,
        line: UiTextLine,
        transform: UiMatrix4,
    ) {
        drawImage(
            fragment.width,
            fragment.height,
            fragment.image.source,
            command.opacity,
            transform * UiMatrix4.translation(
                fragment.x - command.scrollOffset.x,
                line.y + fragment.y - command.scrollOffset.y,
                0f
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
    ) {
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
        )
    }

    private fun drawItem(command: DrawItemCommand) {
        if (layerStack.isNotEmpty()) return
        if (isBackfaceHidden(
                command.rect.width,
                command.rect.height,
                effective(command.transform),
                command.backfaceVisibility
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

    private fun drawCanvasPlaceholder(command: DrawCanvasCommand) {
        val transform = effective(command.transform)
        if (isBackfaceHidden(command.rect.width, command.rect.height, transform, command.backfaceVisibility)) return
        drawLocalPaint(
            command.rect.width,
            command.rect.height,
            0f,
            UiColor(0.08f, 0.08f, 0.1f, command.opacity),
            transform,
            command.filter
        )
        drawLocalPaint(
            command.rect.width - 16f,
            1f,
            0f,
            UiColor(0.4f, 0.7f, 0.9f, command.opacity),
            transform * UiMatrix4.translation(8f, command.rect.height * 0.5f, 0f),
            command.filter
        )
    }

    private fun drawScrollbar(command: DrawScrollbarCommand) {
        drawScrollbarPart(
            localRect(command.track),
            command.trackPaint,
            command.trackBorder,
            command.trackFit,
            command.trackSlice,
            command.opacity,
        )
        drawScrollbarPart(
            localRect(command.thumb),
            command.thumbPaint,
            command.thumbBorder,
            command.thumbFit,
            command.thumbSlice,
            command.opacity,
        )
    }

    private fun drawScrollbarPart(
        rect: UiRect,
        paint: UiResolvedPaint,
        border: UiBorder,
        fit: UiImageFit,
        slice: UiInsets,
        opacity: Float,
    ) {
        val transform = UiMatrix4.translation(rect.x, rect.y, 0f)
        when (paint) {
            UiResolvedPaint.None -> Unit
            is UiResolvedPaint.Color -> drawLocalPaint(
                rect.width,
                rect.height,
                border.radius,
                paint.color.withOpacity(opacity),
                transform,
                UiFilterChain.Empty,
            )

            is UiResolvedPaint.LinearGradient -> drawLocalGradient(
                rect.width,
                rect.height,
                border.radius,
                paint.angleDegrees,
                paint.stops,
                opacity,
                transform,
                UiFilterChain.Empty,
            )

            is UiResolvedPaint.Image -> drawImage(
                rect.width,
                rect.height,
                paint.source,
                opacity,
                transform,
                fit = fit,
                slice = slice,
            )

            is UiResolvedPaint.Shader -> drawLocalPaint(
                rect.width,
                rect.height,
                border.radius,
                UiColor(0.2f, 0.2f, 0.24f, opacity),
                transform,
                UiFilterChain.Empty,
            )
        }
        val borderWidth = border.width.left.resolve(rect.width)
        if (borderWidth > 0f && border.color.alpha > 0f) {
            drawLocalBorder(
                rect.width,
                rect.height,
                border.radius,
                borderWidth.coerceAtLeast(1f),
                border.color.withOpacity(opacity),
                transform,
            )
        }
    }

    private fun pushClip(rect: UiRect) {
        val local = localRect(rect)
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
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(
                layer.framebuffer.region.x + (rect.x * layer.scale).toInt(),
                layer.framebuffer.region.y + (layer.framebuffer.height - (rect.y + rect.height) * layer.scale).toInt(),
                (rect.width * layer.scale).toInt().coerceAtLeast(0),
                (rect.height * layer.scale).toInt().coerceAtLeast(0),
            )
            return
        }
        val target = renderTarget
        if (target != null) {
            val scaleX = target.width / target.logicalWidth.coerceAtLeast(1f)
            val scaleY = target.height / target.logicalHeight.coerceAtLeast(1f)
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(
                target.x + (rect.x * scaleX).toInt(),
                target.y + target.height - ((rect.y + rect.height) * scaleY).toInt(),
                (rect.width * scaleX).toInt().coerceAtLeast(0),
                (rect.height * scaleY).toInt().coerceAtLeast(0),
            )
            return
        }
        val window = Minecraft.getInstance().window
        val scaleX = window.width / window.guiScaledWidth.toFloat()
        val scaleY = window.height / window.guiScaledHeight.toFloat()
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            (rect.x * scaleX).toInt(),
            (window.height - (rect.y + rect.height) * scaleY).toInt(),
            (rect.width * scaleX).toInt().coerceAtLeast(0),
            (rect.height * scaleY).toInt().coerceAtLeast(0),
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
        return UiMatrix4.translation(layer.padding, layer.padding, 0f) *
                (layer.transform.inverse() ?: UiMatrix4.translation(-layer.rect.x, -layer.rect.y, 0f)) *
                transform
    }

    private fun localRect(rect: UiRect): UiRect = layerStack.lastOrNull()
        ?.let {
            rect.copy(
                x = rect.x - it.rect.x + it.padding,
                y = rect.y - it.rect.y + it.padding,
            )
        }
        ?: rect

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
