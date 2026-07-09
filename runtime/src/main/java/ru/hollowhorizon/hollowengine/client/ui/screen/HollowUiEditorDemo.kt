package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.*
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.*

@Composable
internal fun HollowUiEditorDemo(
    keyLog: String,
    onKeyLog: (String) -> Unit,
    highlighter: UiSyntaxHighlighter,
) {
    Box(tags = listOf("editor-demo-stage"), modifier = Modifier.scroll(vertical = true, horizontal = true)) {
        Column(tags = listOf("editor-demo-card"), modifier = Modifier.position(0.px, 0.px)) {
            Text("TextField editor mode", tags = listOf("card-title"))
            TextField(
                value = EditorDemoText,
                mode = UiTextFieldMode.MULTI_LINE,
                multiCaret = true,
                syntaxHighlighter = highlighter,
                completionContributor = EditorDemoCompletionContributor,
                diagnostics = editorDemoDiagnostics(EditorDemoText),
                inlayHintsProvider = EditorDemoInlayHintsProvider,
                placeholder = "Type code here",
                tags = listOf("editor-text-field"),
                modifier = Modifier.size(520.px, 210.px)
                    .scroll(vertical = true, horizontal = true)
                    .textWrap(false)
                    .onKeyInput { input ->
                        if (input.key != GLFW.GLFW_KEY_F2) return@onKeyInput
                        onKeyLog("F2 captured before the default text-field keymap")
                        input.consume()
                    }
            )
            Text(keyLog, tags = listOf("editor-key-log"))
        }

        Column(tags = listOf("editor-demo-card", "lazy-column-card"), modifier = Modifier.position(552.px, 0.px)) {
            Text("LazyColumn", tags = listOf("card-title"))
            LazyColumn(
                tags = listOf("lazy-column-demo"),
                modifier = Modifier.scroll(vertical = true, horizontal = true)
            ) {
                repeat(120) { index ->
                    Row(tags = listOf("lazy-list-row")) {
                        Text((index + 1).toString().padStart(3, '0'), tags = listOf("lazy-row-index"))
                        Text("Virtualized row ${index + 1}", tags = listOf("body"))
                    }
                }
            }
        }

        Column(tags = listOf("editor-demo-card", "lazy-row-card"), modifier = Modifier.position(0.px, 282.px)) {
            Text("LazyRow", tags = listOf("card-title"))
            LazyRow(tags = listOf("lazy-row-demo"), modifier = Modifier.scroll(vertical = true, horizontal = true)) {
                repeat(64) { index ->
                    Column(tags = listOf("lazy-row-tile")) {
                        Text("#${index + 1}", tags = listOf("lazy-row-tile-title"))
                        Text("tile", tags = listOf("body"))
                    }
                }
            }
        }

        Column(tags = listOf("editor-demo-card"), modifier = Modifier.position(552.px, 282.px)) {
            Text("EditableTextField (new)", tags = listOf("card-title"))
            val editableState = remember {
                TextFieldState(
                    initialText = EditorDemoText,
                    multiline = true,
                    indentSize = 4,
                    autoPairs = true,
                    multiCaret = true,
                )
            }
            Text(
                if (editableState.wrap) "wrap: on (click to toggle)" else "wrap: off (click to toggle)",
                tags = listOf("body"),
                modifier = Modifier.onClick { editableState.wrap = !editableState.wrap },
            )
            EditableTextField(
                state = editableState,
                tags = listOf("editable-text-field"),
                modifier = Modifier.size(500.px, 180.px)
                    .background(UiColor(0.1f, 0.11f, 0.13f, 1f))
                    .padding(6.px),
            )
        }
    }
}

internal fun highlightEditorDemoText(text: String): List<UiTextHighlight> {
    val highlights = mutableListOf<UiTextHighlight>()
    val keywordStyle = UiInlineStyle().withColor(UiColor(0.72f, 0.58f, 1f, 1f))
    val stringStyle = UiInlineStyle().withColor(UiColor(0.76f, 0.92f, 0.62f, 1f))
    val commentStyle = UiInlineStyle().withColor(UiColor(0.48f, 0.56f, 0.66f, 1f))
    val numberStyle = UiInlineStyle().withColor(UiColor(0.56f, 0.78f, 1f, 1f))

    Regex("""//[^\n]*""").findAll(text).forEach { match ->
        highlights += UiTextHighlight(match.range.first, match.range.last + 1, commentStyle)
    }
    Regex(""""(?:\\.|[^"\\])*"""").findAll(text).forEach { match ->
        highlights += UiTextHighlight(match.range.first, match.range.last + 1, stringStyle)
    }
    Regex("""\b(fun|val|var|if|else|return|repeat|TextField|LazyColumn|LazyRow)\b""").findAll(text).forEach { match ->
        highlights += UiTextHighlight(match.range.first, match.range.last + 1, keywordStyle)
    }
    Regex("""\b\d+\b""").findAll(text).forEach { match ->
        highlights += UiTextHighlight(match.range.first, match.range.last + 1, numberStyle)
    }
    return highlights
}

private object EditorDemoCompletionContributor : UiCompletionContributor {
    override fun complete(context: UiCompletionContext): List<UiTextCompletion> {
        return listOf(
            UiTextCompletion(
                "TextField(...)",
                "TextField(value = \"\")",
                "template",
                caretOffset = "TextField(value = \"".length
            ),
            UiTextCompletion(
                "LazyColumn { ... }",
                "LazyColumn {\n    \n}",
                "template",
                caretOffset = "LazyColumn {\n    ".length
            ),
            UiTextCompletion(
                "Modifier.onKeyInput",
                "Modifier.onKeyInput { input ->\n    false\n}",
                "modifier",
                caretOffset = "Modifier.onKeyInput { input ->\n    ".length
            ),
            UiTextCompletion("line-numbers: true", "line-numbers: true;", "style"),
        )
    }
}

private object EditorDemoInlayHintsProvider : UiInlayHintsProvider {
    override fun hints(text: String): List<UiInlayHint> = editorDemoInlayHints(text)
}

private fun editorDemoDiagnostics(text: String): List<UiTextDiagnostic> {
    val repeatIndex = text.indexOf("repeat")
    val textIndex = text.indexOf("val text")
    return listOfNotNull(
        repeatIndex.takeIf { it >= 0 }?.let {
            UiTextDiagnostic(it, it + "repeat".length, "Demo warning", UiTextDiagnosticSeverity.WARNING)
        },
        textIndex.takeIf { it >= 0 }?.let {
            UiTextDiagnostic(it, it + "val text".length, "Demo info", UiTextDiagnosticSeverity.INFO)
        },
    )
}

private fun editorDemoInlayHints(text: String): List<UiInlayHint> {
    val functionEnd = text.indexOf("()").takeIf { it >= 0 }?.let { it + 2 }
    val repeatEnd = text.indexOf("repeat(3)").takeIf { it >= 0 }?.let { it + "repeat(3)".length }
    return listOfNotNull(
        functionEnd?.let { UiInlayHint(it, ": Unit") },
        repeatEnd?.let { UiInlayHint(it, " times") },
    )
}

private val EditorDemoText = """
fun editorDemo() {
    val text = "Hollow UI editor"
    // Shift + Up/Down extends the selection.
    // Alt + click adds another caret when multiCaret is enabled.
    // Double click selects a word; Alt + double click adds one more word selection.
    // Type "." or press Alt+Enter to open completion templates.
    repeat(3) { index ->
        TextField(value = text + index)
    }
}
""".trimIndent()
