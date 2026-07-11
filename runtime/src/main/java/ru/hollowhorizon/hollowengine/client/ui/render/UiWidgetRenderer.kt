package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font.DisplayMode
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCheckboxVariant
import java.util.*
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class UiWidgetRenderer(
    private val imageDrawer: (Float, Float, String, Float, UiMatrix4, UiImageFit, UiFilterChain, UiInsets, UiColor) -> Unit,
    private val markTextBatchDirty: () -> Unit,
    private val flushTextBatch: () -> Unit,
) {
    private val scratchQuads = mutableListOf<UiBatchedQuad>()
    private val scratchTriangles = UiTriangleBatch()

    fun drawSlider(command: DrawSliderCommand, transform: UiMatrix4) {
        val width = command.rect.width
        val height = command.rect.height
        val trackY = (height - command.trackThickness) * 0.5f
        drawResolvedPaint(
            width,
            command.trackThickness,
            command.radius,
            command.trackPaint,
            command.opacity,
            transform.translated(0f, trackY),
            command.filter,
        )
        val activeWidth = width * command.fraction
        if (activeWidth > 0f) {
            drawResolvedPaint(
                activeWidth,
                command.trackThickness,
                command.radius,
                command.activeTrackPaint,
                command.opacity,
                transform.translated(0f, trackY),
                command.filter,
            )
        }
        val thumbX = (width * command.fraction - command.thumbWidth * 0.5f).coerceIn(
            0f,
            (width - command.thumbWidth).coerceAtLeast(0f)
        )
        val thumbY = (height - command.thumbHeight) * 0.5f
        val thumbTransform = transform.translated(thumbX, thumbY)
        drawResolvedPaint(
            command.thumbWidth,
            command.thumbHeight,
            command.thumbBorder.radius,
            command.thumbPaint,
            command.opacity,
            thumbTransform,
            command.filter,
        )
        val borderWidth = command.thumbBorder.width.left.resolve(command.thumbWidth)
        if (borderWidth > 0f && command.thumbBorder.color.alpha > 0f) {
            scratchTriangles.appendLocalBorder(
                command.thumbWidth,
                command.thumbHeight,
                command.thumbBorder.radius,
                borderWidth,
                command.thumbBorder.color.withOpacity(command.opacity),
                thumbTransform,
            )
        }
        flushScratchTriangles()
    }

    fun drawCheckbox(command: DrawCheckboxCommand, transform: UiMatrix4) {
        when (command.variant) {
            UiCheckboxVariant.SWITCH -> drawSwitch(command, transform)
            UiCheckboxVariant.RADIO -> drawRadio(command, transform)
            UiCheckboxVariant.CHECKBOX -> drawCheckboxBox(command, transform)
        }
        flushScratchTriangles()
    }

    private fun flushScratchQuads() {
        drawBatchedQuads(scratchQuads)
        scratchQuads.clear()
    }

    private fun flushScratchTriangles() {
        drawBatchedTriangles(scratchTriangles)
        scratchTriangles.clear()
    }

    private fun UiRect.translated(deltaX: Float, deltaY: Float): UiRect {
        return copy(x = x + deltaX, y = y + deltaY)
    }

    private fun UiRect.clipHorizontally(left: Float, right: Float): UiRect? {
        val clippedLeft = max(x, left)
        val clippedRight = min(x + width, right)
        if (clippedRight <= clippedLeft) return null
        return copy(x = clippedLeft, width = clippedRight - clippedLeft)
    }

    private fun drawCheckboxBox(command: DrawCheckboxCommand, transform: UiMatrix4) {
        val size = min(command.rect.width, command.rect.height)
        val local = centeredSquare(command, size, transform)
        scratchTriangles.appendLocalPaint(
            size,
            size,
            3f,
            UiColor(0.16f, 0.18f, 0.22f, command.opacity),
            local,
            command.filter,
        )
        if (!command.checked) return
        drawResolvedPaint(size, size, 3f, command.activePaint, command.opacity, local, command.filter)
        val markSize = size * 0.5f
        drawResolvedPaint(
            markSize,
            markSize,
            1.5f,
            command.markPaint,
            command.opacity,
            local.translated(size * 0.25f, size * 0.25f),
            command.filter,
        )
    }

    private fun drawRadio(command: DrawCheckboxCommand, transform: UiMatrix4) {
        val size = min(command.rect.width, command.rect.height)
        val local = centeredSquare(command, size, transform)
        scratchTriangles.appendLocalPaint(
            size,
            size,
            size * 0.5f,
            UiColor(0.16f, 0.18f, 0.22f, command.opacity),
            local,
            command.filter,
        )
        if (!command.checked) return
        drawResolvedPaint(size, size, size * 0.5f, command.activePaint, command.opacity, local, command.filter)
        val dot = size * 0.45f
        drawResolvedPaint(
            dot,
            dot,
            dot * 0.5f,
            command.markPaint,
            command.opacity,
            local.translated((size - dot) * 0.5f, (size - dot) * 0.5f),
            command.filter,
        )
    }

    private fun drawSwitch(command: DrawCheckboxCommand, transform: UiMatrix4) {
        val height = command.rect.height
        val width = maxOf(command.rect.width, height * 1.8f)
        val local = transform.translated((command.rect.width - width) * 0.5f, 0f)
        val track =
            if (command.checked) command.activePaint else UiResolvedPaint.Color(UiColor(0.16f, 0.18f, 0.22f, 1f))
        drawResolvedPaint(width, height, height * 0.5f, track, command.opacity, local, command.filter)
        val knob = (height - 4f).coerceAtLeast(1f)
        val knobX = if (command.checked) width - knob - 2f else 2f
        drawResolvedPaint(
            knob,
            knob,
            knob * 0.5f,
            command.markPaint,
            command.opacity,
            local.translated(knobX, 2f),
            command.filter,
        )
    }

    private fun centeredSquare(command: DrawCheckboxCommand, size: Float, transform: UiMatrix4): UiMatrix4 {
        val x = (command.rect.width - size) * 0.5f
        val y = (command.rect.height - size) * 0.5f
        return transform.translated(x, y)
    }

    private fun drawResolvedPaint(
        width: Float,
        height: Float,
        radius: Float,
        paint: UiResolvedPaint,
        opacity: Float,
        transform: UiMatrix4,
        filter: UiFilterChain,
    ) {
        when (paint) {
            UiResolvedPaint.None -> Unit
            is UiResolvedPaint.Color -> scratchTriangles.appendLocalPaint(
                width,
                height,
                radius,
                paint.color.withOpacity(opacity),
                transform,
                filter,
            )

            is UiResolvedPaint.LinearGradient -> scratchTriangles.appendLocalGradient(
                width,
                height,
                radius,
                paint.angleDegrees,
                paint.stops,
                opacity,
                transform,
                filter,
            )

            is UiResolvedPaint.RadialGradient -> scratchTriangles.appendLocalRadialGradient(
                width,
                height,
                radius,
                paint.gradient,
                opacity,
                transform,
                filter,
            )

            is UiResolvedPaint.Image -> {
                flushScratchTriangles()
                imageDrawer(
                    width,
                    height,
                    paint.source,
                    opacity,
                    transform,
                    UiImageFit.STRETCH,
                    filter,
                    UiInsets.Zero,
                    UiColor.White
                )
            }

            is UiResolvedPaint.Shader -> scratchTriangles.appendLocalPaint(
                width,
                height,
                radius,
                UiColor(0.2f, 0.2f, 0.24f, opacity),
                transform,
                filter,
            )
        }
    }

    private fun drawPlainText(
        text: String,
        localX: Float,
        localY: Float,
        fontSize: Float,
        color: UiColor,
        opacity: Float,
        transform: UiMatrix4,
        filter: UiFilterChain,
    ) {
        val mc = Minecraft.getInstance()
        RenderSystem.enableBlend()
        configureUiBlend()
        val xAxis = transform.transform(1f, 0f)
        val origin = transform.transform(0f, 0f)
        val yAxis = transform.transform(0f, 1f)
        val scaleX = sqrt((xAxis.x - origin.x) * (xAxis.x - origin.x) + (xAxis.y - origin.y) * (xAxis.y - origin.y))
        val scaleY = sqrt((yAxis.x - origin.x) * (yAxis.x - origin.x) + (yAxis.y - origin.y) * (yAxis.y - origin.y))
        val textOrigin = transform.transform(localX, localY)
        val pose = PoseStack()
        pose.translate(textOrigin.x.toDouble(), textOrigin.y.toDouble(), textOrigin.z.toDouble() - 10)
        val fontScale = fontSize / mc.font.lineHeight.toFloat()
        pose.scale(scaleX * fontScale, scaleY * fontScale, 1f)
        mc.font.drawInBatch(
            Component.literal(text).visualOrderText,
            0f,
            0f,
            color.withOpacity(opacity).filtered(filter).argb(),
            false,
            pose.last().pose(),
            mc.renderBuffers().bufferSource(),
            DisplayMode.SEE_THROUGH,
            0,
            15728880,
        )
        markTextBatchDirty()
    }
}

