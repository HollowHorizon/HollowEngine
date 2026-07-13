package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.*
import kotlinx.coroutines.isActive
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.TransitionEasing
import ru.hollowhorizon.hollowengine.client.ui.style.UiTransition

@Composable
fun animateFloatAsState(
    target: Float,
    durationMillis: Long = 180L,
    easing: TransitionEasing = TransitionEasing.EASE_OUT,
): State<Float> {
    val state = remember { mutableStateOf(target) }
    LaunchedEffect(target, durationMillis) {
        val from = state.value
        if (durationMillis <= 0L || from == target) {
            state.value = target
            return@LaunchedEffect
        }
        val durationNanos = durationMillis * 1_000_000L
        var startNanos = -1L
        while (isActive) {
            var finished = false
            withFrameNanos { now ->
                if (startNanos < 0L) startNanos = now
                val progress = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                state.value = from + (target - from) * easing.transform(progress)
                if (progress >= 1f) finished = true
            }
            if (finished) {
                state.value = target
                break
            }
        }
    }
    return state
}

@Composable
fun AnimatedVisibility(
    visible: Boolean,
    modifier: Modifier? = null,
    durationMillis: Long = 180L,
    easing: TransitionEasing = TransitionEasing.EASE_IN_OUT,
    slideY: Float = 0f,
    scaleFrom: Float = 1f,
    content: HollowUiContent,
) {
    var present by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            present = true
            // Render one frame at the hidden state so the transition has a 0→1 delta to animate.
            withFrameNanos { }
            shown = true
        } else {
            shown = false
            // Keep the node mounted for the exit transition, then drop it.
            val startNanos = withFrameNanos { it }
            val durationNanos = durationMillis * 1_000_000L
            while (isActive && withFrameNanos { it } - startNanos < durationNanos) { /* wait out exit */ }
            present = false
        }
    }

    if (!present) return

    var animated = Modifier.opacity(if (shown) 1f else 0f)
    if (slideY != 0f) animated = animated.translate(y = if (shown) 0f else slideY)
    if (scaleFrom != 1f) animated = animated.scale(if (shown) 1f else scaleFrom)
    animated = animated.transition(UiTransition("all", durationMillis, easing))

    Box(modifier = animated.then(modifier ?: Modifier), content = content)
}
