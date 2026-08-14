package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect

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

/**
 * The surface's scroll clock: it eases offsets towards their targets and counts a [revision] that
 * frame caches use to notice scrolling.
 *
 * It stores no offsets of its own. Every scroll container carries its own [UiScrollHandle] in its
 * [ru.hollowhorizon.hollowengine.client.ui.UiScrollSpec].
 */
class UiScrollState {
    private val animating = LinkedHashSet<UiScrollHandle>()
    private val durationMillis = 190L

    /** Bumped whenever any effective offset changes; lets frame caches detect scrolling. */
    var revision: Long = 0L
        private set

    private var clockMillis = 0L
    private var clockInitialized = false

    fun scroll(handle: UiScrollHandle, deltaX: Float, deltaY: Float): UiScrollOffset {
        val next = handle.clampedTo(handle.range, handle.target.x + deltaX, handle.target.y + deltaY)
        animateTo(handle, next)
        return next
    }

    fun set(handle: UiScrollHandle, x: Float? = null, y: Float? = null): UiScrollOffset {
        val next = handle.clampedTo(handle.range, x, y)
        animateTo(handle, next)
        return next
    }

    fun setImmediate(handle: UiScrollHandle, x: Float? = null, y: Float? = null): UiScrollOffset {
        val next = handle.clampedTo(handle.range, x, y)
        settle(handle, next)
        return next
    }

    /** Records a container's freshly measured travel and re-clamps its offset into it. */
    fun clamp(handle: UiScrollHandle, range: UiScrollOffset): UiScrollOffset {
        handle.range = range
        handle.target = handle.clampedTo(range, handle.target.x, handle.target.y)
        val clamped = handle.clampedTo(range, handle.offsetX, handle.offsetY)
        if (handle.applyOffset(clamped)) revision++
        return clamped
    }

    fun update(nowMillis: Long) {
        clockMillis = nowMillis
        clockInitialized = true
        if (animating.isEmpty()) return
        val iterator = animating.iterator()
        while (iterator.hasNext()) {
            val handle = iterator.next()
            val progress = ((nowMillis - handle.animationStartedAt).toFloat() / durationMillis).coerceIn(0f, 1f)
            val eased = 1f - (1f - progress) * (1f - progress)
            val start = handle.animationStart
            val target = handle.target
            val next = if (progress >= 1f) target else UiScrollOffset(
                x = start.x + (target.x - start.x) * eased,
                y = start.y + (target.y - start.y) * eased,
            )
            if (handle.applyOffset(next)) revision++
            if (progress >= 1f) {
                handle.animating = false
                iterator.remove()
            }
        }
    }

    /** Drains the requests a composable queued on [handle] through `scrollTo`/`animateScrollBy`. */
    internal fun applyPendingRequests(handle: UiScrollHandle) {
        if (handle.pendingX != null || handle.pendingY != null) {
            setImmediate(handle, handle.pendingX, handle.pendingY)
            handle.pendingX = null
            handle.pendingY = null
        }
        if (handle.pendingAnimatedDeltaX != 0f || handle.pendingAnimatedDeltaY != 0f) {
            scroll(handle, handle.pendingAnimatedDeltaX, handle.pendingAnimatedDeltaY)
            handle.pendingAnimatedDeltaX = 0f
            handle.pendingAnimatedDeltaY = 0f
        }
    }

    private fun animateTo(handle: UiScrollHandle, next: UiScrollOffset) {
        if (!clockInitialized) {
            settle(handle, next)
            return
        }
        handle.animationStart = handle.offset
        handle.target = next
        handle.animationStartedAt = clockMillis
        handle.animating = true
        animating += handle
        revision++
    }

    private fun settle(handle: UiScrollHandle, next: UiScrollOffset) {
        handle.target = next
        if (handle.applyOffset(next)) revision++
        if (handle.animating) {
            handle.animating = false
            animating -= handle
        }
    }
}
