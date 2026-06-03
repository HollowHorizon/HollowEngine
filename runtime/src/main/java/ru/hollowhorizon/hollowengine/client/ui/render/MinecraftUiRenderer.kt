package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
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
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.render.render
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.effects.*
import ru.hollowhorizon.hollowengine.client.utils.popPose
import ru.hollowhorizon.hollowengine.client.utils.pushPose
import ru.hollowhorizon.hollowengine.client.utils.setIdentity
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
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
        val u1: Float, val v1: Float
    )

    private fun finishLayer() {
        val layer = layerStack.removeLast()
        val parentLayer = layerStack.lastOrNull()

        restoreClips(layer.parentClips)

        val copiedSource = if (parentLayer != null || layer.filter.blurRadius() > 0f) copyLayerToScratch(layer) else null
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
            layer, source.texture, source.width, source.height,
            source.u0, source.v0, source.u1, source.v1,
            compositeFilter, transform
        )

        if (parentLayer == null) restoreMainProjection()

        blurredSource?.let(framebuffers::release)
        copiedSource?.let(framebuffers::release)
        RenderSystem.disableDepthTest()
    }


    private fun blurIfNeeded(copiedSource: UiFramebuffer?, blurRadius: Float): UiFramebuffer? {
        if (blurRadius <= 0f || copiedSource == null) return null
        return blurTexture(framebuffers, copiedSource.texture, copiedSource.width, copiedSource.height, blurRadius, copiedSource)
    }

    private fun resolveRenderSource(layer: LayerState, copied: UiFramebuffer?, blurred: UiFramebuffer?): RenderSource {
        val activeBuffer = blurred ?: copied

        return if (activeBuffer != null) {
            RenderSource(
                texture = activeBuffer.texture,
                width = activeBuffer.width,
                height = activeBuffer.height,
                u0 = 0f, v0 = 0f, u1 = 1f, v1 = 1f
            )
        } else {
            RenderSource(
                texture = layer.framebuffer.texture,
                width = layer.framebuffer.atlas.width,
                height = layer.framebuffer.atlas.height,
                u0 = layer.framebuffer.u0(), v0 = layer.framebuffer.v0(),
                u1 = layer.framebuffer.u1(), v1 = layer.framebuffer.v1()
            )
        }
    }

    private fun setupParentLayerContext(parentLayer: LayerState) {
        parentLayer.framebuffer.bind()
        configureLayerProjection(
            parentLayer.rect.width + parentLayer.padding * 2f,
            parentLayer.rect.height + parentLayer.padding * 2f
        )
        restoreActiveClip()
    }

    private fun setupRootLayerContext() {
        bindRootTarget()
        restoreActiveClip()
    }

    private fun calculateParentTransform(parentLayer: LayerState, layer: LayerState): UiMatrix4 {
        val parentInverse = parentLayer.transform.inverse()
            ?: UiMatrix4.translation(-parentLayer.rect.x, -parentLayer.rect.y, 0f)

        return UiMatrix4.translation(parentLayer.padding, parentLayer.padding, 0f) *
                parentInverse *
                layer.transform *
                UiMatrix4.translation(-layer.padding, -layer.padding, 0f)
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
        val now = TickHandler.time / 20f
        command.layout.lines.forEach { line ->
            drawTextLine(command, line, transform, scaleX, scaleY, now)
        }
        mc.renderBuffers().bufferSource().endBatch()
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

        if (!fontFamily.isNullOrBlank() && effects.isEmpty()) {
            drawMsdfTextRun(command, fragment, line, transform, scaleX, scaleY, now, fontSize, fontFamily)
            return
        }

        val hasLayer = effects.hasLayerEffects()
        val hasAnimated = effects.hasAnimatedEffects()

        if (!hasLayer && !hasAnimated) {
            drawSingleTextRun(
                command, fragment, transform, scaleX, scaleY,
                fragment.x - command.scrollOffset.x,
                line.y + fragment.y - command.scrollOffset.y,
                fontSize,
                fragment.style.color,
                command.opacity,
            )
            return
        }

        val layerEffects = effects.filter { it.isLayer }
        val animatedEffects = if (hasAnimated) effects.filter { it.isAnimated } else emptyList()

        val localX = fragment.x - command.scrollOffset.x
        val localY = line.y + fragment.y - command.scrollOffset.y

        if (hasAnimated) {
            drawAnimatedTextRun(command, fragment, transform, scaleX, scaleY, now, fontSize, localX, localY, animatedEffects, layerEffects)
            return
        }

        val effectiveColor = fragment.style.color
            ?: if (fragment.style.link != null) {
                val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
                if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
            } else {
                command.color
            }

        for (layerEffect in layerEffects) {
            val passes = UiTextEffectApplier.getLayerPasses(layerEffect)
            for (pass in passes) {
                val passColor = pass.colorOverride ?: effectiveColor
                drawSingleTextRun(
                    command, fragment, transform, scaleX, scaleY,
                    localX + pass.offsetX,
                    localY + pass.offsetY,
                    fontSize,
                    passColor, command.opacity,
                )
            }
        }

        drawSingleTextRun(
            command, fragment, transform, scaleX, scaleY,
            localX, localY, fontSize,
            fragment.style.color, command.opacity,
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
        colorOverride: UiColor?,
        alphaMultiplier: Float,
    ) {
        val mc = Minecraft.getInstance()
        val fontScale = fontSize / mc.font.lineHeight.toFloat()
        val origin = transform.transform(localX, localY)
        val pose = PoseStack()
        pose.translate(
            origin.x.toDouble(),
            origin.y.toDouble(),
            origin.z.toDouble() - 10
        )
        pose.scale(scaleX * fontScale, scaleY * fontScale, 1f)
        if (fragment.style.code) {
            drawLocalPaint(
                fragment.width,
                fragment.height,
                2f,
                UiColor(0f, 0f, 0f, 0.28f * command.opacity * alphaMultiplier),
                transform * UiMatrix4.translation(localX, localY, 0f),
                command.filter,
            )
        }
        val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
        val color = colorOverride
            ?: fragment.style.color
            ?: if (fragment.style.link != null) {
                if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
            } else {
                command.color
            }
        val finalAlpha = command.opacity * alphaMultiplier * color.alpha
        val finalColor = UiColor(color.red, color.green, color.blue, finalAlpha)
        val component = Component.literal(fragment.text).withStyle { style ->
            style
                .withBold(fragment.style.bold)
                .withItalic(fragment.style.italic)
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
    }

    private fun drawAnimatedTextRun(
        command: DrawTextCommand,
        fragment: UiTextRun,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
        now: Float,
        fontSize: Float,
        baseLocalX: Float,
        baseLocalY: Float,
        animatedEffects: List<UiTextEffect>,
        layerEffects: List<UiTextEffect>,
    ) {
        val mc = Minecraft.getInstance()
        val fontScale = fontSize / mc.font.lineHeight.toFloat()
        val totalChars = fragment.text.length

        for (charIndex in fragment.text.indices) {
            val char = fragment.text[charIndex]
            val charWidth = mc.font.width(char.toString()).toFloat() * fontScale
            val charPos = charIndex.toFloat() / totalChars.coerceAtLeast(1).toFloat()
            val prefixText = fragment.text.substring(0, charIndex)
            val prefixWidth = mc.font.width(prefixText).toFloat() * fontScale
            val charLocalX = baseLocalX + prefixWidth

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
                val effectiveColor = colorOverride
                    ?: if (fragment.style.link != null) {
                        val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
                        if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
                    } else {
                        command.color
                    }
                for (layerEffect in layerEffects) {
                    val passes = UiTextEffectApplier.getLayerPasses(layerEffect)
                    for (pass in passes) {
                        val passColor = pass.colorOverride ?: effectiveColor
                        val adjustedLocalX = charLocalX + charOffsetX + pass.offsetX
                        val adjustedLocalY = baseLocalY + charOffsetY + pass.offsetY
                        val origin = transform.transform(adjustedLocalX, adjustedLocalY)
                        val pose = PoseStack()
                        pose.translate(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble() - 1)
                        pose.scale(scaleX * fontScale, scaleY * fontScale, 1f)
                        val finalAlpha = command.opacity * alphaMul * passColor.alpha
                        val finalColor = UiColor(passColor.red, passColor.green, passColor.blue, finalAlpha)
                        mc.font.drawInBatch(
                            Component.literal(char.toString()).visualOrderText, 0f, 0f,
                            finalColor.filtered(command.filter).argb(), false,
                            pose.last().pose(), mc.renderBuffers().bufferSource(),
                            DisplayMode.SEE_THROUGH, 0, 15728880,
                        )
                    }
                }
            }

            val finalLocalX = charLocalX + charOffsetX
            val finalLocalY = baseLocalY + charOffsetY
            val origin = transform.transform(finalLocalX, finalLocalY)
            val pose = PoseStack()
            pose.translate(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
            pose.scale(scaleX * fontScale, scaleY * fontScale, 1f)

            val finalColor = colorOverride
                ?: if (fragment.style.link != null) {
                    val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
                    if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
                } else {
                    command.color
                }
            val finalAlpha = command.opacity * alphaMul * finalColor.alpha
            val color = UiColor(finalColor.red, finalColor.green, finalColor.blue, finalAlpha)

            mc.font.drawInBatch(
                Component.literal(char.toString()).visualOrderText, 0f, 0f,
                color.filtered(command.filter).argb(), false,
                pose.last().pose(), mc.renderBuffers().bufferSource(),
                DisplayMode.SEE_THROUGH, 0, 15728880,
            )
        }
    }

    private fun drawMsdfTextRun(
        command: DrawTextCommand,
        fragment: UiTextRun,
        line: UiTextLine,
        transform: UiMatrix4,
        scaleX: Float,
        scaleY: Float,
        now: Float,
        fontSize: Float,
        fontFamily: String,
    ) {
        UiMsdfFont.loadFont(fontFamily)
        val fontData = UiMsdfFont.getFontData(fontFamily) ?: return
        val atlasInfo = fontData.meta.atlas
        val metrics = fontData.meta.metrics
        val distanceRange = atlasInfo.distanceRange
        val atlasWidth = atlasInfo.width.toFloat()
        val atlasHeight = atlasInfo.height.toFloat()
        val scale = fontSize / atlasInfo.size
        val lineHeight = metrics.lineHeight * atlasInfo.size * scale

        val localX = fragment.x - command.scrollOffset.x
        val localY = line.y + fragment.y - command.scrollOffset.y

        val shader = ModShaders.MSDF_TEXT ?: run {
            drawTextRun(command, fragment, line, transform, scaleX, scaleY, now)
            return
        }

        UiMsdfFont.bindTexture(fontFamily)

        val tessellator = Tesselator.getInstance()
        val bufferBuilder = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR)

        val linkHovered = fragment.style.link != null && fragment.style.link == command.hoveredLink
        val baseColor = fragment.style.color
            ?: if (fragment.style.link != null) {
                if (linkHovered) UiColor(0.64f, 0.82f, 1f, 1f) else UiColor(0.34f, 0.67f, 1f, 1f)
            } else {
                command.color
            }
        val alpha = command.opacity
        val color = baseColor.withOpacity(alpha).filtered(command.filter)

        var penX = 0f
        for (char in fragment.text) {
            val glyph = fontData.glyphMap[char] ?: continue

            val ab = glyph.atlasBounds
            val pb = glyph.planeBounds
            val u0 = ab.left / atlasWidth
            val v0 = ab.bottom / atlasHeight
            val u1 = ab.right / atlasWidth
            val v1 = ab.top / atlasHeight

            val glyphWidth = (pb.right - pb.left) * scale
            val glyphHeight = (pb.top - pb.bottom) * scale
            val x0 = penX + pb.left * scale
            val y0 = (lineHeight - metrics.ascender * atlasInfo.size) * scale - pb.bottom * scale
            val x1 = x0 + glyphWidth
            val y1 = y0 + glyphHeight

            val origin = transform.transform(localX + x0, localY + y0)
            val topLeft = transform.transform(localX + x0, localY + y1)
            val topRight = transform.transform(localX + x1, localY + y1)
            val bottomRight = transform.transform(localX + x1, localY + y0)

            val r = color.red
            val g = color.green
            val b = color.blue
            val a = color.alpha

            bufferBuilder.addVertex(bottomRight.x, bottomRight.y, bottomRight.z).setColor(r, g, b, a).setUv(u1, v0)
            bufferBuilder.addVertex(topRight.x, topRight.y, topRight.z).setColor(r, g, b, a).setUv(u1, v1)
            bufferBuilder.addVertex(topLeft.x, topLeft.y, topLeft.z).setColor(r, g, b, a).setUv(u0, v1)
            bufferBuilder.addVertex(origin.x, origin.y, origin.z).setColor(r, g, b, a).setUv(u0, v0)

            penX += glyph.advance * scale
        }

        RenderSystem.setShader { shader }
        shader.safeGetUniform("DistanceRange")?.set(distanceRange)
        shader.safeGetUniform("Softness")?.set(0.15f)
        shader.safeGetUniform("OutlineWidth")?.set(0f)
        shader.safeGetUniform("OutlineColor")?.set(0f, 0f, 0f, 0f)
        shader.safeGetUniform("GlowRadius")?.set(0f)
        shader.safeGetUniform("GlowColor")?.set(0f, 0f, 0f, 0f)
        shader.safeGetUniform("ShadowOffset")?.set(0f, 0f)
        shader.safeGetUniform("ShadowColor")?.set(0f, 0f, 0f, 0f)
        shader.safeGetUniform("AtlasSize")?.set(atlasWidth, atlasHeight)
        shader.apply()
        BufferUploader.drawWithShader(bufferBuilder.buildOrThrow())
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
