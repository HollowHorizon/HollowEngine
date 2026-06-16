package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.*

@Composable
internal fun HollowIdeDiagnosticsBadge(
    fileId: String,
    diagnostics: List<UiTextDiagnostic>,
    onToggle: (String) -> Unit,
) {
    if (diagnostics.isEmpty()) return
    val errors = diagnostics.count { it.severity == UiTextDiagnosticSeverity.ERROR }
    val warnings = diagnostics.count { it.severity == UiTextDiagnosticSeverity.WARNING }
    val label = if (errors > 0) "$errors errors" else "$warnings warnings"
    Row(
        tags = listOf("ide-diagnostics-badge", if (errors > 0) "has-errors" else "has-warnings"),
        modifier = Modifier.then(
            Modifier.align(UiAlign.END, UiAlign.START),
            Modifier.margin(6.px),
            Modifier.translate(z = 20f),
            Modifier.input(clickable = true, hoverable = true),
            Modifier.onClick { event ->
                onToggle(fileId)
                event.consume()
            },
        ),
    ) {
        Text(label)
    }
}

@Composable
internal fun HollowIdeDiagnosticsPanel(
    fileId: String,
    diagnostics: List<UiTextDiagnostic>,
    height: Float,
    onResize: (String, Float) -> Unit,
) {
    Column(tags = listOf("ide-diagnostics-wrap"), modifier = Modifier.size(100.percent, height.px)) {
        Box(
            tags = listOf("ide-diagnostics-resizer"),
            modifier = Modifier.then(
                Modifier.input(clickable = true, hoverable = true),
                Modifier.cursor(UiCursorShape.RESIZE_VERTICAL),
                Modifier.onDrag { event ->
                    onResize(fileId, -event.deltaY)
                    event.consume()
                },
            ),
        )
        LazyColumn(
            tags = listOf("ide-diagnostics-panel"),
            modifier = Modifier.input(scrollable = true),
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
