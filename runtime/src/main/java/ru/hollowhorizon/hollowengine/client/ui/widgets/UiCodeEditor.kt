package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.*

@Composable
fun UiCodeEditor(
    value: String,
    onChange: (String) -> Unit,
    highlighter: UiSyntaxHighlighter? = null,
    completions: UiCompletionContributor? = null,
    diagnostics: List<UiTextDiagnostic> = emptyList(),
    inlayHints: List<UiInlayHint> = emptyList(),
    inlayHintsProvider: UiInlayHintsProvider? = null,
    inlayRevision: Long = 0L,
    readOnly: Boolean = false,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier = Modifier.size(100.percent, 100.percent),
    attributes: Map<String, String> = emptyMap(),
) {
    TextField(
        value = value,
        mode = UiTextFieldMode.MULTI_LINE,
        multiCaret = true,
        syntaxHighlighter = highlighter,
        completionContributor = completions,
        indentSize = 4,
        autoPairs = true,
        readOnly = readOnly,
        diagnostics = diagnostics,
        inlayHints = inlayHints,
        inlayHintsProvider = inlayHintsProvider,
        inlayRevision = inlayRevision,
        onChange = onChange,
        id = id,
        tags = listOf("code-editor") + tags,
        modifier = modifier.input(scrollable = true).textWrap(false),
        attributes = attributes,
    )
}
