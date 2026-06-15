package ru.hollowhorizon.hollowengine.client.ui

import java.awt.BasicStroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class UiPathContour(
    val points: List<UiPathPoint>,
    val closed: Boolean,
)

data class UiPathGeometry(
    val contours: List<UiPathContour>,
) {
    fun fillTriangles(): List<UiPathTriangle> = triangulatePath(contours)

    fun strokeTriangles(
        width: Float,
        lineCap: UiPathStrokeLineCap = UiPathStrokeLineCap.Round,
        lineJoin: UiPathStrokeLineJoin = UiPathStrokeLineJoin.Round,
    ): List<UiPathTriangle> {
        if (width <= 0f || contours.none { it.points.size >= 2 }) return emptyList()
        return BasicStroke(
            width,
            lineCap.toAwtStrokeCap(),
            lineJoin.toAwtStrokeJoin(),
        ).createStrokedShape(toPath().toAwtPath()).toUiPath(flatness = 0.35).flatten().fillTriangles()
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

private fun List<UiPathPoint>.withoutRepeatedLast(): List<UiPathPoint> {
    if (size > 1 && first() == last()) return dropLast(1)
    return this
}

private fun UiPathPoint.distanceTo(other: UiPathPoint): Float {
    val dx = other.x - x
    val dy = other.y - y
    return sqrt(dx * dx + dy * dy)
}

private fun UiPathStrokeLineCap.toAwtStrokeCap(): Int {
    return when (this) {
        UiPathStrokeLineCap.Butt -> BasicStroke.CAP_BUTT
        UiPathStrokeLineCap.Round -> BasicStroke.CAP_ROUND
        UiPathStrokeLineCap.Square -> BasicStroke.CAP_SQUARE
    }
}

private fun UiPathStrokeLineJoin.toAwtStrokeJoin(): Int {
    return when (this) {
        UiPathStrokeLineJoin.Miter -> BasicStroke.JOIN_MITER
        UiPathStrokeLineJoin.Round -> BasicStroke.JOIN_ROUND
        UiPathStrokeLineJoin.Bevel -> BasicStroke.JOIN_BEVEL
    }
}

private const val DefaultPathTolerance = 0.35f
