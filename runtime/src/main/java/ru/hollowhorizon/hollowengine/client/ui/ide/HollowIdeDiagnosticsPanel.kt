package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextDiagnostic
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextDiagnosticSeverity
import ru.hollowhorizon.hollowengine.generated.Assets

@Composable
internal fun HollowIdeDiagnosticsBadge(
    fileId: String,
    diagnostics: List<UiTextDiagnostic>,
    onToggle: (String) -> Unit,
) {
    if (diagnostics.isEmpty()) return
    val errors = diagnostics.count { it.severity == UiTextDiagnosticSeverity.ERROR }
    val warnings = diagnostics.count { it.severity == UiTextDiagnosticSeverity.WARNING }
    Row(
        tags = listOf("ide-diagnostics-badge", if (errors > 0) "has-errors" else "has-warnings"),
        modifier = Modifier.align(UiAlign.END, UiAlign.START)
            .input(clickable = true, hoverable = true)
            .onClick { event ->
                onToggle(fileId)
                event.consume()
            }
    ) {
        if (errors > 0) {
            Image(Assets.Hollowengine.Textures.Gui.Icons.ERROR.toString(), modifier = Modifier.size(10.px, 10.px))
            Text(errors.toString(), tags = listOf("ide-diagnostics-text"))
        }
        if (warnings > 0) {
            Image(Assets.Hollowengine.Textures.Gui.Icons.WARN.toString(), modifier = Modifier.size(10.px, 10.px))
            Text(warnings.toString(), tags = listOf("ide-diagnostics-text"))
        }
    }
}

@Composable
internal fun HollowIdeDiagnosticsPanel(
    fileId: String,
    diagnostics: List<UiTextDiagnostic>,
    height: Float,
    onResize: (String, Float) -> Unit,
) {
    val dragStartHeight = remember(fileId) { floatArrayOf(height) }
    Column(tags = listOf("ide-diagnostics-wrap"), modifier = Modifier.size(100.percent, height.px)) {
        Box(
            tags = listOf("ide-diagnostics-resizer"),
            modifier = Modifier.input(clickable = true, hoverable = true)
                .cursor(UiCursorShape.RESIZE_VERTICAL)
                .onPress { dragStartHeight[0] = height }
                .onDrag { event ->
                    onResize(fileId, dragStartHeight[0] - event.dragTotalY)
                    event.consume()
                }
        )
        Column(
            tags = listOf("ide-diagnostics-panel"),
            modifier = Modifier.scrollable(),
        ) {
            diagnostics.forEach { diagnostic ->
                Row(tags = listOf("ide-diagnostic-row", diagnostic.severity.name.lowercase())) {
                    Text(diagnostic.severity.name, tags = listOf("ide-diagnostic-severity"))
                    Text(diagnostic.locationLabel(), tags = listOf("ide-diagnostic-location"))
                    Text(diagnostic.message, tags = listOf("ide-diagnostic-message"))
                }
            }
        }
    }
}

private fun UiTextDiagnostic.locationLabel(): String {
    return if (line > 0 && column > 0) "$line:$column" else "-"
}
