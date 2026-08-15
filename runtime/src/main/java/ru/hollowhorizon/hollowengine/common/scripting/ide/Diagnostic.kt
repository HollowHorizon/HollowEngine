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

data class Diagnostic(val range: Range, val severity: Severity, val message: String) {
    override fun toString(): String {
        return "[$severity] ${range.start.line}:${range.start.column}: $message"
    }
}

class ScriptCompilationException(name: String, val reports: List<Diagnostic>): RuntimeException("Script '$name' compilation failed!\nReports:${reports.joinToString("\n\t", "\n\t")}")
class ScriptEvaluationException(name: String, val reports: List<Diagnostic>): RuntimeException("Script '$name' evaluation failed!\nReports:${reports.joinToString("\n\t", "\n\t")}")
