package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.TextField
import ru.hollowhorizon.hollowengine.client.ui.UiCompletionContributor
import ru.hollowhorizon.hollowengine.client.ui.UiInlayHint
import ru.hollowhorizon.hollowengine.client.ui.UiInlayHintsProvider
import ru.hollowhorizon.hollowengine.client.ui.UiSyntaxHighlighter
import ru.hollowhorizon.hollowengine.client.ui.UiTextDiagnostic
import ru.hollowhorizon.hollowengine.client.ui.UiTextFieldMode
import ru.hollowhorizon.hollowengine.client.ui.percent

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
        diagnostics = diagnostics,
        inlayHints = inlayHints,
        inlayHintsProvider = inlayHintsProvider,
        inlayRevision = inlayRevision,
        onChange = onChange,
        id = id,
        tags = listOf("code-editor") + tags,
        modifier = Modifier.then(
            modifier,
            Modifier.input(scrollable = true),
            Modifier.textWrap(false),
        ),
        attributes = attributes,
    )
}
