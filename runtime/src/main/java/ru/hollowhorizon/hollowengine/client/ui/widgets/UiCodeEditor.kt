package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.DefaultUiFontSize
import ru.hollowhorizon.hollowengine.client.ui.text.Shadow

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
    onInlayAction: ((UiInlayAction) -> Unit)? = null,
    completionRevision: Long = inlayRevision,
    readOnly: Boolean = false,
    fontSize: Float = DefaultUiFontSize,
    fontFamily: String? = CodeEditorFontFamily,
    textShadow: Shadow? = CodeEditorTextShadow,
    state: TextFieldState? = null,
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
        onInlayAction = onInlayAction,
        completionRevision = completionRevision,
        wrap = false,
        fontSize = fontSize,
        fontFamily = fontFamily,
        textShadow = textShadow,
        caretColor = CodeEditorCaretColor,
        selectionColor = CodeEditorSelectionColor,
        lineNumbers = true,
        lineNumberColor = CodeEditorLineNumberColor,
        indentGuides = true,
        indentGuideColor = CodeEditorIndentGuideColor,
        onChange = onChange,
        state = state,
        id = id,
        tags = listOf("code-editor") + tags,
        modifier = modifier,
        attributes = attributes,
    )
}

private const val CodeEditorFontFamily = "hollowengine:fonts/monocraft"

private val CodeEditorTextShadow = Shadow(offsetX = 0f, offsetY = 1f, blur = 2f, color = UiColor(1f, 1f, 1f, 0.4f))
private val CodeEditorCaretColor = UiColor(0.941f, 0.965f, 1f, 1f)
private val CodeEditorSelectionColor = UiColor(0.373f, 0.549f, 0.804f, 0.46f)
private val CodeEditorLineNumberColor = UiColor(0.333f, 0.376f, 0.439f, 1f)
private val CodeEditorIndentGuideColor = UiColor(0.408f, 0.459f, 0.533f, 0.22f)
