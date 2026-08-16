package ru.hollowhorizon.hollowengine.client.ui.shape

import java.awt.BasicStroke
import kotlin.math.*

data class UiPathContour(
    val points: List<UiPathPoint>,
    val closed: Boolean,
)

data class UiPathGeometry(
    val contours: List<UiPathContour>,
) {
    fun fillTriangles(): List<UiPathTriangle> = triangulatePath(contours)

    /**
     * Tessellates the stroke by offsetting each flattened contour directly: a quad per segment,
     * a fan per join, and caps on open ends. This never builds a stroke *outline* to fill - which
     * for sharp polylines (e.g. the diagnostic zig-zag) self-intersects and can't be ear-clipped -
     * and it skips AWT stroking entirely, so it's both correct and much cheaper.
     */
    fun strokeTriangles(
        width: Float,
        lineCap: UiPathStrokeLineCap = UiPathStrokeLineCap.Round,
        lineJoin: UiPathStrokeLineJoin = UiPathStrokeLineJoin.Round,
    ): List<UiPathTriangle> {
        if (width <= 0f) return emptyList()
        val half = width * 0.5f
        val triangles = ArrayList<UiPathTriangle>()
        contours.forEach { strokeContour(it, half, lineCap, lineJoin, triangles) }
        return triangles
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

internal fun UiPath.strokedPath(
    width: Float,
    lineCap: UiPathStrokeLineCap,
    lineJoin: UiPathStrokeLineJoin,
): UiPath {
    if (width <= 0f || isEmpty()) return UiPath(emptyList())
    val cap = when (lineCap) {
        UiPathStrokeLineCap.Butt -> BasicStroke.CAP_BUTT
        UiPathStrokeLineCap.Round -> BasicStroke.CAP_ROUND
        UiPathStrokeLineCap.Square -> BasicStroke.CAP_SQUARE
    }
    val join = when (lineJoin) {
        UiPathStrokeLineJoin.Miter -> BasicStroke.JOIN_MITER
        UiPathStrokeLineJoin.Round -> BasicStroke.JOIN_ROUND
        UiPathStrokeLineJoin.Bevel -> BasicStroke.JOIN_BEVEL
    }
    return BasicStroke(width, cap, join).createStrokedShape(toAwtPath()).toUiPath()
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
    val result = ArrayList<UiPathPoint>()
    flattenCubic(
        from,
        command.control1,
        command.control2,
        command.target,
        tolerance.coerceAtLeast(MinimumPathTolerance),
        0,
        result,
    )
    return result
}

private fun quadraticPoints(
    from: UiPathPoint,
    command: UiPathCommand.QuadraticTo,
    tolerance: Float,
): List<UiPathPoint> {
    val result = ArrayList<UiPathPoint>()
    flattenQuadratic(
        from,
        command.control,
        command.target,
        tolerance.coerceAtLeast(MinimumPathTolerance),
        0,
        result,
    )
    return result
}

private fun flattenCubic(
    first: UiPathPoint,
    control1: UiPathPoint,
    control2: UiPathPoint,
    last: UiPathPoint,
    tolerance: Float,
    depth: Int,
    result: MutableList<UiPathPoint>,
) {
    if (depth >= MaximumSubdivisionDepth ||
        max(distanceToLine(control1, first, last), distanceToLine(control2, first, last)) <= tolerance
    ) {
        result += last
        return
    }
    val firstControl = midpoint(first, control1)
    val centerControl = midpoint(control1, control2)
    val lastControl = midpoint(control2, last)
    val firstMiddle = midpoint(firstControl, centerControl)
    val lastMiddle = midpoint(centerControl, lastControl)
    val center = midpoint(firstMiddle, lastMiddle)
    flattenCubic(first, firstControl, firstMiddle, center, tolerance, depth + 1, result)
    flattenCubic(center, lastMiddle, lastControl, last, tolerance, depth + 1, result)
}

private fun flattenQuadratic(
    first: UiPathPoint,
    control: UiPathPoint,
    last: UiPathPoint,
    tolerance: Float,
    depth: Int,
    result: MutableList<UiPathPoint>,
) {
    if (depth >= MaximumSubdivisionDepth || distanceToLine(control, first, last) <= tolerance) {
        result += last
        return
    }
    val firstControl = midpoint(first, control)
    val lastControl = midpoint(control, last)
    val center = midpoint(firstControl, lastControl)
    flattenQuadratic(first, firstControl, center, tolerance, depth + 1, result)
    flattenQuadratic(center, lastControl, last, tolerance, depth + 1, result)
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
    val effectiveTolerance = tolerance.coerceAtLeast(MinimumPathTolerance).toDouble().coerceAtMost(radius)
    val toleranceStep = 2.0 * acos((1.0 - effectiveTolerance / radius).coerceIn(-1.0, 1.0))
    val step = min(maxStep, toleranceStep.coerceAtLeast(MinimumArcStep))
    val segments = ceil(abs(sweepAngle) / step).toInt().coerceIn(1, 256)
    val points = (1..segments).map { index ->
        val theta = startAngle + sweepAngle * index.toDouble() / segments.toDouble()
        val x = cosPhi * radiusXd * cos(theta) - sinPhi * radiusYd * sin(theta) + centerX
        val y = sinPhi * radiusXd * cos(theta) + cosPhi * radiusYd * sin(theta) + centerY
        UiPathPoint(x.toFloat(), y.toFloat())
    }
    return points.dropLast(1) + target
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

private fun midpoint(first: UiPathPoint, second: UiPathPoint) = UiPathPoint(
    (first.x + second.x) * 0.5f,
    (first.y + second.y) * 0.5f,
)

private fun distanceToLine(point: UiPathPoint, first: UiPathPoint, last: UiPathPoint): Float {
    val dx = last.x - first.x
    val dy = last.y - first.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared <= 0.000001f) {
        val pointDx = point.x - first.x
        val pointDy = point.y - first.y
        return sqrt(pointDx * pointDx + pointDy * pointDy)
    }
    return abs(dy * point.x - dx * point.y + last.x * first.y - last.y * first.x) /
            sqrt(lengthSquared)
}

private fun strokeContour(
    contour: UiPathContour,
    half: Float,
    lineCap: UiPathStrokeLineCap,
    lineJoin: UiPathStrokeLineJoin,
    out: MutableList<UiPathTriangle>,
) {
    val points = ArrayList<UiPathPoint>(contour.points.size)
    contour.points.forEach { if (points.lastOrNull() != it) points += it }
    if (contour.closed && points.size > 1 && points.first() == points.last()) points.removeAt(points.lastIndex)
    if (points.size < 2) {
        if (points.size == 1 && lineCap == UiPathStrokeLineCap.Round) addDisc(points[0], half, out)
        return
    }

    val size = points.size
    val closed = contour.closed
    val segments = if (closed) size else size - 1
    for (i in 0 until segments) addSegmentQuad(points[i], points[(i + 1) % size], half, out)

    val firstJoin = if (closed) 0 else 1
    val lastJoin = if (closed) size - 1 else size - 2
    for (i in firstJoin..lastJoin) {
        addJoin(points[(i - 1 + size) % size], points[i], points[(i + 1) % size], half, lineJoin, out)
    }

    if (!closed) {
        addCap(points[1], points[0], half, lineCap, out)
        addCap(points[size - 2], points[size - 1], half, lineCap, out)
    }
}

/** A segment [a]->[b] widened to [half] on each side: two triangles covering the offset rectangle. */
private fun addSegmentQuad(a: UiPathPoint, b: UiPathPoint, half: Float, out: MutableList<UiPathTriangle>) {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < StrokeEpsilon) return
    val nx = -dy / length * half
    val ny = dx / length * half
    val a1 = UiPathPoint(a.x + nx, a.y + ny)
    val a2 = UiPathPoint(a.x - nx, a.y - ny)
    val b1 = UiPathPoint(b.x + nx, b.y + ny)
    val b2 = UiPathPoint(b.x - nx, b.y - ny)
    out += UiPathTriangle(a1, b1, b2)
    out += UiPathTriangle(a1, b2, a2)
}

/** Fills the wedge left open on the outer side of the corner at [corner]. */
private fun addJoin(
    previous: UiPathPoint,
    corner: UiPathPoint,
    next: UiPathPoint,
    half: Float,
    join: UiPathStrokeLineJoin,
    out: MutableList<UiPathTriangle>,
) {
    val inX = corner.x - previous.x
    val inY = corner.y - previous.y
    val outX = next.x - corner.x
    val outY = next.y - corner.y
    val inLength = sqrt(inX * inX + inY * inY)
    val outLength = sqrt(outX * outX + outY * outY)
    if (inLength < StrokeEpsilon || outLength < StrokeEpsilon) return
    val turn = inX * outY - inY * outX
    if (abs(turn) < StrokeEpsilon) return // straight - the segment quads already meet flush

    // Outer side of the turn: right normals for a left turn, left normals for a right turn.
    val sign = if (turn > 0f) -1f else 1f
    val inNormalX = -inY / inLength * half * sign
    val inNormalY = inX / inLength * half * sign
    val outNormalX = -outY / outLength * half * sign
    val outNormalY = outX / outLength * half * sign
    val start = UiPathPoint(corner.x + inNormalX, corner.y + inNormalY)
    val end = UiPathPoint(corner.x + outNormalX, corner.y + outNormalY)

    if (join == UiPathStrokeLineJoin.Round) {
        val startAngle = atan2(start.y - corner.y, start.x - corner.x)
        val endAngle = atan2(end.y - corner.y, end.x - corner.x)
        addArc(corner, half, startAngle, shortSweep(startAngle, endAngle), out)
    } else {
        // Bevel (and miter, approximated): a single triangle spanning the outer gap.
        out += UiPathTriangle(corner, start, end)
    }
}

/** Round/square/butt cap centred on [end], the outer terminal of a segment coming from [inner]. */
private fun addCap(
    inner: UiPathPoint,
    end: UiPathPoint,
    half: Float,
    cap: UiPathStrokeLineCap,
    out: MutableList<UiPathTriangle>,
) {
    if (cap == UiPathStrokeLineCap.Butt) return
    val dx = end.x - inner.x
    val dy = end.y - inner.y
    val length = sqrt(dx * dx + dy * dy)
    if (length < StrokeEpsilon) return
    val ux = dx / length
    val uy = dy / length
    val nx = -uy * half
    val ny = ux * half
    if (cap == UiPathStrokeLineCap.Square) {
        val e1 = UiPathPoint(end.x + nx, end.y + ny)
        val e2 = UiPathPoint(end.x - nx, end.y - ny)
        val f1 = UiPathPoint(e1.x + ux * half, e1.y + uy * half)
        val f2 = UiPathPoint(e2.x + ux * half, e2.y + uy * half)
        out += UiPathTriangle(e1, f1, f2)
        out += UiPathTriangle(e1, f2, e2)
        return
    }
    // Round: a semicircle fanned from the left corner, through the tip, to the right corner.
    val steps = 8
    var previous = UiPathPoint(end.x + nx, end.y + ny)
    for (step in 1..steps) {
        val phi = PI * step / steps
        val c = cos(phi).toFloat()
        val s = sin(phi).toFloat()
        val point = UiPathPoint(end.x + nx * c + ux * half * s, end.y + ny * c + uy * half * s)
        out += UiPathTriangle(end, previous, point)
        previous = point
    }
}

/** A filled circle around [center] - the round cap of a zero-length (single-point) contour. */
private fun addDisc(center: UiPathPoint, radius: Float, out: MutableList<UiPathTriangle>) {
    val steps = 16
    var previous = UiPathPoint(center.x + radius, center.y)
    for (step in 1..steps) {
        val angle = PI * 2.0 * step / steps
        val point = UiPathPoint(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
        out += UiPathTriangle(center, previous, point)
        previous = point
    }
}

/** Fans a circular sector of [radius] around [center] sweeping [sweep] radians from [startAngle]. */
private fun addArc(center: UiPathPoint, radius: Float, startAngle: Float, sweep: Float, out: MutableList<UiPathTriangle>) {
    val steps = max(1, ceil(abs(sweep) / (PI / 8.0)).toInt())
    var previous = UiPathPoint(center.x + cos(startAngle) * radius, center.y + sin(startAngle) * radius)
    for (step in 1..steps) {
        val angle = startAngle + sweep * step / steps
        val point = UiPathPoint(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        out += UiPathTriangle(center, previous, point)
        previous = point
    }
}

/** Signed shortest sweep (in (-PI, PI]) from [from] to [to] - the outer corner wedge is always < PI. */
private fun shortSweep(from: Float, to: Float): Float {
    var sweep = to - from
    while (sweep <= -PI) sweep += (PI * 2f).toFloat()
    while (sweep > PI) sweep -= (PI * 2f).toFloat()
    return sweep
}

private const val StrokeEpsilon = 1e-5f
private const val DefaultPathTolerance = 0.175f
private const val MinimumPathTolerance = 0.05f
private const val MaximumSubdivisionDepth = 12
private const val MinimumArcStep = 0.01
