package ru.hollowhorizon.hollowengine.client.ui.shape

import kotlin.math.abs

internal fun triangulatePath(contours: List<UiPathContour>): List<UiPathTriangle> {
    val rings = contours
        .filter { it.closed }
        .mapNotNull { contour -> contour.points.cleanedRing().takeIf { it.size >= 3 } }
        .map(::TriangulationRing)
    if (rings.isEmpty()) return emptyList()

    rings.forEach { ring ->
        ring.parent = rings
            .asSequence()
            .filter { candidate -> candidate !== ring && candidate.absArea > ring.absArea }
            .filter { candidate -> candidate.contains(ring.points.first()) }
            .minByOrNull { it.absArea }
    }

    return rings
        .filter { it.depth % 2 == 0 }
        .flatMap { outer ->
            val holes = rings.filter { it.depth % 2 == 1 && it.outerParent == outer }
            triangulatePolygon(outer.points, holes.map { it.points })
        }
}

private class TriangulationRing(
    val points: List<UiPathPoint>,
) {
    val absArea = abs(polygonArea(points))
    var parent: TriangulationRing? = null

    val depth: Int
        get() {
            var value = 0
            var current = parent
            while (current != null) {
                value++
                current = current.parent
            }
            return value
        }

    val outerParent: TriangulationRing?
        get() {
            var current = parent
            var result: TriangulationRing? = null
            while (current != null) {
                if (current.depth % 2 == 0) result = current
                current = current.parent
            }
            return result
        }

    fun contains(point: UiPathPoint): Boolean = pointInPolygon(point, points)
}

private fun triangulatePolygon(outer: List<UiPathPoint>, holes: List<List<UiPathPoint>>): List<UiPathTriangle> {
    var polygon = orient(outer, clockwise = false)
    holes
        .map { orient(it, clockwise = true) }
        .sortedByDescending { hole -> hole.maxOf { it.x } }
        .forEach { hole -> polygon = bridgeHole(polygon, hole) ?: return@forEach }
    return triangulateSimple(polygon.cleanedRing().withoutCollinear())
}

private fun bridgeHole(outer: List<UiPathPoint>, hole: List<UiPathPoint>): List<UiPathPoint>? {
    if (outer.size < 3 || hole.size < 3) return null
    val holeIndex = hole.indices.maxWith(compareBy<Int> { hole[it].x }.thenBy { -hole[it].y })
    val holePoint = hole[holeIndex]
    val outerIndex = outer.indices
        .filter { index -> isVisibleBridge(holePoint, outer[index], outer, hole) }
        .minByOrNull { index -> holePoint.distanceSquaredTo(outer[index]) }
        ?: return null

    val result = ArrayList<UiPathPoint>(outer.size + hole.size + 2)
    result += outer.take(outerIndex + 1)
    result += holePoint
    for (offset in 1..hole.size) {
        val index = (holeIndex + offset) % hole.size
        result += hole[index]
    }
    result += outer[outerIndex]
    result += outer.drop(outerIndex + 1)
    return result.cleanedRing()
}

private fun isVisibleBridge(
    start: UiPathPoint,
    end: UiPathPoint,
    outer: List<UiPathPoint>,
    hole: List<UiPathPoint>,
): Boolean {
    if (start == end) return false
    if (!pointInPolygon(midpoint(start, end), outer)) return false
    return !segmentsIntersectAny(start, end, outer, ignored = end) &&
            !segmentsIntersectAny(start, end, hole, ignored = start)
}

private fun segmentsIntersectAny(
    start: UiPathPoint,
    end: UiPathPoint,
    polygon: List<UiPathPoint>,
    ignored: UiPathPoint,
): Boolean {
    for (index in polygon.indices) {
        val a = polygon[index]
        val b = polygon[(index + 1) % polygon.size]
        if (a == ignored || b == ignored) continue
        if (segmentsIntersect(start, end, a, b)) return true
    }
    return false
}

private fun triangulateSimple(points: List<UiPathPoint>): List<UiPathTriangle> {
    val polygon = orient(points, clockwise = false).withoutCollinear()
    if (polygon.size < 3) return emptyList()
    val indices = polygon.indices.toMutableList()
    val triangles = ArrayList<UiPathTriangle>(polygon.size - 2)
    var guard = polygon.size * polygon.size

    while (indices.size > 3 && guard-- > 0) {
        var clipped = false
        for (position in indices.indices) {
            if (isEar(polygon, indices, position)) {
                val previous = indices[(position - 1 + indices.size) % indices.size]
                val current = indices[position]
                val next = indices[(position + 1) % indices.size]
                triangles += UiPathTriangle(polygon[previous], polygon[current], polygon[next])
                indices.removeAt(position)
                clipped = true
                break
            }
        }
        if (!clipped) break
    }

    if (indices.size == 3 && abs(cross(polygon[indices[0]], polygon[indices[1]], polygon[indices[2]])) > Epsilon) {
        triangles += UiPathTriangle(polygon[indices[0]], polygon[indices[1]], polygon[indices[2]])
    }
    return triangles
}

private fun isEar(points: List<UiPathPoint>, indices: List<Int>, position: Int): Boolean {
    val previous = indices[(position - 1 + indices.size) % indices.size]
    val current = indices[position]
    val next = indices[(position + 1) % indices.size]
    val a = points[previous]
    val b = points[current]
    val c = points[next]
    if (cross(a, b, c) <= Epsilon) return false

    for (index in indices) {
        if (index == previous || index == current || index == next) continue
        val point = points[index]
        if (point == a || point == b || point == c) continue
        if (pointInTriangle(point, a, b, c)) return false
    }
    return true
}

private fun List<UiPathPoint>.cleanedRing(): List<UiPathPoint> {
    val clean = ArrayList<UiPathPoint>(size)
    forEach { point ->
        if (clean.lastOrNull() != point) clean += point
    }
    if (clean.size > 1 && clean.first() == clean.last()) clean.removeAt(clean.lastIndex)
    return clean
}

private fun List<UiPathPoint>.withoutCollinear(): List<UiPathPoint> {
    if (size < 3) return this
    val result = ArrayList<UiPathPoint>(size)
    for (index in indices) {
        val previous = this[(index - 1 + size) % size]
        val current = this[index]
        val next = this[(index + 1) % size]
        if (abs(cross(previous, current, next)) > Epsilon) result += current
    }
    return result
}

private fun orient(points: List<UiPathPoint>, clockwise: Boolean): List<UiPathPoint> {
    val isClockwise = polygonArea(points) < 0f
    return if (isClockwise == clockwise) points else points.asReversed()
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

private fun pointInPolygon(point: UiPathPoint, polygon: List<UiPathPoint>): Boolean {
    var inside = false
    for (index in polygon.indices) {
        val a = polygon[index]
        val b = polygon[(index + 1) % polygon.size]
        if (a.y > point.y != b.y > point.y &&
            point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
        ) inside = !inside
    }
    return inside
}

private fun pointInTriangle(point: UiPathPoint, a: UiPathPoint, b: UiPathPoint, c: UiPathPoint): Boolean {
    val ab = cross(a, b, point)
    val bc = cross(b, c, point)
    val ca = cross(c, a, point)
    return ab >= -Epsilon && bc >= -Epsilon && ca >= -Epsilon
}

private fun segmentsIntersect(a: UiPathPoint, b: UiPathPoint, c: UiPathPoint, d: UiPathPoint): Boolean {
    val abC = cross(a, b, c)
    val abD = cross(a, b, d)
    val cdA = cross(c, d, a)
    val cdB = cross(c, d, b)
    return abC * abD < -Epsilon && cdA * cdB < -Epsilon
}

private fun cross(a: UiPathPoint, b: UiPathPoint, c: UiPathPoint): Float {
    return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
}

private fun midpoint(a: UiPathPoint, b: UiPathPoint): UiPathPoint {
    return UiPathPoint((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)
}

private fun UiPathPoint.distanceSquaredTo(other: UiPathPoint): Float {
    val dx = other.x - x
    val dy = other.y - y
    return dx * dx + dy * dy
}

private const val Epsilon = 0.0001f
