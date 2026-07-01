package ru.hollowhorizon.hollowengine.client.ui.scroll

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
    private val offsets = WeakHashMap<ru.hollowhorizon.hollowengine.client.ui.UiNode, UiScrollOffset>()
    private val targets = WeakHashMap<ru.hollowhorizon.hollowengine.client.ui.UiNode, UiScrollOffset>()
    private val starts = WeakHashMap<ru.hollowhorizon.hollowengine.client.ui.UiNode, UiScrollOffset>()
    private val startedAt = WeakHashMap<ru.hollowhorizon.hollowengine.client.ui.UiNode, Long>()
    private val ranges = WeakHashMap<ru.hollowhorizon.hollowengine.client.ui.UiNode, UiScrollOffset>()
    private val durationMillis = 190L

    fun offset(node: ru.hollowhorizon.hollowengine.client.ui.UiNode): UiScrollOffset = offsets[node] ?: UiScrollOffset.Zero

    fun range(node: ru.hollowhorizon.hollowengine.client.ui.UiNode): UiScrollOffset = ranges[node] ?: UiScrollOffset.Zero

    fun isAnimating(): Boolean = startedAt.isNotEmpty()

    fun scroll(node: ru.hollowhorizon.hollowengine.client.ui.UiNode, deltaX: Float, deltaY: Float): UiScrollOffset {
        val current = targets[node] ?: offset(node)
        val range = range(node)
        val next = UiScrollOffset(
            x = (current.x + deltaX).coerceIn(0f, range.x),
            y = (current.y + deltaY).coerceIn(0f, range.y),
        )
        animateTo(node, next)
        return next
    }

    fun set(node: ru.hollowhorizon.hollowengine.client.ui.UiNode, x: Float? = null, y: Float? = null): UiScrollOffset {
        val next = resolveNext(node, x, y)
        animateTo(node, next)
        return next
    }

    fun setImmediate(node: ru.hollowhorizon.hollowengine.client.ui.UiNode, x: Float? = null, y: Float? = null): UiScrollOffset {
        val next = resolveNext(node, x, y)
        offsets[node] = next
        targets[node] = next
        starts.remove(node)
        startedAt.remove(node)
        return next
    }

    private fun resolveNext(node: ru.hollowhorizon.hollowengine.client.ui.UiNode, x: Float? = null, y: Float?): UiScrollOffset {
        val current = targets[node] ?: offset(node)
        val range = range(node)
        return UiScrollOffset(
            x = (x ?: current.x).coerceIn(0f, range.x),
            y = (y ?: current.y).coerceIn(0f, range.y),
        )
    }

    fun clamp(node: ru.hollowhorizon.hollowengine.client.ui.UiNode, range: UiScrollOffset): UiScrollOffset {
        ranges[node] = range
        val current = offset(node)
        val clamped = UiScrollOffset(current.x.coerceIn(0f, range.x), current.y.coerceIn(0f, range.y))
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
            offsets[node] = next
            if (progress >= 1f) {
                offsets[node] = target
                starts.remove(node)
                startedAt.remove(node)
            }
        }
    }

    private fun animateTo(node: ru.hollowhorizon.hollowengine.client.ui.UiNode, next: UiScrollOffset) {
        starts[node] = offsets[node] ?: UiScrollOffset.Zero
        targets[node] = next
        startedAt[node] = System.currentTimeMillis()
    }
}
