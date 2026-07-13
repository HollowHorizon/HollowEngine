package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.shape.SvgPathShape
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathStrokeLineCap
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathStrokeLineJoin
import ru.hollowhorizon.hollowengine.client.ui.shape.UiShapeSize
import ru.hollowhorizon.hollowengine.client.ui.shape.UiSvgFilterEffect
import ru.hollowhorizon.hollowengine.client.ui.shape.UiSvgPathDocument
import ru.hollowhorizon.hollowengine.client.ui.style.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Geometry coverage applied to a canvas draw operation. */
sealed interface UiDrawStyle {
    data object Fill : UiDrawStyle

    data class Stroke(
        val width: Float = 1f,
        val lineCap: UiPathStrokeLineCap = UiPathStrokeLineCap.Round,
        val lineJoin: UiPathStrokeLineJoin = UiPathStrokeLineJoin.Round,
    ) : UiDrawStyle {
        init {
            require(width >= 0f) { "Stroke width cannot be negative" }
        }
    }
}

/**
 * Records local drawing operations for one laid-out UI node. Commands are streamed directly into
 * the frame renderer: the API has retained canvas semantics without allocating another frame tree.
 */
interface UiCanvasDrawScope {
    val size: UiShapeSize

    val bounds: UiRect
        get() = UiRect(0f, 0f, size.width, size.height)

    fun drawRect(
        rect: UiRect,
        paint: UiPaint,
        radius: Float = 0f,
        border: UiBorder = UiBorder(),
        tint: UiColor = UiColor.White,
        fit: UiImageFit = UiImageFit.STRETCH,
        slice: UiInsets = UiInsets.Zero,
    )

    fun drawRect(
        paint: UiPaint,
        radius: Float = 0f,
        border: UiBorder = UiBorder(),
        tint: UiColor = UiColor.White,
        fit: UiImageFit = UiImageFit.STRETCH,
        slice: UiInsets = UiInsets.Zero,
    ) = drawRect(bounds, paint, radius, border, tint, fit, slice)

    fun drawShape(
        shape: Shape,
        rect: UiRect,
        paint: UiPaint,
        style: UiDrawStyle = UiDrawStyle.Fill,
    )

    fun drawShape(
        shape: Shape,
        paint: UiPaint,
        style: UiDrawStyle = UiDrawStyle.Fill,
    ) = drawShape(shape, bounds, paint, style)

    fun drawSvg(document: UiSvgPathDocument, rect: UiRect)

    fun drawSvg(document: UiSvgPathDocument) = drawSvg(document, bounds)
}

enum class UiCanvasDrawLayer {
    BEHIND,
    OVERLAY,
}

class UiCanvasModifier internal constructor(
    val layer: UiCanvasDrawLayer,
    key: Any?,
    internal val block: UiCanvasDrawScope.() -> Unit,
) : Modifier {
    private val equalityKey = key ?: block

    override fun equals(other: Any?): Boolean =
        other is UiCanvasModifier && layer == other.layer && equalityKey == other.equalityKey

    override fun hashCode(): Int = 31 * layer.hashCode() + equalityKey.hashCode()
}

fun Modifier.drawBehind(
    key: Any? = null,
    block: UiCanvasDrawScope.() -> Unit,
): Modifier = this then UiCanvasModifier(UiCanvasDrawLayer.BEHIND, key, block)

fun Modifier.draw(
    key: Any? = null,
    block: UiCanvasDrawScope.() -> Unit,
): Modifier = this then UiCanvasModifier(UiCanvasDrawLayer.OVERLAY, key, block)

internal class UiCommandCanvasScope(
    private val node: UiNode,
    private val layoutNode: UiLayoutNode,
    private val opacity: Float,
    private val filter: UiFilterChain,
    private val backfaceVisibility: UiBackfaceVisibility,
    private val phase: UiRenderPhase,
    private val sink: UiRenderSink,
) : UiCanvasDrawScope {
    override val size = UiShapeSize(layoutNode.rect.width, layoutNode.rect.height)

    override fun drawRect(
        rect: UiRect,
        paint: UiPaint,
        radius: Float,
        border: UiBorder,
        tint: UiColor,
        fit: UiImageFit,
        slice: UiInsets,
    ) {
        if (!rect.isDrawable() || opacity <= 0f) return
        val resolvedPaint = paint.resolve()
        if (!resolvedPaint.hasVisiblePixels() && !border.hasVisiblePixels()) return
        sink += DrawBoxCommand(
            node = node,
            rect = rect.toCommandRect(),
            paint = resolvedPaint,
            border = border.copy(radius = radius.coerceAtLeast(0f)),
            shadows = emptyList(),
            opacity = opacity,
            tint = tint,
            transform = layoutNode.worldTransform.translated(rect.x, rect.y),
            renderToFramebuffer = false,
            fit = fit,
            slice = slice,
            filter = filter,
            backfaceVisibility = backfaceVisibility,
            phase = phase,
        )
    }

    override fun drawShape(
        shape: Shape,
        rect: UiRect,
        paint: UiPaint,
        style: UiDrawStyle,
    ) {
        if (!rect.isDrawable() || opacity <= 0f || paint == UiPaint.None) return
        val resolvedPaint = paint.resolve()
        if (!resolvedPaint.hasVisiblePixels()) return
        val fill = if (style == UiDrawStyle.Fill) resolvedPaint else UiResolvedPaint.None
        val stroke = if (style is UiDrawStyle.Stroke) resolvedPaint else UiResolvedPaint.None
        sink += DrawShapeCommand(
            node = node,
            rect = rect.toCommandRect(),
            shape = shape,
            fill = fill,
            stroke = stroke,
            strokeWidth = (style as? UiDrawStyle.Stroke)?.width ?: 0f,
            opacity = opacity,
            transform = layoutNode.worldTransform.translated(rect.x, rect.y),
            filter = filter,
            backfaceVisibility = backfaceVisibility,
            phase = phase,
            strokeLineCap = (style as? UiDrawStyle.Stroke)?.lineCap ?: UiPathStrokeLineCap.Round,
            strokeLineJoin = (style as? UiDrawStyle.Stroke)?.lineJoin ?: UiPathStrokeLineJoin.Round,
        )
    }

    override fun drawSvg(document: UiSvgPathDocument, rect: UiRect) {
        if (!rect.isDrawable()) return
        val viewBox = document.viewBox
        val scale = min(
            rect.width / viewBox.width.coerceAtLeast(0.0001f),
            rect.height / viewBox.height.coerceAtLeast(0.0001f),
        )
        val contentRect = UiRect(
            x = rect.x + (rect.width - viewBox.width * scale) * 0.5f,
            y = rect.y + (rect.height - viewBox.height * scale) * 0.5f,
            width = viewBox.width * scale,
            height = viewBox.height * scale,
        )
        val scaleX = contentRect.width / viewBox.width.coerceAtLeast(0.0001f)
        val scaleY = contentRect.height / viewBox.height.coerceAtLeast(0.0001f)
        val effectScale = max(abs(scaleX), abs(scaleY))
        for (element in document.elements) {
            val shape = SvgPathShape(element.path, viewBox)
            for (effect in element.filterEffects) {
                if (effect !is UiSvgFilterEffect.DropShadow) continue
                submitSvgShape(
                    shape = shape,
                    rect = contentRect,
                    color = effect.color,
                    offsetX = effect.offsetX * scaleX,
                    offsetY = effect.offsetY * scaleY,
                    blurRadius = effect.standardDeviation * effectScale,
                )
            }
        }
        for (element in document.elements) {
            val color = element.paint ?: continue
            val shape = SvgPathShape(element.path, viewBox)
            var blurRadius = 0f
            for (effect in element.filterEffects) {
                when (effect) {
                    is UiSvgFilterEffect.GaussianBlur -> {
                        blurRadius = max(blurRadius, effect.standardDeviation * effectScale)
                    }

                    is UiSvgFilterEffect.DropShadow -> Unit
                }
            }
            submitSvgShape(
                shape = shape,
                rect = contentRect,
                color = color,
                blurRadius = blurRadius,
            )
        }
    }

    private fun submitSvgShape(
        shape: Shape,
        rect: UiRect,
        color: UiColor,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        blurRadius: Float = 0f,
    ) {
        sink += DrawShapeCommand(
            node = node,
            rect = rect.toCommandRect(),
            shape = shape,
            fill = UiResolvedPaint.Color(color),
            stroke = UiResolvedPaint.None,
            strokeWidth = 0f,
            opacity = opacity,
            transform = layoutNode.worldTransform.translated(rect.x + offsetX, rect.y + offsetY),
            filter = filter,
            backfaceVisibility = backfaceVisibility,
            phase = phase,
            blurRadius = blurRadius.coerceAtLeast(0f),
        )
    }

    private fun UiRect.toCommandRect() = UiRect(
        x = layoutNode.rect.x + x,
        y = layoutNode.rect.y + y,
        width = width,
        height = height,
    )

    private fun UiRect.isDrawable(): Boolean =
        width.isFinite() && height.isFinite() && width > 0f && height > 0f
}
