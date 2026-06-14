package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.TextField
import ru.hollowhorizon.hollowengine.client.ui.UiCompletionContributor
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
    inlayHints: UiInlayHintsProvider? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier = Modifier.size(100.percent, 100.percent),
) {
    TextField(
        value = value,
        mode = UiTextFieldMode.MULTI_LINE,
        multiCaret = true,
        syntaxHighlighter = highlighter,
        completionContributor = completions,
        diagnostics = diagnostics,
        inlayHintsProvider = inlayHints,
        onChange = onChange,
        id = id,
        tags = listOf("code-editor") + tags,
        modifier = Modifier.then(
            modifier,
            Modifier.input(scrollable = true),
            Modifier.textWrap(false),
        ),
    )
}
