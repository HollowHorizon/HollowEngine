package ru.hollowhorizon.hollowengine.client.ui.graph

import kotlin.math.round

/** Which edge or middle of the selection nodes are lined up on. */
enum class GraphAlignment {
    LEFT, CENTER_X, RIGHT, TOP, CENTER_Y, BOTTOM,
}

/** Node alignment: alignment along a line, uniform spacing, and grid placement. */
object GraphArrange {
    /** Lines [boxes] up on the [alignment] edge or middle of all of them together. */
    fun <K> align(boxes: Map<K, GraphRect>, alignment: GraphAlignment): Map<K, Pair<Float, Float>> {
        val around = GraphRect.around(boxes.values) ?: return emptyMap()
        return boxes.mapValues { (_, box) ->
            when (alignment) {
                GraphAlignment.LEFT -> around.x to box.y
                GraphAlignment.CENTER_X -> around.centerX - box.width / 2f to box.y
                GraphAlignment.RIGHT -> around.x + around.width - box.width to box.y
                GraphAlignment.TOP -> box.x to around.y
                GraphAlignment.CENTER_Y -> box.x to around.centerY - box.height / 2f
                GraphAlignment.BOTTOM -> box.x to around.y + around.height - box.height
            }
        }.filter { (key, corner) -> boxes.getValue(key).movedTo(corner) }
    }

    /**
     * Spaces [boxes] out along one axis with the same gap between each two, keeping the first and the
     * last where they are. Nodes of different sizes get equal gaps, not equal steps, as they would by hand.
     */
    fun <K> distribute(boxes: Map<K, GraphRect>, horizontal: Boolean): Map<K, Pair<Float, Float>> {
        if (boxes.size < 3) return emptyMap()
        val ordered = boxes.entries.sortedBy { (_, box) -> if (horizontal) box.x else box.y }
        val first = ordered.first().value
        val last = ordered.last().value
        val start = if (horizontal) first.x else first.y
        val end = if (horizontal) last.x + last.width else last.y + last.height
        val occupied = ordered.sumOf { (_, box) -> (if (horizontal) box.width else box.height).toDouble() }.toFloat()
        val gap = (end - start - occupied) / (ordered.size - 1)

        val result = LinkedHashMap<K, Pair<Float, Float>>()
        var at = start
        ordered.forEach { (key, box) ->
            val corner = if (horizontal) at to box.y else box.x to at
            if (box.movedTo(corner)) result[key] = corner
            at += (if (horizontal) box.width else box.height) + gap
        }
        return result
    }

    /** [value] on the nearest line of a grid [step] apart. */
    fun snap(value: Float, step: Float): Float = if (step <= 0f) value else round(value / step) * step

    private fun GraphRect.movedTo(corner: Pair<Float, Float>): Boolean = corner.first != x || corner.second != y
}
