package ru.hollowhorizon.hollowengine.client.ui.ide.files.animator

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover

internal const val AnimatorStylesheet = "hollowengine:ui/styles/animator-editor.hss"

/**
 * A number that springs towards its target instead of jumping there.
 */
internal class SpringFloat(initial: Float) {
    var value by mutableStateOf(initial)
        private set

    var target: Float = initial

    private var velocity = 0f
    private var lastFrame = 0L

    /** Puts the value there at once, for changes the pointer is already animating by hand. */
    fun snapTo(next: Float) {
        target = next
        value = next
        velocity = 0f
    }

    fun advance(frameNanos: Long) {
        val previous = lastFrame
        lastFrame = frameNanos
        if (previous == 0L) return

        val delta = target - value
        if (kotlin.math.abs(delta) < 0.05f && kotlin.math.abs(velocity) < 0.05f) {
            value = target
            velocity = 0f
            return
        }

        val dt = ((frameNanos - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
        velocity += (delta * 300f - velocity * 32f) * dt
        value += velocity * dt
    }
}

@Composable
internal fun AnimatorButton(
    label: String,
    modifier: Modifier = Modifier,
    color: UiColor = AnimatorColors.Text,
    onClick: () -> Unit,
) {
    Box(
        mode = UiBoxMode.STACK,
        tags = listOf("animator-button"),
        modifier = modifier
            .input(hoverable = true, clickable = true)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    ) {
        Text(label, tags = listOf("animator-button-label"), modifier = Modifier.foreground(color))
    }
}

@Composable
internal fun AnimatorIconButton(
    icon: String,
    tooltip: String,
    size: Float = 16f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Image(
        icon,
        tags = listOf("animator-icon-button"),
        modifier = modifier
            .size((size + 6f).px, (size + 6f).px)
            .input(hoverable = true, clickable = true)
            .tooltipOnHover(tooltip)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    )
}

/**
 * A row of choices that wraps by itself.
 */
@Composable
internal fun AnimatorPillFlow(content: HollowUiContent) {
    Layout(
        content = content,
        modifier = Modifier.size(100.percent, UiLength.Fit).gap(3.px).lineSpacing(3f).textWrap(),
        measurePolicy = UiMeasurePolicies.InlineFlow,
    )
}

/** One choice out of a small set, the shape the editor uses instead of a dropdown. */
@Composable
internal fun AnimatorPill(label: String, active: Boolean, onClick: () -> Unit) {
    InlineWidget(
        id = "animator-pill-$label",
        tags = listOf("animator-pill") + if (active) listOf("active") else emptyList(),
        modifier = Modifier
            .input(hoverable = true, clickable = true)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    ) {
        Text(label, tags = listOf("animator-pill-label"))
    }
}
