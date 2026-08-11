package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Outline geometry and a multi-channel signed distance field generator over it. Port of msdfgen's
 * algorithm, enough to bake a TrueType face into the same atlas format the UI already renders.
 */
internal object MsdfChannel {
    const val RED = 1
    const val GREEN = 2
    const val BLUE = 4
    const val YELLOW = RED or GREEN
    const val MAGENTA = RED or BLUE
    const val CYAN = GREEN or BLUE
    const val WHITE = RED or GREEN or BLUE
}

/**
 * One outline segment as a polyline. [points] holds `x, y` pairs; the first and last are the
 * segment's own endpoints, everything between comes from flattening a curve.
 */
internal class MsdfEdge(val points: FloatArray) {
    var color: Int = MsdfChannel.WHITE

    val subSegments: Int get() = points.size / 2 - 1

    private val minX: Float
    private val minY: Float
    private val maxX: Float
    private val maxY: Float

    init {
        var left = Float.MAX_VALUE
        var bottom = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var top = -Float.MAX_VALUE
        var index = 0
        while (index < points.size) {
            left = min(left, points[index])
            right = max(right, points[index])
            bottom = min(bottom, points[index + 1])
            top = max(top, points[index + 1])
            index += 2
        }
        minX = left
        minY = bottom
        maxX = right
        maxY = top
    }

    fun boundingDistance(x: Float, y: Float): Float {
        val dx = max(max(minX - x, x - maxX), 0f)
        val dy = max(max(minY - y, y - maxY), 0f)
        return sqrt(dx * dx + dy * dy)
    }

    val startX: Float get() = points[0]
    val startY: Float get() = points[1]
    val endX: Float get() = points[points.size - 2]
    val endY: Float get() = points[points.size - 1]

    val startDirX: Float get() = points[2] - points[0]
    val startDirY: Float get() = points[3] - points[1]

    val endDirX: Float get() = points[points.size - 2] - points[points.size - 4]
    val endDirY: Float get() = points[points.size - 1] - points[points.size - 3]
}

/** A closed loop of edges. */
internal class MsdfContour {
    val edges = ArrayList<MsdfEdge>()

    /** Twice the enclosed area, signed: positive counter-clockwise. Only its sign is used. */
    fun doubleSignedArea(): Float {
        var sum = 0f
        for (edge in edges) {
            val points = edge.points
            var index = 0
            while (index + 3 < points.size) {
                sum += points[index] * points[index + 3] - points[index + 2] * points[index + 1]
                index += 2
            }
        }
        return sum
    }

    fun reverse() {
        for (edge in edges) {
            val points = edge.points
            var head = 0
            var tail = points.size - 2
            while (head < tail) {
                val x = points[head]
                val y = points[head + 1]
                points[head] = points[tail]
                points[head + 1] = points[tail + 1]
                points[tail] = x
                points[tail + 1] = y
                head += 2
                tail -= 2
            }
        }
        edges.reverse()
    }
}

internal class MsdfShape {
    val contours = ArrayList<MsdfContour>()

    val isEmpty: Boolean get() = contours.all { it.edges.isEmpty() }

    fun orientForPositiveInside() {
        val area = contours.sumOf { it.doubleSignedArea().toDouble() }
        if (area <= 0.0) return
        contours.forEach { it.reverse() }
    }

    fun bounds(): FloatArray? {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (contour in contours) {
            for (edge in contour.edges) {
                val points = edge.points
                var index = 0
                while (index < points.size) {
                    minX = minOf(minX, points[index])
                    maxX = max(maxX, points[index])
                    minY = minOf(minY, points[index + 1])
                    maxY = max(maxY, points[index + 1])
                    index += 2
                }
            }
        }
        if (minX > maxX) return null
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    fun colorEdges(angleThreshold: Float = DefaultAngleThreshold) {
        val crossThreshold = sin(angleThreshold)
        for (contour in contours) {
            val edges = contour.edges
            if (edges.isEmpty()) continue
            val corners = findCorners(edges, crossThreshold)
            when {
                corners.isEmpty() -> edges.forEach { it.color = MsdfChannel.WHITE }
                else -> colorBetweenCorners(edges, corners)
            }
        }
    }

    private fun findCorners(edges: List<MsdfEdge>, crossThreshold: Float): List<Int> {
        val corners = ArrayList<Int>()
        var previous = edges.last()
        for (index in edges.indices) {
            val edge = edges[index]
            if (isCorner(previous.endDirX, previous.endDirY, edge.startDirX, edge.startDirY, crossThreshold)) {
                corners += index
            }
            previous = edge
        }
        return corners
    }

    private fun colorBetweenCorners(edges: List<MsdfEdge>, corners: List<Int>) {
        val count = edges.size
        var color = MsdfChannel.CYAN
        val initialColor = color
        var spline = 0
        val start = corners[0]
        for (offset in 0 until count) {
            val index = (start + offset) % count
            if (spline + 1 < corners.size && corners[spline + 1] == index) {
                spline++
                // The final run wraps back onto the first one, so it must not reuse its colour.
                val banned = if (spline == corners.size - 1) initialColor else 0
                color = switchColor(color, banned)
            }
            edges[index].color = color
        }
    }

    private companion object {
        /** msdfgen's default: joins sharper than ~172deg count as corners. */
        const val DefaultAngleThreshold = 3f
    }
}

private fun isCorner(ax: Float, ay: Float, bx: Float, by: Float, crossThreshold: Float): Boolean {
    val aLength = sqrt(ax * ax + ay * ay)
    val bLength = sqrt(bx * bx + by * by)
    if (aLength == 0f || bLength == 0f) return true
    val nax = ax / aLength
    val nay = ay / aLength
    val nbx = bx / bLength
    val nby = by / bLength
    return nax * nbx + nay * nby <= 0f || abs(nax * nby - nay * nbx) > crossThreshold
}

/** Rotates through the two-channel colors, skipping any channel pair [banned] would collide with. */
private fun switchColor(color: Int, banned: Int): Int {
    val combined = color and banned
    if (combined == MsdfChannel.RED || combined == MsdfChannel.GREEN || combined == MsdfChannel.BLUE) {
        return combined xor MsdfChannel.WHITE
    }
    if (color == 0 || color == MsdfChannel.WHITE) return MsdfChannel.CYAN
    val shifted = color shl 1
    return (shifted or (shifted shr 3)) and MsdfChannel.WHITE
}

/** The nearest edge found so far for one channel, in msdfgen's (distance, dot) ordering. */
private class NearestEdge {
    var distance = Float.MAX_VALUE
    var dot = 0f
    var param = 0f
    var edge: MsdfEdge? = null

    fun reset() {
        distance = Float.MAX_VALUE
        dot = 0f
        param = 0f
        edge = null
    }

    fun accept(edge: MsdfEdge, distance: Float, dot: Float, param: Float) {
        val candidate = abs(distance)
        val current = abs(this.distance)
        if (candidate < current || (candidate == current && dot < this.dot)) {
            this.distance = distance
            this.dot = dot
            this.param = param
            this.edge = edge
        }
    }
}

/** Scratch results of one edge's distance query, avoiding an allocation per pixel per edge. */
private class EdgeDistance {
    var distance = 0f
    var dot = 0f
    var param = 0f
}

internal fun generateMsdf(
    shape: MsdfShape,
    width: Int,
    height: Int,
    scale: Float,
    translateX: Float,
    translateY: Float,
    range: Float,
    output: FloatArray,
) {
    val red = NearestEdge()
    val green = NearestEdge()
    val blue = NearestEdge()
    val scratch = EdgeDistance()
    for (row in 0 until height) {
        val sampleY = (row + 0.5f) / scale - translateY
        for (column in 0 until width) {
            val sampleX = (column + 0.5f) / scale - translateX
            red.reset()
            green.reset()
            blue.reset()
            for (contour in shape.contours) {
                for (edge in contour.edges) {
                    val wantsRed = edge.color and MsdfChannel.RED != 0
                    val wantsGreen = edge.color and MsdfChannel.GREEN != 0
                    val wantsBlue = edge.color and MsdfChannel.BLUE != 0
                    val bound = edge.boundingDistance(sampleX, sampleY)
                    val couldWin = (wantsRed && bound < abs(red.distance)) ||
                            (wantsGreen && bound < abs(green.distance)) ||
                            (wantsBlue && bound < abs(blue.distance))
                    if (!couldWin) continue
                    edge.signedDistance(sampleX, sampleY, scratch)
                    if (wantsRed) red.accept(edge, scratch.distance, scratch.dot, scratch.param)
                    if (wantsGreen) green.accept(edge, scratch.distance, scratch.dot, scratch.param)
                    if (wantsBlue) blue.accept(edge, scratch.distance, scratch.dot, scratch.param)
                }
            }
            val offset = (row * width + column) * 3
            output[offset] = red.resolve(sampleX, sampleY, range)
            output[offset + 1] = green.resolve(sampleX, sampleY, range)
            output[offset + 2] = blue.resolve(sampleX, sampleY, range)
        }
    }
}

/** Beyond an edge's endpoints the *perpendicular* distance to its tangent is what keeps corners sharp. */
private fun NearestEdge.resolve(x: Float, y: Float, range: Float): Float {
    val edge = edge ?: return 0f
    var resolved = distance
    if (param < 0f) {
        val length = sqrt(edge.startDirX * edge.startDirX + edge.startDirY * edge.startDirY)
        if (length > 0f) {
            val dirX = edge.startDirX / length
            val dirY = edge.startDirY / length
            val aqX = x - edge.startX
            val aqY = y - edge.startY
            if (aqX * dirX + aqY * dirY < 0f) {
                val pseudo = aqX * dirY - aqY * dirX
                if (abs(pseudo) <= abs(resolved)) resolved = pseudo
            }
        }
    } else if (param > 1f) {
        val length = sqrt(edge.endDirX * edge.endDirX + edge.endDirY * edge.endDirY)
        if (length > 0f) {
            val dirX = edge.endDirX / length
            val dirY = edge.endDirY / length
            val bqX = x - edge.endX
            val bqY = y - edge.endY
            if (bqX * dirX + bqY * dirY > 0f) {
                val pseudo = bqX * dirY - bqY * dirX
                if (abs(pseudo) <= abs(resolved)) resolved = pseudo
            }
        }
    }
    return resolved / range + 0.5f
}

/**
 * Closest point on this edge to `(x, y)`. [EdgeDistance.param] is the position along the whole edge:
 * below 0 or above 1 means the projection fell outside, which the pseudo-distance step then handles.
 */
private fun MsdfEdge.signedDistance(x: Float, y: Float, out: EdgeDistance) {
    var bestDistance = Float.MAX_VALUE
    var bestAbsolute = Float.MAX_VALUE
    var bestDot = 0f
    var bestParam = 0f
    val segments = subSegments
    for (segment in 0 until segments) {
        val index = segment * 2
        val ax = points[index]
        val ay = points[index + 1]
        val abx = points[index + 2] - ax
        val aby = points[index + 3] - ay
        val abLengthSquared = abx * abx + aby * aby
        if (abLengthSquared == 0f) continue
        val aqx = x - ax
        val aqy = y - ay
        val param = (aqx * abx + aqy * aby) / abLengthSquared
        val towardsEnd = param > 0.5f
        val eqx = (if (towardsEnd) points[index + 2] else ax) - x
        val eqy = (if (towardsEnd) points[index + 3] else ay) - y
        val endpointDistance = sqrt(eqx * eqx + eqy * eqy)
        var distance: Float
        var dot: Float
        val abLength = sqrt(abLengthSquared)
        val orthogonal = (aby * aqx - abx * aqy) / abLength
        if (param > 0f && param < 1f && abs(orthogonal) < endpointDistance) {
            distance = orthogonal
            dot = 0f
        } else {
            val cross = aqx * aby - aqy * abx
            distance = if (cross >= 0f) endpointDistance else -endpointDistance
            dot = if (endpointDistance == 0f) {
                0f
            } else {
                abs((abx * eqx + aby * eqy) / (abLength * endpointDistance))
            }
        }
        val absolute = abs(distance)
        if (absolute < bestAbsolute || (absolute == bestAbsolute && dot < bestDot)) {
            bestAbsolute = absolute
            bestDistance = distance
            bestDot = dot
            bestParam = (segment + param) / segments
        }
    }
    out.distance = bestDistance
    out.dot = bestDot
    out.param = bestParam
}
