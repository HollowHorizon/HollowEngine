package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Assigns each edge two of the three channels so that every corner has one channel in common between
 * its two sides and one that differs, the median of the three then reconstructs the sharp join.
 */
internal fun MsdfShape.colorEdges(angleThreshold: Float = DefaultAngleThreshold) {
    val crossThreshold = sin(angleThreshold)
    var color = MsdfChannel.CYAN
    for (contour in contours) {
        if (contour.edges.isEmpty()) continue
        val corners = findCorners(contour.edges, crossThreshold)
        color = when (corners.size) {
            0 -> switchColor(color, 0).also { smooth -> contour.edges.forEach { it.color = smooth } }
            1 -> colorTeardrop(contour, corners[0], color)
            else -> colorBetweenCorners(contour.edges, corners, color)
        }
    }
    ensureEveryChannelIsUsed()
}

/** msdfgen's default: joins sharper than ~172deg count as corners. */
internal const val DefaultAngleThreshold = 3f

/** Indices of the edges whose join with the previous edge is a corner. */
internal fun findCorners(edges: List<MsdfEdge>, crossThreshold: Float): List<Int> {
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

/** The union of every edge's channels; [MsdfChannel.WHITE] means all three are written somewhere. */
internal fun MsdfShape.usedChannels(): Int {
    var used = 0
    for (contour in contours) for (edge in contour.edges) used = used or edge.color
    return used
}

/**
 * Three color bands around a contour with a single corner, the outer two meeting at it.
 */
private fun colorTeardrop(contour: MsdfContour, corner: Int, startColor: Int): Int {
    val first = switchColor(startColor, 0)
    val last = switchColor(first, 0)
    val palette = intArrayOf(first, MsdfChannel.WHITE, last)
    var cornerIndex = corner
    if (contour.edges.size < 3) {
        val split = ArrayList<MsdfEdge>(contour.edges.size * 3)
        contour.edges.forEach { split += it.splitInThirds() }
        contour.edges.clear()
        contour.edges.addAll(split)
        cornerIndex = corner * 3
    }
    val count = contour.edges.size
    for (offset in 0 until count) {
        contour.edges[(cornerIndex + offset) % count].color = palette[1 + symmetricalTrichotomy(offset, count)]
    }
    return last
}

private fun MsdfShape.ensureEveryChannelIsUsed() {
    val used = usedChannels()
    if (used == MsdfChannel.WHITE || used == 0) return
    for (contour in contours) for (edge in contour.edges) edge.color = MsdfChannel.WHITE
}

private fun colorBetweenCorners(edges: List<MsdfEdge>, corners: List<Int>, startColor: Int): Int {
    val count = edges.size
    var color = switchColor(startColor, 0)
    val initialColor = color
    var spline = 0
    val start = corners[0]
    for (offset in 0 until count) {
        val index = (start + offset) % count
        if (spline + 1 < corners.size && corners[spline + 1] == index) {
            spline++
            val banned = if (spline == corners.size - 1) initialColor else 0
            color = switchColor(color, banned)
        }
        edges[index].color = color
    }
    return color
}

private fun symmetricalTrichotomy(position: Int, n: Int): Int =
    (3.0 + 2.875 * position / (n - 1) - 1.4375 + 0.5).toInt() - 3

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

private fun switchColor(color: Int, banned: Int): Int {
    val combined = color and banned
    if (combined == MsdfChannel.RED || combined == MsdfChannel.GREEN || combined == MsdfChannel.BLUE) {
        return combined xor MsdfChannel.WHITE
    }
    if (color == 0 || color == MsdfChannel.WHITE) return MsdfChannel.CYAN
    val shifted = color shl 1
    return (shifted or (shifted shr 3)) and MsdfChannel.WHITE
}
