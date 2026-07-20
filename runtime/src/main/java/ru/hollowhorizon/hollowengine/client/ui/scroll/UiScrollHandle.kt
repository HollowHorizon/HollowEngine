package ru.hollowhorizon.hollowengine.client.ui.scroll

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect


class UiScrollHandle {
    var offsetX: Float by mutableStateOf(0f)
        internal set
    var offsetY: Float by mutableStateOf(0f)
        internal set

    /** The scroll container's content box in root coordinates (updated after each layout). */
    var viewport: UiRect by mutableStateOf(UiRect.Zero)
        internal set

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
}

@Composable
fun rememberScrollState(): UiScrollHandle = remember { UiScrollHandle() }
