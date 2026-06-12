package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.Composable
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*

@Composable
internal fun HollowUiEditorDemo(
    keyLog: String,
    onKeyLog: (String) -> Unit,
    highlighter: UiSyntaxHighlighter,
) {
    Box(tags = listOf("editor-demo-stage"), modifier = Modifier.input(scrollable = true)) {
        Column(tags = listOf("editor-demo-card"), modifier = Modifier.position(0.px, 0.px)) {
            Text("TextField editor mode", tags = listOf("card-title"))
            TextField(
                value = EditorDemoText,
                mode = UiTextFieldMode.MULTI_LINE,
                multiCaret = true,
                syntaxHighlighter = highlighter,
                placeholder = "Type code here",
                tags = listOf("editor-text-field"),
                modifier = Modifier.then(
                    Modifier.size(520.px, 210.px),
                    Modifier.input(scrollable = true),
                    Modifier.textWrap(false),
                    Modifier.onKeyInput { input ->
                        if (input.key != GLFW.GLFW_KEY_F2) return@onKeyInput false
                        onKeyLog("F2 captured before the default text-field keymap")
                        true
                    },
                ),
            )
            Text(keyLog, tags = listOf("editor-key-log"))
        }

        Column(tags = listOf("editor-demo-card", "lazy-column-card"), modifier = Modifier.position(552.px, 0.px)) {
            Text("LazyColumn", tags = listOf("card-title"))
            LazyColumn(tags = listOf("lazy-column-demo"), modifier = Modifier.input(scrollable = true)) {
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
            LazyRow(tags = listOf("lazy-row-demo"), modifier = Modifier.input(scrollable = true)) {
                repeat(64) { index ->
                    Column(tags = listOf("lazy-row-tile")) {
                        Text("#${index + 1}", tags = listOf("lazy-row-tile-title"))
                        Text("tile", tags = listOf("body"))
                    }
                }
            }
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

private val EditorDemoText = """
fun editorDemo() {
    val text = "Hollow UI editor"
    // Shift + Up/Down extends the selection.
    // Alt + click adds another caret when multiCaret is enabled.
    repeat(3) { index ->
        TextField(value = text + index)
    }
}
""".trimIndent()
