package ru.hollowhorizon.hollowengine.client.ui.ide.files.animator

import androidx.compose.runtime.Composable
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.inspector.TextRow
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover

internal const val AnimatorStylesheet = "hollowengine:ui/styles/animator-editor.hss"

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
        modifier = modifier.input(hoverable = true, clickable = true).onClick { event ->
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
    active: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Image(
        icon,
        tags = if (active) listOf("animator-icon-button", "active") else listOf("animator-icon-button"),
        modifier = modifier.size((size + 6f).px, (size + 6f).px).input(hoverable = true, clickable = true)
            .tooltipOnHover(tooltip).onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    )
}

/**
 * Field that holding an animation expression.
 */
@Composable
internal fun ExpressionField(label: String, value: String, onChange: (String) -> Unit) = TextRow(
    label = label,
    value = value,
    completions = AnimationExpressionEditing.completions,
    highlighter = AnimationExpressionEditing.highlighter,
    diagnostics = AnimationExpressionEditing.diagnostics(value),
    onChange = onChange,
)
