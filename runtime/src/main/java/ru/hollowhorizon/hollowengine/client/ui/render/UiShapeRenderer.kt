package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.math.*

internal data class UiBatchedQuad(
    val width: Float,
    val height: Float,
    val transform: UiMatrix4,
    val colors: List<UiColor>,
)

internal fun drawBatchedQuads(quads: List<UiBatchedQuad>) {
    if (quads.isEmpty()) return
    withCullStatePreserved {
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        quads.forEach { quad ->
            val corners = localCorners(quad.width, quad.height, quad.transform)
            corners.forEachIndexed { index, corner ->
                val color = quad.colors[index.coerceAtMost(quad.colors.lastIndex)]
                buffer.addVertex(corner.x, corner.y, corner.z).setColor(color.red, color.green, color.blue, color.alpha)
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }
}

internal fun solidQuad(width: Float, height: Float, color: UiColor, transform: UiMatrix4): UiBatchedQuad {
    return UiBatchedQuad(width, height, transform, List(4) { color })
}

internal fun gradientQuad(
    width: Float,
    height: Float,
    angleDegrees: Float,
    stops: List<UiGradientStop>,
    opacity: Float,
    transform: UiMatrix4,
): UiBatchedQuad {
    return UiBatchedQuad(
        width = width,
        height = height,
        transform = transform,
        colors = listOf(
            gradientColorAt(0f, 0f, width, height, angleDegrees, stops).withOpacity(opacity),
            gradientColorAt(0f, height, width, height, angleDegrees, stops).withOpacity(opacity),
            gradientColorAt(width, height, width, height, angleDegrees, stops).withOpacity(opacity),
            gradientColorAt(width, 0f, width, height, angleDegrees, stops).withOpacity(opacity),
        ),
    )
}

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
    withCullStatePreserved {
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
    withCullStatePreserved {
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
    drawProjectedShadow(width, height, radius, shadow, opacity, transform, filter)
}

internal fun drawProjectedShadow(
    width: Float,
    height: Float,
    radius: Float,
    shadow: UiShadow,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    val outline = roundedPerimeter(width, height, radius).map { (x, y) -> transform.transform(x, y) }
    val corners = localCorners(width, height, transform)
    val projectedScale = projectedScale(corners, width, height)
    val facing = facingAmount(width, height, transform)
    val elevation = corners.map { it.z }.average().toFloat().coerceAtLeast(0f) + shadow.offset.z.coerceAtLeast(0f)
    val distanceFactor = 1f + elevation * 0.025f
    val angleFactor = 1f + (1f - facing) * 0.45f
    val spread = shadow.spread * projectedScale
    val blur = shadow.blur * projectedScale * distanceFactor * angleFactor
    val castX = shadow.offset.x
    val castY = shadow.offset.y
    val alpha = opacity * (0.78f + facing * 0.42f) / distanceFactor
    drawProjectedShadowGradient(outline, spread, blur, castX, castY, shadow.color.withOpacity(alpha).filtered(filter))
}

internal fun isBackfaceHidden(
    width: Float,
    height: Float,
    transform: UiMatrix4,
    visibility: UiBackfaceVisibility,
): Boolean {
    if (visibility == UiBackfaceVisibility.VISIBLE) return false
    val xAxis = transform.transform(1f, 0f, 0f)
    val yAxis = transform.transform(0f, 1f, 0f)
    val origin = transform.transform(0f, 0f, 0f)
    val ax = xAxis.x - origin.x
    val ay = xAxis.y - origin.y
    val bx = yAxis.x - origin.x
    val by = yAxis.y - origin.y
    val normalZ = ax * by - ay * bx
    return normalZ < 0f
}

internal fun localCorners(width: Float, height: Float, transform: UiMatrix4) = arrayOf(
    transform.transform(0f, 0f),
    transform.transform(0f, height),
    transform.transform(width, height),
    transform.transform(width, 0f),
)

private fun projectedScale(corners: Array<UiVec3>, width: Float, height: Float): Float {
    val top = distance(corners[0], corners[3]) / width.coerceAtLeast(1f)
    val bottom = distance(corners[1], corners[2]) / width.coerceAtLeast(1f)
    val left = distance(corners[0], corners[1]) / height.coerceAtLeast(1f)
    val right = distance(corners[3], corners[2]) / height.coerceAtLeast(1f)
    return ((top + bottom + left + right) * 0.25f).coerceIn(0.25f, 4f)
}

private fun distance(a: UiVec3, b: UiVec3): Float {
    val x = a.x - b.x
    val y = a.y - b.y
    return sqrt(x * x + y * y)
}

private fun facingAmount(width: Float, height: Float, transform: UiMatrix4): Float {
    val origin = transform.transform(width * 0.5f, height * 0.5f, 0f)
    val xAxis = transform.transform(width * 0.5f + 1f, height * 0.5f, 0f)
    val yAxis = transform.transform(width * 0.5f, height * 0.5f + 1f, 0f)
    val ax = xAxis.x - origin.x
    val ay = xAxis.y - origin.y
    val az = xAxis.z - origin.z
    val bx = yAxis.x - origin.x
    val by = yAxis.y - origin.y
    val bz = yAxis.z - origin.z
    val crossX = ay * bz - az * by
    val crossY = az * bx - ax * bz
    val crossZ = ax * by - ay * bx
    val length = sqrt(crossX * crossX + crossY * crossY + crossZ * crossZ).coerceAtLeast(0.0001f)
    return abs(crossZ / length).coerceIn(0.05f, 1f)
}

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
    withCullStatePreserved {
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
}

private fun drawProjectedShadowGradient(
    outline: List<UiVec3>,
    spread: Float,
    blur: Float,
    offsetX: Float,
    offsetY: Float,
    color: UiColor,
) {
    if (color.alpha <= 0f) return
    if (outline.isEmpty()) return
    val centerX = outline.sumOf { it.x.toDouble() }.toFloat() / outline.size.toFloat()
    val centerY = outline.sumOf { it.y.toDouble() }.toFloat() / outline.size.toFloat()
    val outerExpansion = blur.coerceAtLeast(1f)
    val innerAlpha = color.alpha * 0.72f
    configureUiBlend()
    withCullStatePreserved {
        RenderSystem.disableCull()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        val center = UiVec3(centerX + offsetX, centerY + offsetY, 0f)
        val inner = outline.map { expandFromCenter(it, centerX, centerY, spread) }
        val outer = outline.map { expandFromCenter(it, centerX, centerY, spread + outerExpansion) }
        for (index in 0 until inner.lastIndex) {
            val currentInner = inner[index].withOffset(offsetX, offsetY)
            val nextInner = inner[index + 1].withOffset(offsetX, offsetY)
            val currentOuter = outer[index].withOffset(offsetX, offsetY)
            val nextOuter = outer[index + 1].withOffset(offsetX, offsetY)
            buffer.addVertex(center.x, center.y, center.z).setColor(color.red, color.green, color.blue, innerAlpha)
            buffer.addVertex(currentInner.x, currentInner.y, 0f).setColor(color.red, color.green, color.blue, innerAlpha)
            buffer.addVertex(nextInner.x, nextInner.y, 0f).setColor(color.red, color.green, color.blue, innerAlpha)

            buffer.addVertex(currentInner.x, currentInner.y, 0f).setColor(color.red, color.green, color.blue, innerAlpha)
            buffer.addVertex(currentOuter.x, currentOuter.y, 0f).setColor(color.red, color.green, color.blue, 0f)
            buffer.addVertex(nextOuter.x, nextOuter.y, 0f).setColor(color.red, color.green, color.blue, 0f)

            buffer.addVertex(currentInner.x, currentInner.y, 0f).setColor(color.red, color.green, color.blue, innerAlpha)
            buffer.addVertex(nextOuter.x, nextOuter.y, 0f).setColor(color.red, color.green, color.blue, 0f)
            buffer.addVertex(nextInner.x, nextInner.y, 0f).setColor(color.red, color.green, color.blue, innerAlpha)
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }
}

private fun UiVec3.withOffset(x: Float, y: Float): UiVec3 = UiVec3(this.x + x, this.y + y, z)

private fun expandFromCenter(point: UiVec3, centerX: Float, centerY: Float, distance: Float): UiVec3 {
    val x = point.x - centerX
    val y = point.y - centerY
    val length = sqrt(x * x + y * y).coerceAtLeast(1f)
    return UiVec3(
        x = point.x + x / length * distance,
        y = point.y + y / length * distance,
        z = point.z,
    )
}

private fun drawRoundedStroke(width: Float, height: Float, radius: Float, thickness: Float, color: UiColor, transform: UiMatrix4) {
    val inset = thickness.coerceAtLeast(1f)
    val innerWidth = width - inset * 2f
    val innerHeight = height - inset * 2f
    if (innerWidth <= 0f || innerHeight <= 0f) {
        drawRoundedFan(width, height, radius, transform) { _, _ -> color }
        return
    }
    val segments = roundedSegments(radius)
    val outer = roundedPerimeter(width, height, radius, segments)
    val inner = roundedPerimeter(innerWidth, innerHeight, max(0f, radius - inset), segments).map { (x, y) -> x + inset to y + inset }
    withCullStatePreserved {
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
}

private fun roundedPerimeter(width: Float, height: Float, radius: Float, segmentsOverride: Int? = null): List<Pair<Float, Float>> {
    val clamped = radius.coerceIn(0f, min(width, height) * 0.5f)
    if (clamped <= 0f) {
        return listOf(0f to 0f, 0f to height, width to height, width to 0f, 0f to 0f)
    }
    val segments = segmentsOverride ?: roundedSegments(clamped)
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

private fun roundedSegments(radius: Float): Int = max(4, min(12, (radius / 2f).roundToInt()))

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
