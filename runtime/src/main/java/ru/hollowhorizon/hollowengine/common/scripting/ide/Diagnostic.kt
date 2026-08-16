package ru.hollowhorizon.hollowengine.common.scripting.ide

data class Position(val line: Int, val column: Int) {
    companion object {
        fun at(text: String, offset: Int): Position {
            var line = 0
            var column = 0
            for (index in 0 until offset.coerceIn(0, text.length)) {
                if (text[index] == '\n') {
                    line++
                    column = 0
                } else {
                    column++
                }
            }
            return Position(line, column)
        }
    }
}
data class Range(val start: Position, val end: Position)
enum class Severity {
    DEBUG, INFO, WARNING, ERROR, FATAL;

    fun isError(): Boolean = this == ERROR || this == FATAL
}

/**
 * One replacement inside the analysed document, in absolute end-exclusive offsets. A fix is a set
 * of these, so "remove every unused import" is one entry rather than one per line.
 */
data class TextEdit(val start: Int, val end: Int, val replacement: String)

/**
 * An action offered on a diagnostic; the editor shows [title] and applies [edits] as one undo step.
 *
 * [title] is a translation key, formatted with [titleArgs]; an untranslated string falls through
 * unchanged, so an analyzer that has nothing to translate can still name its fix in plain text.
 */
data class DiagnosticFix(
    val title: String,
    val edits: List<TextEdit>,
    val titleArgs: List<String> = emptyList(),
)

data class Diagnostic(
    val range: Range,
    val severity: Severity,
    val message: String,
    val fixes: List<DiagnosticFix> = emptyList(),
) {
    override fun toString(): String {
        return "[$severity] ${range.start.line}:${range.start.column}: $message"
    }
}

class ScriptCompilationException(name: String, val reports: List<Diagnostic>): RuntimeException("Script '$name' compilation failed!\nReports:${reports.joinToString("\n\t", "\n\t")}")
class ScriptEvaluationException(name: String, val reports: List<Diagnostic>): RuntimeException("Script '$name' evaluation failed!\nReports:${reports.joinToString("\n\t", "\n\t")}")
