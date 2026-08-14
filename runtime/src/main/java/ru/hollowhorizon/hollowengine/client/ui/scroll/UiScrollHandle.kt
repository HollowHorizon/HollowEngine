package ru.hollowhorizon.hollowengine.client.ui.scroll

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect

/**
 * The scroll position of one scroll container.
 */
class UiScrollHandle {
    var offsetX: Float by mutableStateOf(0f)
        internal set
    var offsetY: Float by mutableStateOf(0f)
        internal set

    /** The scroll container's content box in root coordinates (updated after each layout). */
    var viewport: UiRect by mutableStateOf(UiRect.Zero)
        internal set

    /** How far each axis can still travel: the content size minus the viewport, never negative. */
    var range: UiScrollOffset by mutableStateOf(UiScrollOffset.Zero)
        internal set

    val offset: UiScrollOffset get() = UiScrollOffset(offsetX, offsetY)

    internal var target = UiScrollOffset.Zero
    internal var animationStart = UiScrollOffset.Zero
    internal var animationStartedAt = 0L
    internal var animating = false

    internal var pendingX: Float? = null
    internal var pendingY: Float? = null
    internal var pendingAnimatedDeltaX = 0f
    internal var pendingAnimatedDeltaY = 0f

    fun scrollTo(x: Float? = null, y: Float? = null) {
        if (x != null) pendingX = x
        if (y != null) pendingY = y
    }

    fun scrollBy(deltaX: Float = 0f, deltaY: Float = 0f) {
        scrollTo(
            x = (pendingX ?: offsetX) + deltaX,
            y = (pendingY ?: offsetY) + deltaY,
        )
    }

    fun animateScrollBy(deltaX: Float = 0f, deltaY: Float = 0f) {
        pendingAnimatedDeltaX += deltaX
        pendingAnimatedDeltaY += deltaY
    }

    internal fun applyOffset(next: UiScrollOffset): Boolean {
        if (offsetX == next.x && offsetY == next.y) return false
        offsetX = next.x
        offsetY = next.y
        return true
    }

    internal fun clampedTo(range: UiScrollOffset, x: Float? = null, y: Float? = null) = UiScrollOffset(
        x = (x ?: target.x).coerceIn(0f, range.x),
        y = (y ?: target.y).coerceIn(0f, range.y),
    )
}

@Composable
fun rememberScrollState(): UiScrollHandle = remember { UiScrollHandle() }
