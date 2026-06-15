package ru.hollowhorizon.hollowengine.client.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class UiPathContour(
    val points: List<UiPathPoint>,
    val closed: Boolean,
)

data class UiPathGeometry(
    val contours: List<UiPathContour>,
) {
    fun fillTriangles(): List<UiPathTriangle> = contours
        .filter { it.closed }
        .flatMap { triangulate(it.points) }

    fun strokeTriangles(
        width: Float,
        lineCap: UiPathStrokeLineCap = UiPathStrokeLineCap.Round,
        lineJoin: UiPathStrokeLineJoin = UiPathStrokeLineJoin.Round,
    ): List<UiPathTriangle> {
        val half = width.coerceAtLeast(0f) * 0.5f
        if (half <= 0f) return emptyList()
        return contours.flatMap { contour ->
            val points = contour.points
            if (points.size < 2) return@flatMap emptyList()
            val result = mutableListOf<UiPathTriangle>()
            val segmentCount = if (contour.closed) points.size else points.size - 1
            for (index in 0 until segmentCount) {
                val start = points[index]
                val end = points[(index + 1) % points.size]
                appendStrokeSegment(result, start, end, half, lineCap)
            }
            if (lineJoin == UiPathStrokeLineJoin.Round) {
                val joinRange = if (contour.closed) points.indices else 1 until points.lastIndex
                joinRange.forEach { index -> appendRoundStrokeDisk(result, points[index], half) }
            }
            if (!contour.closed && lineCap == UiPathStrokeLineCap.Round) {
                appendRoundStrokeDisk(result, points.first(), half)
                appendRoundStrokeDisk(result, points.last(), half)
            }
            result
        }
    }
}

enum class UiPathStrokeLineCap {
    Butt,
    Round,
    Square,
}

enum class UiPathStrokeLineJoin {
    Miter,
    Round,
    Bevel,
}

data class UiPathTriangle(
    val first: UiPathPoint,
    val second: UiPathPoint,
    val third: UiPathPoint,
)

fun UiPath.flatten(tolerance: Float = DefaultPathTolerance): UiPathGeometry {
    val contours = mutableListOf<UiPathContour>()
    var points = mutableListOf<UiPathPoint>()
    var current = UiPathPoint(0f, 0f)
    var subPathStart = UiPathPoint(0f, 0f)

    fun finish(closed: Boolean = false) {
        val clean = points.withoutRepeatedLast()
        if (clean.isNotEmpty()) contours += UiPathContour(clean, closed)
        points = mutableListOf()
    }

    fun add(point: UiPathPoint) {
        if (points.lastOrNull() != point) points += point
        current = point
    }

    commands.forEach { command ->
        when (command) {
            is UiPathCommand.MoveTo -> {
                finish()
                add(command.target)
                subPathStart = command.target
            }
            is UiPathCommand.LineTo -> add(command.target)
            is UiPathCommand.CubicTo -> {
                cubicPoints(current, command, tolerance).forEach(::add)
            }
            is UiPathCommand.QuadraticTo -> {
                quadraticPoints(current, command, tolerance).forEach(::add)
            }
            is UiPathCommand.ArcTo -> {
                arcPoints(current, command, tolerance).forEach(::add)
            }
            UiPathCommand.Close -> {
                add(subPathStart)
                finish(closed = true)
                current = subPathStart
            }
        }
    }
    finish()
    return UiPathGeometry(contours)
}

private fun cubicPoints(from: UiPathPoint, command: UiPathCommand.CubicTo, tolerance: Float): List<UiPathPoint> {
    val segments = curveSegments(from, command.control1, command.control2, command.target, tolerance = tolerance)
    return (1..segments).map { index ->
        val t = index.toFloat() / segments.toFloat()
        val inverse = 1f - t
        UiPathPoint(
            x = inverse * inverse * inverse * from.x +
                    3f * inverse * inverse * t * command.control1.x +
                    3f * inverse * t * t * command.control2.x +
                    t * t * t * command.target.x,
            y = inverse * inverse * inverse * from.y +
                    3f * inverse * inverse * t * command.control1.y +
                    3f * inverse * t * t * command.control2.y +
                    t * t * t * command.target.y,
        )
    }
}

private fun quadraticPoints(from: UiPathPoint, command: UiPathCommand.QuadraticTo, tolerance: Float): List<UiPathPoint> {
    val segments = curveSegments(from, command.control, command.target, tolerance = tolerance)
    return (1..segments).map { index ->
        val t = index.toFloat() / segments.toFloat()
        val inverse = 1f - t
        UiPathPoint(
            x = inverse * inverse * from.x + 2f * inverse * t * command.control.x + t * t * command.target.x,
            y = inverse * inverse * from.y + 2f * inverse * t * command.control.y + t * t * command.target.y,
        )
    }
}

private fun arcPoints(from: UiPathPoint, command: UiPathCommand.ArcTo, tolerance: Float): List<UiPathPoint> {
    var radiusX = abs(command.radiusX)
    var radiusY = abs(command.radiusY)
    val target = command.target
    if (radiusX <= 0f || radiusY <= 0f || from == target) return listOf(target)

    val phi = command.xAxisRotation.toDouble() * PI / 180.0
    val cosPhi = cos(phi)
    val sinPhi = sin(phi)
    val dx = (from.x - target.x).toDouble() * 0.5
    val dy = (from.y - target.y).toDouble() * 0.5
    val x1p = cosPhi * dx + sinPhi * dy
    val y1p = -sinPhi * dx + cosPhi * dy

    val lambda = x1p * x1p / (radiusX * radiusX) + y1p * y1p / (radiusY * radiusY)
    if (lambda > 1.0) {
        val scale = sqrt(lambda).toFloat()
        radiusX *= scale
        radiusY *= scale
    }

    val radiusXd = radiusX.toDouble()
    val radiusYd = radiusY.toDouble()
    val rx2 = radiusXd * radiusXd
    val ry2 = radiusYd * radiusYd
    val numerator = max(0.0, rx2 * ry2 - rx2 * y1p * y1p - ry2 * x1p * x1p)
    val denominator = (rx2 * y1p * y1p + ry2 * x1p * x1p).coerceAtLeast(0.000001)
    val sign = if (command.largeArc == command.sweep) -1.0 else 1.0
    val coefficient = sign * sqrt(numerator / denominator)
    val cxp = coefficient * radiusXd * y1p / radiusYd
    val cyp = coefficient * -radiusYd * x1p / radiusXd
    val centerX = cosPhi * cxp - sinPhi * cyp + (from.x + target.x).toDouble() * 0.5
    val centerY = sinPhi * cxp + cosPhi * cyp + (from.y + target.y).toDouble() * 0.5

    val startAngle = angle(1.0, 0.0, (x1p - cxp) / radiusXd, (y1p - cyp) / radiusYd)
    var sweepAngle = angle(
        (x1p - cxp) / radiusXd,
        (y1p - cyp) / radiusYd,
        (-x1p - cxp) / radiusXd,
        (-y1p - cyp) / radiusYd,
    )
    if (!command.sweep && sweepAngle > 0.0) sweepAngle -= PI * 2.0
    if (command.sweep && sweepAngle < 0.0) sweepAngle += PI * 2.0

    val maxStep = PI / 8.0
    val radius = max(radiusX, radiusY).toDouble()
    val byTolerance = ceil(max(abs(sweepAngle) * radius / tolerance.coerceAtLeast(0.25f).toDouble(), 1.0)).toInt()
    val byAngle = ceil(abs(sweepAngle) / maxStep).toInt().coerceAtLeast(1)
    val segments = max(byTolerance, byAngle).coerceIn(1, 256)
    val points = (1..segments).map { index ->
        val theta = startAngle + sweepAngle * index.toDouble() / segments.toDouble()
        val x = cosPhi * radiusXd * cos(theta) - sinPhi * radiusYd * sin(theta) + centerX
        val y = sinPhi * radiusXd * cos(theta) + cosPhi * radiusYd * sin(theta) + centerY
        UiPathPoint(x.toFloat(), y.toFloat())
    }
    return points.dropLast(1) + target
}

private fun curveSegments(vararg points: UiPathPoint, tolerance: Float): Int {
    val length = points.asList().zipWithNext().sumOf { (start, end) -> start.distanceTo(end).toDouble() }.toFloat()
    return ceil(length / tolerance.coerceAtLeast(0.1f)).toInt().coerceIn(8, 256)
}

private fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
    val dot = ux * vx + uy * vy
    val cross = ux * vy - uy * vx
    return atan2(cross, dot)
}

private fun triangulate(points: List<UiPathPoint>): List<UiPathTriangle> {
    val clean = points.withoutRepeatedLast()
    if (clean.size < 3) return emptyList()
    if (clean.size == 3) return listOf(UiPathTriangle(clean[0], clean[1], clean[2]))
    val indices = clean.indices.toMutableList()
    val result = mutableListOf<UiPathTriangle>()
    val orientation = polygonArea(clean)
    var guard = 0
    while (indices.size > 3 && guard++ < clean.size * clean.size) {
        val earIndex = indices.indices.firstOrNull { localIndex ->
            isEar(clean, indices, localIndex, orientation)
        } ?: break
        val previous = indices[(earIndex - 1 + indices.size) % indices.size]
        val current = indices[earIndex]
        val next = indices[(earIndex + 1) % indices.size]
        result += UiPathTriangle(clean[previous], clean[current], clean[next])
        indices.removeAt(earIndex)
    }
    if (indices.size == 3) result += UiPathTriangle(clean[indices[0]], clean[indices[1]], clean[indices[2]])
    return if (result.isEmpty()) fanTriangulate(clean) else result
}

private fun isEar(points: List<UiPathPoint>, indices: List<Int>, localIndex: Int, orientation: Float): Boolean {
    val previous = points[indices[(localIndex - 1 + indices.size) % indices.size]]
    val current = points[indices[localIndex]]
    val next = points[indices[(localIndex + 1) % indices.size]]
    val cross = cross(previous, current, next)
    if (orientation >= 0f && cross <= 0f) return false
    if (orientation < 0f && cross >= 0f) return false
    return indices.none { index ->
        val point = points[index]
        point != previous && point != current && point != next && pointInTriangle(point, previous, current, next)
    }
}

private fun fanTriangulate(points: List<UiPathPoint>): List<UiPathTriangle> {
    val center = UiPathPoint(
        points.sumOf { it.x.toDouble() }.toFloat() / points.size.toFloat(),
        points.sumOf { it.y.toDouble() }.toFloat() / points.size.toFloat(),
    )
    return points.indices.map { index ->
        UiPathTriangle(center, points[index], points[(index + 1) % points.size])
    }
}

private fun appendStrokeSegment(
    result: MutableList<UiPathTriangle>,
    start: UiPathPoint,
    end: UiPathPoint,
    half: Float,
    lineCap: UiPathStrokeLineCap,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length <= 0.0001f) return
    val extension = if (lineCap == UiPathStrokeLineCap.Square) half else 0f
    val tx = dx / length * extension
    val ty = dy / length * extension
    val nx = -dy / length * half
    val ny = dx / length * half
    val a = UiPathPoint(start.x - tx + nx, start.y - ty + ny)
    val b = UiPathPoint(start.x - tx - nx, start.y - ty - ny)
    val c = UiPathPoint(end.x + tx - nx, end.y + ty - ny)
    val d = UiPathPoint(end.x + tx + nx, end.y + ty + ny)
    result += UiPathTriangle(a, b, c)
    result += UiPathTriangle(a, c, d)
}

private fun appendRoundStrokeDisk(result: MutableList<UiPathTriangle>, center: UiPathPoint, radius: Float) {
    val segments = max(16, min(64, ceil(radius * 4f).toInt()))
    for (index in 0 until segments) {
        val firstAngle = PI.toFloat() * 2f * index.toFloat() / segments.toFloat()
        val secondAngle = PI.toFloat() * 2f * (index + 1).toFloat() / segments.toFloat()
        result += UiPathTriangle(
            center,
            UiPathPoint(center.x + cos(firstAngle) * radius, center.y + sin(firstAngle) * radius),
            UiPathPoint(center.x + cos(secondAngle) * radius, center.y + sin(secondAngle) * radius),
        )
    }
}

private fun List<UiPathPoint>.withoutRepeatedLast(): List<UiPathPoint> {
    if (size > 1 && first() == last()) return dropLast(1)
    return this
}

private fun polygonArea(points: List<UiPathPoint>): Float {
    var area = 0f
    for (index in points.indices) {
        val a = points[index]
        val b = points[(index + 1) % points.size]
        area += a.x * b.y - b.x * a.y
    }
    return area * 0.5f
}

private fun cross(a: UiPathPoint, b: UiPathPoint, c: UiPathPoint): Float {
    return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
}

private fun pointInTriangle(point: UiPathPoint, a: UiPathPoint, b: UiPathPoint, c: UiPathPoint): Boolean {
    val c1 = cross(a, b, point)
    val c2 = cross(b, c, point)
    val c3 = cross(c, a, point)
    val hasNegative = c1 < 0f || c2 < 0f || c3 < 0f
    val hasPositive = c1 > 0f || c2 > 0f || c3 > 0f
    return !(hasNegative && hasPositive)
}

private fun UiPathPoint.distanceTo(other: UiPathPoint): Float {
    val dx = other.x - x
    val dy = other.y - y
    return sqrt(dx * dx + dy * dy)
}

private const val DefaultPathTolerance = 0.35f
