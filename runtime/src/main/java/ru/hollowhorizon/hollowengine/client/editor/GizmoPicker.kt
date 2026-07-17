package ru.hollowhorizon.hollowengine.client.editor

import kotlin.math.sqrt

/**
 * Screen-space picking for gizmo handles.
 * Given the pointer position (real mouse, or the crosshair for world interaction)
 */
object GizmoPicker {
    private const val LINE_THRESHOLD_PX = 7f

    fun pick(handles: List<GizmoHandle>, x: Float, y: Float): GizmoHandle? {
        var best: GizmoHandle? = null
        var bestScore = Float.POSITIVE_INFINITY
        var bestDepth = Float.POSITIVE_INFINITY
        for (handle in handles) {
            val distance = distanceTo(handle.pick, x, y) ?: continue
            val score = distance
            if (score < bestScore - 0.001f || (kotlin.math.abs(score - bestScore) <= 0.001f && handle.depth < bestDepth)) {
                best = handle
                bestScore = score
                bestDepth = handle.depth
            }
        }
        return best
    }

    private fun distanceTo(primitive: PickPrimitive, x: Float, y: Float): Float? = when (primitive) {
        is PickPrimitive.Line -> {
            val d = polylineDistance(primitive.points, x, y)
            if (d <= LINE_THRESHOLD_PX) d else null
        }

        is PickPrimitive.Polygon -> {
            if (pointInPolygon(primitive.points, x, y)) -1f
            else {
                val d = polylineDistance(primitive.points + primitive.points.first(), x, y)
                if (d <= LINE_THRESHOLD_PX) d else null
            }
        }

        is PickPrimitive.Disc -> {
            val dx = x - primitive.center.x
            val dy = y - primitive.center.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist <= primitive.radius) dist - primitive.radius else null
        }
    }

    private fun polylineDistance(points: List<Pt>, x: Float, y: Float): Float {
        if (points.isEmpty()) return Float.POSITIVE_INFINITY
        if (points.size == 1) return sqrt((x - points[0].x).let { it * it } + (y - points[0].y).let { it * it })
        var min = Float.POSITIVE_INFINITY
        for (i in 0 until points.size - 1) {
            min = minOf(min, segmentDistance(points[i], points[i + 1], x, y))
        }
        return min
    }

    private fun segmentDistance(a: Pt, b: Pt, x: Float, y: Float): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val lengthSq = abx * abx + aby * aby
        val t = if (lengthSq < 1e-6f) 0f else (((x - a.x) * abx + (y - a.y) * aby) / lengthSq).coerceIn(0f, 1f)
        val projX = a.x + abx * t
        val projY = a.y + aby * t
        val dx = x - projX
        val dy = y - projY
        return sqrt(dx * dx + dy * dy)
    }

    private fun pointInPolygon(points: List<Pt>, x: Float, y: Float): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val pi = points[i]
            val pj = points[j]
            if ((pi.y > y) != (pj.y > y)) {
                val slope = (x - pi.x) * (pj.y - pi.y) - (pj.x - pi.x) * (y - pi.y)
                if ((slope < 0f) != (pj.y < pi.y)) inside = !inside
            }
            j = i
        }
        return inside
    }
}
