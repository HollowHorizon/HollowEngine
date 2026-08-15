package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import net.minecraft.client.Minecraft
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
        Row(
            tags = listOf("ide-diagnostics-header"),
            modifier = Modifier.alignItems(vertical = UiAlign.CENTER),
        ) {
            Text(diagnosticsSummary(diagnostics), tags = listOf("ide-diagnostics-title"))
            Box(modifier = Modifier.size(0.px, 100.percent).grow(1f))
            CopyButton("Copy all") { diagnostics.joinToString("\n", transform = UiTextDiagnostic::clipboardLine) }
        }
        Column(
            tags = listOf("ide-diagnostics-panel"),
            modifier = Modifier.scrollable(),
        ) {
            diagnostics.forEach { diagnostic ->
                Row(
                    tags = listOf("ide-diagnostic-row", diagnostic.severity.name.lowercase()),
                    modifier = Modifier.alignItems(vertical = UiAlign.CENTER),
                ) {
                    Text(diagnostic.severity.name, tags = listOf("ide-diagnostic-severity"))
                    Text(diagnostic.locationLabel(), tags = listOf("ide-diagnostic-location"))
                    Text(diagnostic.message, tags = listOf("ide-diagnostic-message"))
                    CopyButton("Copy this message") { diagnostic.clipboardLine() }
                }
            }
        }
    }
}

@Composable
private fun CopyButton(tooltip: String, text: () -> String) {
    Box(
        tags = listOf("ide-diagnostic-copy"),
        attributes = mapOf("title" to tooltip),
        modifier = Modifier.input(clickable = true, hoverable = true)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                Minecraft.getInstance().keyboardHandler.clipboard = text()
                event.consume()
            },
    ) {
        Image(
            Assets.Hollowengine.Textures.Gui.Icons.COPY.toString(),
            tags = listOf("ide-diagnostic-copy-icon"),
            modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER),
        )
    }
}

private fun diagnosticsSummary(diagnostics: List<UiTextDiagnostic>): String {
    val errors = diagnostics.count { it.severity == UiTextDiagnosticSeverity.ERROR }
    val warnings = diagnostics.count { it.severity == UiTextDiagnosticSeverity.WARNING }
    return buildList {
        if (errors > 0) add("$errors error${if (errors == 1) "" else "s"}")
        if (warnings > 0) add("$warnings warning${if (warnings == 1) "" else "s"}")
        if (isEmpty()) add("${diagnostics.size} message${if (diagnostics.size == 1) "" else "s"}")
    }.joinToString(", ")
}

private fun UiTextDiagnostic.locationLabel(): String {
    return if (line > 0 && column > 0) "$line:$column" else "-"
}

private fun UiTextDiagnostic.clipboardLine(): String = "${locationLabel()} ${severity.name}: $message"
