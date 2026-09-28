package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderExpression
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderExpressionError
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderInput
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderTokenKind
import ru.hollowhorizon.hollowengine.client.ui.widgets.*

/**
 * What a field needs to edit the text of an expression node: the token colors every expression field
 * of the editor uses, the functions and names it can complete, and what is wrong with the text.
 */
internal object ShaderExpressionEditing {
    val highlighter = UiSyntaxHighlighter { text ->
        ShaderExpression.parse(text).tokens.mapNotNull { token ->
            val color = when (token.kind) {
                ShaderTokenKind.NUMBER -> ExpressionTokenColors.Number
                ShaderTokenKind.VARIABLE -> ExpressionTokenColors.Name
                ShaderTokenKind.CONSTANT -> ExpressionTokenColors.Number
                ShaderTokenKind.BOOLEAN -> ExpressionTokenColors.Keyword
                ShaderTokenKind.FUNCTION -> ExpressionTokenColors.Keyword
                ShaderTokenKind.MEMBER -> ExpressionTokenColors.String
                ShaderTokenKind.OPERATOR -> ExpressionTokenColors.Operator
                ShaderTokenKind.END -> return@mapNotNull null
            }
            UiTextHighlight(token.start, token.end, UiInlineStyle().withColor(color))
        }
    }

    val completions = UiCompletionContributor { context ->
        val before = context.text.take(context.caret.coerceIn(0, context.text.length))
        val prefix = before.takeLastWhile { it.isLetterOrDigit() || it == '_' }
        if (before.dropLast(prefix.length).endsWith('.')) {
            return@UiCompletionContributor Swizzles.filter { it.startsWith(prefix) }
                .map { UiTextCompletion(label = it) }
        }
        val names = ShaderExpression.parse(context.text).variables.filter { it != prefix }
        val candidates = ShaderExpression.Functions.map { (name, parameters) ->
            UiTextCompletion(label = name, insertText = "$name()", detail = parameters, caretOffset = name.length + 1)
        } + (ShaderExpression.Constants.keys + ShaderExpression.Booleans).map {
            UiTextCompletion(
                label = it,
                tail = "const"
            )
        } + ShaderInput.entries.mapNotNull { input ->
            input.expressionName?.let {
                UiTextCompletion(
                    label = it,
                    tail = input.type.glsl
                )
            }
        } + names.map { UiTextCompletion(label = it, tail = "pin") }
        candidates.distinctBy { it.label }.filter { prefix.isEmpty() || it.label.startsWith(prefix, ignoreCase = true) }
    }

    fun diagnostics(text: String): List<UiTextDiagnostic> {
        val error = ShaderExpression.parse(text).error ?: return emptyList()
        if (text.isEmpty()) return emptyList()
        val start = error.start.coerceIn(0, text.length - 1)
        val end = error.end.coerceIn(start + 1, text.length)
        return listOf(UiTextDiagnostic(start, end, describe(error)))
    }

    fun describe(error: ShaderExpressionError): String =
        expressionProblemText(error.problem.name.lowercase(), error.detail)

    private val Swizzles = listOf("x", "y", "z", "w", "xy", "xyz", "rgb", "rgba", "a")
}

/** What a problem of an expression says, in words, with what it is about. */
internal fun expressionProblemText(reason: String, detail: String): String {
    val text = graphText("expression.$reason")
    return if (detail.isBlank()) text else "$text: $detail"
}
