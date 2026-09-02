package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.ui

import androidx.compose.runtime.Composable
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal const val MinDragUnit = 3f

internal class TimelinePanGesture {
    var scrollX = 0f
    var scrollY = 0f
    var center = 0f
    var moved = false

    var pressX = 0f
        private set
    var pressY = 0f
        private set
    var pressLocalX = 0f
        private set

    fun begin(event: UiEvent, scrollX: Float, scrollY: Float, center: Float) {
        this.scrollX = scrollX
        this.scrollY = scrollY
        this.center = center
        pressX = event.x
        pressY = event.y
        pressLocalX = event.localX
        moved = false
    }

    fun advance(event: UiEvent): Boolean {
        if (abs(event.dragTotalX) > MinDragUnit || abs(event.dragTotalY) > MinDragUnit) moved = true
        return moved
    }
}

internal data class TimelineMarquee(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val additive: Boolean,
) {
    val x: Float get() = min(fromX, toX)
    val y: Float get() = min(fromY, toY)
    val width: Float get() = abs(toX - fromX)
    val height: Float get() = abs(toY - fromY)

    val isDrag: Boolean get() = width > MinDragUnit || height > MinDragUnit

    fun contains(pointX: Float, pointY: Float): Boolean =
        pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height
}

@Composable
internal fun MarqueeOverlay(marquee: TimelineMarquee?) {
    if (marquee == null || !marquee.isDrag) return
    Box(
        modifier = Modifier.position(marquee.x.px, marquee.y.px)
            .size(max(marquee.width, 1f).px, max(marquee.height, 1f).px)
            .background(TimelineColors.Accent.withAlpha(0.14f)).border(1.px, TimelineColors.Accent.withAlpha(0.7f))
            .inputTransparent(),
    )
}

internal fun UiEvent.isAdditiveSelection(): Boolean = modifiers and (GLFW.GLFW_MOD_CONTROL or GLFW.GLFW_MOD_SHIFT) != 0
