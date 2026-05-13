package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.math.*

internal fun drawLocalBorder(width: Float, height: Float, radius: Float, color: UiColor, transform: UiMatrix4) {
    drawLocalBorder(width, height, radius, 1f, color, transform)
}

internal fun drawLocalBorder(width: Float, height: Float, radius: Float, thickness: Float, color: UiColor, transform: UiMatrix4) {
    val border = thickness.coerceAtLeast(1f)
    if (radius > 0f) {
        drawRoundedStroke(width, height, radius, border, color, transform)
        return
    }
    drawLocalPaint(width, border, 0f, color, transform, UiFilterChain.Empty)
    drawLocalPaint(width, border, 0f, color, transform * UiMatrix4.translation(0f, height - border, 0f), UiFilterChain.Empty)
    drawLocalPaint(border, height, 0f, color, transform, UiFilterChain.Empty)
    drawLocalPaint(border, height, 0f, color, transform * UiMatrix4.translation(width - border, 0f, 0f), UiFilterChain.Empty)
}

internal fun drawSolid(rect: UiRect, color: UiColor, transform: UiMatrix4, radius: Float = 0f) {
    if (radius > 0f) {
        drawLocalPaint(rect.width, rect.height, radius, color, transform * UiMatrix4.translation(rect.x, rect.y, 0f), UiFilterChain.Empty)
        return
    }
    RenderSystem.disableCull()
    RenderSystem.setShader(GameRenderer::getPositionColorShader)
    val tessellator = Tesselator.getInstance()
    val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
    val corners = rect.corners(transform)
    buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setColor(color.red, color.green, color.blue, color.alpha)
    buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setColor(color.red, color.green, color.blue, color.alpha)
    buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setColor(color.red, color.green, color.blue, color.alpha)
    buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setColor(color.red, color.green, color.blue, color.alpha)
    BufferUploader.drawWithShader(buffer.buildOrThrow())
}

internal fun drawLocalPaint(
    width: Float,
    height: Float,
    radius: Float,
    color: UiColor,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    val filtered = color.filtered(filter)
    if (radius > 0f) {
        drawRoundedFan(width, height, radius, transform) { _, _ -> filtered }
        return
    }
    RenderSystem.disableCull()
    RenderSystem.setShader(GameRenderer::getPositionColorShader)
    val tessellator = Tesselator.getInstance()
    val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
    val corners = localCorners(width, height, transform)
    buffer.addVertex(corners[0].x, corners[0].y, corners[0].z).setColor(filtered.red, filtered.green, filtered.blue, filtered.alpha)
    buffer.addVertex(corners[1].x, corners[1].y, corners[1].z).setColor(filtered.red, filtered.green, filtered.blue, filtered.alpha)
    buffer.addVertex(corners[2].x, corners[2].y, corners[2].z).setColor(filtered.red, filtered.green, filtered.blue, filtered.alpha)
    buffer.addVertex(corners[3].x, corners[3].y, corners[3].z).setColor(filtered.red, filtered.green, filtered.blue, filtered.alpha)
    BufferUploader.drawWithShader(buffer.buildOrThrow())
}

internal fun drawLocalGradient(
    width: Float,
    height: Float,
    radius: Float,
    angleDegrees: Float,
    stops: List<UiGradientStop>,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    drawRoundedFan(width, height, radius, transform) { x, y ->
        gradientColorAt(x, y, width, height, angleDegrees, stops).withOpacity(opacity).filtered(filter)
    }
}

internal fun drawShadow(
    width: Float,
    height: Float,
    radius: Float,
    shadow: UiShadow,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    RenderSystem.enableBlend()
    RenderSystem.defaultBlendFunc()
    val layers = max(2, min(18, (shadow.blur / 2f).roundToInt() + 1))
    for (index in layers downTo 1) {
        val progress = index.toFloat() / layers.toFloat()
        val expansion = shadow.spread + shadow.blur * progress
        val falloff = (1f - progress).coerceIn(0f, 1f)
        val alpha = shadow.color.alpha * opacity * falloff * falloff * 0.32f
        val color = shadow.color.copy(alpha = alpha).filtered(filter)
        val shadowTransform = transform * UiMatrix4.translation(shadow.offset.x - expansion, shadow.offset.y - expansion, shadow.offset.z)
        drawLocalPaint(width + expansion * 2f, height + expansion * 2f, radius + expansion, color, shadowTransform, UiFilterChain.Empty)
    }
}

internal fun isBackfaceHidden(
    width: Float,
    height: Float,
    transform: UiMatrix4,
    visibility: UiBackfaceVisibility,
): Boolean {
    if (visibility == UiBackfaceVisibility.VISIBLE) return false
    val corners = localCorners(width, height, transform)
    var area = 0f
    for (index in corners.indices) {
        val current = corners[index]
        val next = corners[(index + 1) % corners.size]
        area += current.x * next.y - current.y * next.x
    }
    return area >= 0f
}

internal fun localCorners(width: Float, height: Float, transform: UiMatrix4) = arrayOf(
    transform.transform(0f, 0f),
    transform.transform(0f, height),
    transform.transform(width, height),
    transform.transform(width, 0f),
)

internal fun UiColor.withOpacity(opacity: Float) = copy(alpha = alpha * opacity)

internal fun UiColor.argb(): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return a shl 24 or (r shl 16) or (g shl 8) or b
}

private fun drawRoundedFan(
    width: Float,
    height: Float,
    radius: Float,
    transform: UiMatrix4,
    colorAt: (Float, Float) -> UiColor,
) {
    RenderSystem.disableCull()
    RenderSystem.setShader(GameRenderer::getPositionColorShader)
    val tessellator = Tesselator.getInstance()
    val buffer = tessellator.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR)
    val centerX = width * 0.5f
    val centerY = height * 0.5f
    val center = transform.transform(centerX, centerY)
    val centerColor = colorAt(centerX, centerY)
    buffer.addVertex(center.x, center.y, center.z).setColor(centerColor.red, centerColor.green, centerColor.blue, centerColor.alpha)
    for ((x, y) in roundedPerimeter(width, height, radius)) {
        val point = transform.transform(x, y)
        val color = colorAt(x, y)
        buffer.addVertex(point.x, point.y, point.z).setColor(color.red, color.green, color.blue, color.alpha)
    }
    BufferUploader.drawWithShader(buffer.buildOrThrow())
}

private fun drawRoundedStroke(width: Float, height: Float, radius: Float, thickness: Float, color: UiColor, transform: UiMatrix4) {
    val inset = thickness.coerceAtLeast(1f)
    val innerWidth = width - inset * 2f
    val innerHeight = height - inset * 2f
    if (innerWidth <= 0f || innerHeight <= 0f) {
        drawRoundedFan(width, height, radius, transform) { _, _ -> color }
        return
    }
    val outer = roundedPerimeter(width, height, radius)
    val inner = roundedPerimeter(innerWidth, innerHeight, max(0f, radius - inset)).map { (x, y) -> x + inset to y + inset }
    RenderSystem.disableCull()
    RenderSystem.setShader(GameRenderer::getPositionColorShader)
    val tessellator = Tesselator.getInstance()
    val buffer = tessellator.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR)
    for (index in outer.indices) {
        val innerIndex = index.coerceAtMost(inner.lastIndex)
        val outerPoint = transform.transform(outer[index].first, outer[index].second)
        val innerPoint = transform.transform(inner[innerIndex].first, inner[innerIndex].second)
        buffer.addVertex(outerPoint.x, outerPoint.y, outerPoint.z).setColor(color.red, color.green, color.blue, color.alpha)
        buffer.addVertex(innerPoint.x, innerPoint.y, innerPoint.z).setColor(color.red, color.green, color.blue, color.alpha)
    }
    BufferUploader.drawWithShader(buffer.buildOrThrow())
}

private fun roundedPerimeter(width: Float, height: Float, radius: Float): List<Pair<Float, Float>> {
    val clamped = radius.coerceIn(0f, min(width, height) * 0.5f)
    if (clamped <= 0f) {
        return listOf(0f to 0f, 0f to height, width to height, width to 0f, 0f to 0f)
    }
    val segments = max(4, min(12, (clamped / 2f).roundToInt()))
    val corners = listOf(
        Corner(clamped, clamped, PI.toFloat() * 1.5f, PI.toFloat()),
        Corner(clamped, height - clamped, PI.toFloat(), PI.toFloat() * 0.5f),
        Corner(width - clamped, height - clamped, PI.toFloat() * 0.5f, 0f),
        Corner(width - clamped, clamped, 0f, -PI.toFloat() * 0.5f),
    )
    val points = mutableListOf<Pair<Float, Float>>()
    for (corner in corners) {
        for (index in 0..segments) {
            val progress = index.toFloat() / segments.toFloat()
            val angle = corner.start + (corner.end - corner.start) * progress
            points += corner.x + cos(angle) * clamped to corner.y + sin(angle) * clamped
        }
    }
    points += points.first()
    return points
}

private fun gradientColorAt(x: Float, y: Float, width: Float, height: Float, angleDegrees: Float, stops: List<UiGradientStop>): UiColor {
    if (stops.isEmpty()) return UiColor.Transparent
    if (stops.size == 1) return stops.first().color
    val radians = angleDegrees * PI.toFloat() / 180f
    val directionX = cos(radians)
    val directionY = sin(radians)
    val projection = (x - width * 0.5f) * directionX + (y - height * 0.5f) * directionY
    val extent = abs(directionX) * width * 0.5f + abs(directionY) * height * 0.5f
    val offset = if (extent <= 0f) 0f else (projection / extent + 1f) * 0.5f
    val clamped = offset.coerceIn(0f, 1f)
    val right = stops.firstOrNull { it.offset >= clamped } ?: stops.last()
    val left = stops.lastOrNull { it.offset <= clamped } ?: stops.first()
    if (left == right) return left.color
    val range = (right.offset - left.offset).coerceAtLeast(0.0001f)
    return left.color.interpolate(right.color, (clamped - left.offset) / range)
}

private fun UiRect.corners(transform: UiMatrix4) = arrayOf(
    transform.transform(x, y),
    transform.transform(x, y + height),
    transform.transform(x + width, y + height),
    transform.transform(x + width, y),
)

internal fun UiColor.filtered(filter: UiFilterChain): UiColor {
    val grayscale = filter.grayscaleAmount()
    if (grayscale <= 0f) return this
    val luminance = red * 0.2126f + green * 0.7152f + blue * 0.0722f
    return UiColor(
        red = red + (luminance - red) * grayscale,
        green = green + (luminance - green) * grayscale,
        blue = blue + (luminance - blue) * grayscale,
        alpha = alpha,
    )
}

private data class Corner(
    val x: Float,
    val y: Float,
    val start: Float,
    val end: Float,
)
