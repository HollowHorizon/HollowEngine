package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import java.util.*

data class UiScrollOffset(
    val x: Float = 0f,
    val y: Float = 0f,
) {
    companion object {
        val Zero = UiScrollOffset()
    }
}

internal fun UiScrollOffset.hasScrollableAxis(): Boolean = x > 0f || y > 0f

enum class ScrollbarOrientation {
    VERTICAL,
    HORIZONTAL,
}

data class UiScrollbarGeometry(
    val track: UiRect,
    val thumb: UiRect,
    val orientation: ScrollbarOrientation,
)

class UiScrollState {
    private val offsets = WeakHashMap<UiNode, UiScrollOffset>()
    private val targets = WeakHashMap<UiNode, UiScrollOffset>()
    private val starts = WeakHashMap<UiNode, UiScrollOffset>()
    private val startedAt = WeakHashMap<UiNode, Long>()
    private val ranges = WeakHashMap<UiNode, UiScrollOffset>()
    private val durationMillis = 190L

    /** Bumped whenever any effective offset changes; lets frame caches detect scrolling. */
    var revision: Long = 0L
        private set

    fun offset(node: UiNode): UiScrollOffset = offsets[node] ?: UiScrollOffset.Zero

    fun range(node: UiNode): UiScrollOffset = ranges[node] ?: UiScrollOffset.Zero

    fun isAnimating(): Boolean = startedAt.isNotEmpty()

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset {
        val current = targets[node] ?: offset(node)
        val range = range(node)
        val next = UiScrollOffset(
            x = (current.x + deltaX).coerceIn(0f, range.x),
            y = (current.y + deltaY).coerceIn(0f, range.y),
        )
        animateTo(node, next)
        return next
    }

    fun set(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset {
        val next = resolveNext(node, x, y)
        animateTo(node, next)
        return next
    }

    fun setImmediate(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset {
        val next = resolveNext(node, x, y)
        if (offsets[node] != next || targets[node] != next) revision++
        offsets[node] = next
        targets[node] = next
        starts.remove(node)
        startedAt.remove(node)
        return next
    }

    private fun resolveNext(node: UiNode, x: Float? = null, y: Float?): UiScrollOffset {
        val current = targets[node] ?: offset(node)
        val range = range(node)
        return UiScrollOffset(
            x = (x ?: current.x).coerceIn(0f, range.x),
            y = (y ?: current.y).coerceIn(0f, range.y),
        )
    }

    fun clamp(node: UiNode, range: UiScrollOffset): UiScrollOffset {
        ranges[node] = range
        val current = offset(node)
        val clamped = UiScrollOffset(current.x.coerceIn(0f, range.x), current.y.coerceIn(0f, range.y))
        if (clamped != current) revision++
        offsets[node] = clamped
        targets[node] =
            (targets[node] ?: clamped).let { UiScrollOffset(it.x.coerceIn(0f, range.x), it.y.coerceIn(0f, range.y)) }
        return clamped
    }

    fun update(nowMillis: Long) {
        for ((node, target) in targets.toMap()) {
            val startTime = startedAt[node] ?: continue
            val start = starts[node] ?: offsets[node] ?: UiScrollOffset.Zero
            val progress = ((nowMillis - startTime).toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
            val eased = 1f - (1f - progress) * (1f - progress)
            val next = UiScrollOffset(
                x = start.x + (target.x - start.x) * eased,
                y = start.y + (target.y - start.y) * eased,
            )
            if (offsets[node] != next) revision++
            offsets[node] = next
            if (progress >= 1f) {
                offsets[node] = target
                starts.remove(node)
                startedAt.remove(node)
            }
        }
    }

    private fun animateTo(node: UiNode, next: UiScrollOffset) {
        starts[node] = offsets[node] ?: UiScrollOffset.Zero
        targets[node] = next
        startedAt[node] = System.currentTimeMillis()
        revision++
    }
}
