package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorLanguageService
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiCompletionContext
import ru.hollowhorizon.hollowengine.client.ui.UiCompletionContributor
import ru.hollowhorizon.hollowengine.client.ui.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.UiSyntaxHighlighter
import ru.hollowhorizon.hollowengine.client.ui.UiTextCompletion
import ru.hollowhorizon.hollowengine.client.ui.UiTextDiagnostic
import ru.hollowhorizon.hollowengine.client.ui.UiTextDiagnosticSeverity
import ru.hollowhorizon.hollowengine.client.ui.UiTextHighlight
import ru.hollowhorizon.hollowengine.client.ui.withBold
import ru.hollowhorizon.hollowengine.client.ui.withColor
import ru.hollowhorizon.hollowengine.client.ui.withItalic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.UnavailableKotlinScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType

internal class HollowIdeSyntaxHighlighter(
    private val path: String,
) : UiSyntaxHighlighter {
    private val analyzer = analyzerFor(path)

    override fun highlight(text: String): List<UiTextHighlight> {
        val lineStarts = lineStarts(text)
        return analyzer.highlight(path, text, 0).flatMapIndexed { lineIndex, line ->
            val lineStart = lineStarts.getOrElse(lineIndex) { text.length }
            var cursor = lineStart
            line.spans.mapNotNull { (segment, style) ->
                val start = cursor
                val end = (start + segment.length).coerceAtMost(text.length)
                cursor = end
                if (start == end) null else UiTextHighlight(start, end, style.toUi())
            }
        }
    }
}

internal class HollowIdeCompletionContributor(
    private val path: String,
) : UiCompletionContributor {
    private val analyzer = analyzerFor(path)

    override fun complete(context: UiCompletionContext): List<UiTextCompletion> {
        return analyzer.completions(path, context.text, context.caret).map { item ->
            UiTextCompletion(
                label = item.show,
                insertText = item.insert,
                detail = item.tag.text,
                caretOffset = (item.insert.length + item.moveCaret).coerceIn(0, item.insert.length),
            )
        }
    }
}

internal fun diagnosticsFor(path: String, text: String): List<UiTextDiagnostic> {
    val lineStarts = lineStarts(text)
    return analyzerFor(path).diagnostic(path, text).map { diagnostic ->
        diagnostic.toUi(text, lineStarts)
    }
}

private fun analyzerFor(path: String) = runCatching {
    EditorLanguageService(path.substringAfterLast('.', "")).analyzer
}.getOrElse {
    UnavailableKotlinScriptingAnalyzer
}

private fun Diagnostic.toUi(text: String, lineStarts: List<Int>): UiTextDiagnostic {
    val start = offsetAt(lineStarts, range.start.line, range.start.column, text.length)
    val end = offsetAt(lineStarts, range.end.line, range.end.column, text.length).coerceAtLeast(start)
    return UiTextDiagnostic(
        start = start,
        end = end,
        message = message,
        severity = severity.toUi(),
    )
}

private fun Severity.toUi(): UiTextDiagnosticSeverity {
    return when (this) {
        Severity.ERROR,
        Severity.FATAL -> UiTextDiagnosticSeverity.ERROR

        Severity.WARNING -> UiTextDiagnosticSeverity.WARNING
        Severity.DEBUG,
        Severity.INFO -> UiTextDiagnosticSeverity.INFO
    }
}

private fun SpanStyle.toUi(): UiInlineStyle {
    var style = UiInlineStyle().withColor(color.toUiColor())
    if (bold) style = style.withBold()
    if (italic) style = style.withItalic()
    return style
}

private fun TokenType.toUiColor(): UiColor {
    return when (this) {
        TokenType.COMMENT -> UiColor(0.55f, 0.6f, 0.68f, 1f)
        TokenType.KEYWORD -> UiColor(0.81f, 0.56f, 0.43f, 1f)
        TokenType.STRING -> UiColor(0.42f, 0.67f, 0.45f, 1f)
        TokenType.ANNOTATION -> UiColor(0.7f, 0.68f, 0.38f, 1f)
        TokenType.NUMERIC_LITERAL -> UiColor(0.16f, 0.68f, 0.72f, 1f)
        TokenType.PROPERTY_IDENTIFIER,
        TokenType.FIELD -> UiColor(0.78f, 0.49f, 0.73f, 1f)

        TokenType.VARIABLE,
        TokenType.PARAMETER,
        TokenType.NAME_REFERENCE,
        TokenType.CLASS,
        TokenType.INTERFACE,
        TokenType.ENUM,
        TokenType.OBJECT -> UiColor(0.66f, 0.72f, 0.78f, 1f)

        TokenType.EXTENSION_RECEIVER,
        TokenType.VALUE_ARGUMENT_NAME -> UiColor(0.34f, 0.66f, 0.97f, 1f)

        TokenType.TOP_LEVEL -> UiColor(0.95f, 0.96f, 0.95f, 1f)
        TokenType.FUNCTION,
        TokenType.METHOD -> UiColor(1f, 0.78f, 0.43f, 1f)

        TokenType.DEFAULT -> UiColor(0.84f, 0.87f, 0.92f, 1f)
    }
}

private fun lineStarts(text: String): List<Int> {
    val starts = mutableListOf(0)
    text.forEachIndexed { index, char ->
        if (char == '\n') starts += index + 1
    }
    return starts
}

private fun offsetAt(lineStarts: List<Int>, line: Int, column: Int, textLength: Int): Int {
    val lineStart = lineStarts.getOrElse(line.coerceAtLeast(0)) { textLength }
    return (lineStart + column.coerceAtLeast(0)).coerceIn(0, textLength)
}
