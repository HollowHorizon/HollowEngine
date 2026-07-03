package ru.hollowhorizon.hollowengine.client.ui.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.renderer.GameRenderer
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.shape.SvgResourceShape
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathPoint
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathTriangle
import ru.hollowhorizon.hollowengine.client.ui.shape.UiShapeSize
import ru.hollowhorizon.hollowengine.client.ui.shape.flatten
import ru.hollowhorizon.hollowengine.client.ui.style.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

internal data class UiBatchedQuad(
    val width: Float,
    val height: Float,
    val transform: UiMatrix4,
    val topLeft: UiColor,
    val bottomLeft: UiColor,
    val bottomRight: UiColor,
    val topRight: UiColor,
)

internal data class UiBatchedTriangle(
    val first: UiBatchedVertex,
    val second: UiBatchedVertex,
    val third: UiBatchedVertex,
)

internal data class UiBatchedVertex(
    val position: UiVec3,
    val color: UiColor,
)

internal fun drawBatchedQuads(quads: List<UiBatchedQuad>) {
    if (quads.isEmpty()) return
    withCullStatePreserved {
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        configureUiBlend()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        quads.forEach { quad ->
            val corners = localCorners(quad.width, quad.height, quad.transform)
            corners.forEachIndexed { index, corner ->
                val color = when (index) {
                    0 -> quad.topLeft
                    1 -> quad.bottomLeft
                    2 -> quad.bottomRight
                    else -> quad.topRight
                }
                buffer.addVertex(corner.x, corner.y, corner.z).setColor(color.red, color.green, color.blue, color.alpha)
            }
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }
}

internal fun drawBatchedTriangles(triangles: List<UiBatchedTriangle>) {
    if (triangles.isEmpty()) return
    withCullStatePreserved {
        RenderSystem.disableCull()
        RenderSystem.enableBlend()
        configureUiBlend()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR)
        triangles.forEach { triangle ->
            buffer.add(triangle.first)
            buffer.add(triangle.second)
            buffer.add(triangle.third)
        }
        BufferUploader.drawWithShader(buffer.buildOrThrow())
    }
}

internal fun solidQuad(width: Float, height: Float, color: UiColor, transform: UiMatrix4): UiBatchedQuad {
    return UiBatchedQuad(width, height, transform, color, color, color, color)
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
        topLeft = gradientColorAt(0f, 0f, width, height, angleDegrees, stops).withOpacity(opacity),
        bottomLeft = gradientColorAt(0f, height, width, height, angleDegrees, stops).withOpacity(opacity),
        bottomRight = gradientColorAt(width, height, width, height, angleDegrees, stops).withOpacity(opacity),
        topRight = gradientColorAt(width, 0f, width, height, angleDegrees, stops).withOpacity(opacity),
    )
}

internal fun MutableList<UiBatchedTriangle>.appendSolidQuad(
    width: Float,
    height: Float,
    color: UiColor,
    transform: UiMatrix4,
) {
    appendGradientQuad(width, height, color, color, color, color, transform)
}

internal fun MutableList<UiBatchedTriangle>.appendGradientQuad(
    width: Float,
    height: Float,
    angleDegrees: Float,
    stops: List<UiGradientStop>,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    appendGradientQuad(
        width = width,
        height = height,
        topLeft = gradientColorAt(0f, 0f, width, height, angleDegrees, stops).withOpacity(opacity).filtered(filter),
        bottomLeft = gradientColorAt(0f, height, width, height, angleDegrees, stops).withOpacity(opacity).filtered(filter),
        bottomRight = gradientColorAt(width, height, width, height, angleDegrees, stops).withOpacity(opacity).filtered(filter),
        topRight = gradientColorAt(width, 0f, width, height, angleDegrees, stops).withOpacity(opacity).filtered(filter),
        transform = transform,
    )
}

internal fun MutableList<UiBatchedTriangle>.appendLocalPaint(
    width: Float,
    height: Float,
    radius: Float,
    color: UiColor,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    val filtered = color.filtered(filter)
    if (radius <= 0f) {
        appendSolidQuad(width, height, filtered, transform)
        return
    }
    appendRoundedFill(width, height, radius, transform) { _, _ -> filtered }
}

internal fun MutableList<UiBatchedTriangle>.appendLocalGradient(
    width: Float,
    height: Float,
    radius: Float,
    angleDegrees: Float,
    stops: List<UiGradientStop>,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    if (radius <= 0f) {
        appendGradientQuad(width, height, angleDegrees, stops, opacity, transform, filter)
        return
    }
    appendRoundedFill(width, height, radius, transform) { x, y ->
        gradientColorAt(x, y, width, height, angleDegrees, stops).withOpacity(opacity).filtered(filter)
    }
}

internal fun MutableList<UiBatchedTriangle>.appendLocalRadialGradient(
    width: Float,
    height: Float,
    radius: Float,
    gradient: UiRadialGradient,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    if (radius <= 0f) {
        appendGradientQuad(
            width = width,
            height = height,
            topLeft = radialGradientColorAt(0f, 0f, width, height, gradient).withOpacity(opacity).filtered(filter),
            bottomLeft = radialGradientColorAt(0f, height, width, height, gradient).withOpacity(opacity).filtered(filter),
            bottomRight = radialGradientColorAt(width, height, width, height, gradient).withOpacity(opacity).filtered(filter),
            topRight = radialGradientColorAt(width, 0f, width, height, gradient).withOpacity(opacity).filtered(filter),
            transform = transform,
        )
        return
    }
    appendRoundedFill(width, height, radius, transform) { x, y ->
        radialGradientColorAt(x, y, width, height, gradient).withOpacity(opacity).filtered(filter)
    }
}

internal fun MutableList<UiBatchedTriangle>.appendLocalBorder(
    width: Float,
    height: Float,
    radius: Float,
    thickness: Float,
    color: UiColor,
    transform: UiMatrix4,
) {
    val border = thickness.coerceAtLeast(1f)
    if (radius > 0f) {
        appendRoundedStroke(width, height, radius, border, color, transform)
        return
    }
    appendSolidQuad(width, border, color, transform)
    appendSolidQuad(width, border, color, transform * UiMatrix4.translation(0f, height - border, 0f))
    appendSolidQuad(border, height, color, transform)
    appendSolidQuad(border, height, color, transform * UiMatrix4.translation(width - border, 0f, 0f))
}

internal fun MutableList<UiBatchedTriangle>.appendLocalShape(
    shape: Shape,
    width: Float,
    height: Float,
    fill: UiResolvedPaint,
    stroke: UiResolvedPaint,
    strokeWidth: Float,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
): Boolean {
    if (!fill.canDrawAsShapePaint() || !stroke.canDrawAsShapePaint()) return false
    val mesh = UiShapeMeshCache.mesh(
        shape = shape,
        width = width,
        height = height,
        strokeWidth = strokeWidth,
        includeFill = fill != UiResolvedPaint.None,
        includeStroke = stroke != UiResolvedPaint.None && strokeWidth > 0f,
    )
    if (fill != UiResolvedPaint.None) {
        mesh.fill.forEach { triangle ->
            appendColoredTriangle(triangle, fill, width, height, opacity, transform, filter)
        }
    }
    if (stroke != UiResolvedPaint.None && strokeWidth > 0f) {
        mesh.stroke.forEach { triangle ->
            appendColoredTriangle(triangle, stroke, width, height, opacity, transform, filter)
        }
    }
    return true
}

internal fun cachedFillTriangles(shape: Shape, width: Float, height: Float): List<UiPathTriangle> {
    return UiShapeMeshCache.mesh(
        shape = shape,
        width = width,
        height = height,
        strokeWidth = 0f,
        includeFill = true,
        includeStroke = false,
    ).fill
}

private object UiShapeMeshCache {
    private val meshes = ConcurrentHashMap<UiShapeMeshKey, UiShapeMesh>()

    fun mesh(
        shape: Shape,
        width: Float,
        height: Float,
        strokeWidth: Float,
        includeFill: Boolean,
        includeStroke: Boolean,
    ): UiShapeMesh {
        val key = UiShapeMeshKey(
            shape = shape,
            revision = shape.revision(),
            width = width,
            height = height,
            strokeWidth = strokeWidth,
            includeFill = includeFill,
            includeStroke = includeStroke,
        )
        return meshes.computeIfAbsent(key) {
            val geometry = shape.createPath(UiShapeSize(width, height)).flatten()
            UiShapeMesh(
                fill = if (includeFill) geometry.fillTriangles() else emptyList(),
                stroke = if (includeStroke) geometry.strokeTriangles(strokeWidth) else emptyList(),
            )
        }
    }

    private fun Shape.revision(): Long {
        return if (this is SvgResourceShape) HollowUiResourceAccess.version(location) else 0L
    }
}

private data class UiShapeMeshKey(
    val shape: Shape,
    val revision: Long,
    val width: Float,
    val height: Float,
    val strokeWidth: Float,
    val includeFill: Boolean,
    val includeStroke: Boolean,
)

private data class UiShapeMesh(
    val fill: List<UiPathTriangle>,
    val stroke: List<UiPathTriangle>,
)

private fun MutableList<UiBatchedTriangle>.appendGradientQuad(
    width: Float,
    height: Float,
    topLeft: UiColor,
    bottomLeft: UiColor,
    bottomRight: UiColor,
    topRight: UiColor,
    transform: UiMatrix4,
) {
    val corners = localCorners(width, height, transform)
    val first = UiBatchedVertex(corners[0], topLeft)
    val second = UiBatchedVertex(corners[1], bottomLeft)
    val third = UiBatchedVertex(corners[2], bottomRight)
    val fourth = UiBatchedVertex(corners[3], topRight)
    this += UiBatchedTriangle(first, second, third)
    this += UiBatchedTriangle(first, third, fourth)
}

private fun BufferBuilder.addColoredQuad(corners: Array<UiVec3>, color: UiColor) {
    corners.forEach { corner ->
        addVertex(corner.x, corner.y, corner.z).setColor(color.red, color.green, color.blue, color.alpha)
    }
}

private fun MutableList<UiBatchedTriangle>.appendRoundedFill(
    width: Float,
    height: Float,
    radius: Float,
    transform: UiMatrix4,
    colorAt: (Float, Float) -> UiColor,
) {
    val centerX = width * 0.5f
    val centerY = height * 0.5f
    val center = UiBatchedVertex(transform.transform(centerX, centerY), colorAt(centerX, centerY))
    val perimeter = roundedPerimeter(width, height, radius)
    for (index in 0 until perimeter.lastIndex) {
        val first = perimeter[index]
        val second = perimeter[index + 1]
        this += UiBatchedTriangle(
            center,
            UiBatchedVertex(transform.transform(first.first, first.second), colorAt(first.first, first.second)),
            UiBatchedVertex(transform.transform(second.first, second.second), colorAt(second.first, second.second)),
        )
    }
}

private fun MutableList<UiBatchedTriangle>.appendRoundedStroke(
    width: Float,
    height: Float,
    radius: Float,
    thickness: Float,
    color: UiColor,
    transform: UiMatrix4,
) {
    val inset = thickness.coerceAtLeast(1f)
    val innerWidth = width - inset * 2f
    val innerHeight = height - inset * 2f
    if (innerWidth <= 0f || innerHeight <= 0f) {
        appendRoundedFill(width, height, radius, transform) { _, _ -> color }
        return
    }
    val segments = roundedSegments(radius)
    val outer = roundedPerimeter(width, height, radius, segments)
    val inner = roundedPerimeter(innerWidth, innerHeight, max(0f, radius - inset), segments)
        .map { (x, y) -> x + inset to y + inset }
    for (index in 0 until outer.lastIndex) {
        val nextIndex = index + 1
        val currentInner = inner[index.coerceAtMost(inner.lastIndex)]
        val nextInner = inner[nextIndex.coerceAtMost(inner.lastIndex)]
        val outerVertex = UiBatchedVertex(transform.transform(outer[index].first, outer[index].second), color)
        val nextOuterVertex = UiBatchedVertex(transform.transform(outer[nextIndex].first, outer[nextIndex].second), color)
        val innerVertex = UiBatchedVertex(transform.transform(currentInner.first, currentInner.second), color)
        val nextInnerVertex = UiBatchedVertex(transform.transform(nextInner.first, nextInner.second), color)
        this += UiBatchedTriangle(outerVertex, innerVertex, nextInnerVertex)
        this += UiBatchedTriangle(outerVertex, nextInnerVertex, nextOuterVertex)
    }
}

private fun VertexConsumer.add(vertex: UiBatchedVertex) {
    val color = vertex.color
    val position = vertex.position
    addVertex(position.x, position.y, position.z).setColor(color.red, color.green, color.blue, color.alpha)
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
        RenderSystem.enableBlend()
        configureUiBlend()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        buffer.addColoredQuad(rect.corners(transform), color)
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
        RenderSystem.enableBlend()
        configureUiBlend()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val tessellator = Tesselator.getInstance()
        val buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        buffer.addColoredQuad(localCorners(width, height, transform), filtered)
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

internal fun drawLocalRadialGradient(
    width: Float,
    height: Float,
    radius: Float,
    gradient: UiRadialGradient,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    drawRoundedFan(width, height, radius, transform) { x, y ->
        radialGradientColorAt(x, y, width, height, gradient).withOpacity(opacity).filtered(filter)
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
        RenderSystem.enableBlend()
        configureUiBlend()
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
        RenderSystem.enableBlend()
        configureUiBlend()
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
        RenderSystem.enableBlend()
        configureUiBlend()
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

private fun roundedSegments(radius: Float): Int = max(8, min(48, (radius * 0.75f).roundToInt()))

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

private fun radialGradientColorAt(x: Float, y: Float, width: Float, height: Float, gradient: UiRadialGradient): UiColor {
    val stops = gradient.stops
    if (stops.isEmpty()) return UiColor.Transparent
    if (stops.size == 1) return stops.first().color
    val centerX = gradient.centerX.resolve(width)
    val centerY = gradient.centerY.resolve(height)
    val radius = gradient.radius.resolve(max(width, height)).coerceAtLeast(0.0001f)
    val dx = x - centerX
    val dy = y - centerY
    val offset = (sqrt(dx * dx + dy * dy) / radius).coerceIn(0f, 1f)
    val right = stops.firstOrNull { it.offset >= offset } ?: stops.last()
    val left = stops.lastOrNull { it.offset <= offset } ?: stops.first()
    if (left == right) return left.color
    val range = (right.offset - left.offset).coerceAtLeast(0.0001f)
    return left.color.interpolate(right.color, (offset - left.offset) / range)
}

private fun MutableList<UiBatchedTriangle>.appendColoredTriangle(
    triangle: UiPathTriangle,
    paint: UiResolvedPaint,
    width: Float,
    height: Float,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
) {
    this += UiBatchedTriangle(
        first = triangle.first.toVertex(paint, width, height, opacity, transform, filter),
        second = triangle.second.toVertex(paint, width, height, opacity, transform, filter),
        third = triangle.third.toVertex(paint, width, height, opacity, transform, filter),
    )
}

private fun UiPathPoint.toVertex(
    paint: UiResolvedPaint,
    width: Float,
    height: Float,
    opacity: Float,
    transform: UiMatrix4,
    filter: UiFilterChain,
): UiBatchedVertex {
    return UiBatchedVertex(
        position = transform.transform(x, y),
        color = paint.colorAt(x, y, width, height).withOpacity(opacity).filtered(filter),
    )
}

private fun UiResolvedPaint.colorAt(x: Float, y: Float, width: Float, height: Float): UiColor = when (this) {
    UiResolvedPaint.None -> UiColor.Transparent
    is UiResolvedPaint.Color -> color
    is UiResolvedPaint.LinearGradient -> gradientColorAt(x, y, width, height, angleDegrees, stops)
    is UiResolvedPaint.RadialGradient -> radialGradientColorAt(x, y, width, height, gradient)
    is UiResolvedPaint.Image,
    is UiResolvedPaint.Shader -> UiColor.Transparent
}

private fun UiResolvedPaint.canDrawAsShapePaint(): Boolean = when (this) {
    UiResolvedPaint.None,
    is UiResolvedPaint.Color,
    is UiResolvedPaint.LinearGradient,
    is UiResolvedPaint.RadialGradient -> true
    is UiResolvedPaint.Image,
    is UiResolvedPaint.Shader -> false
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
