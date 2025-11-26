package ru.hollowhorizon.hollowengine.common.scripting.ide

data class Position(val line: Int, val column: Int)
data class Range(val start: Position, val end: Position)
enum class Severity {
    DEBUG, INFO, WARNING, ERROR, FATAL;

    fun isError(): Boolean = this == ERROR || this == FATAL
}

data class Diagnostic(val range: Range, val severity: Severity, val message: String)

class ScriptCompilationException(name: String, val reports: List<Diagnostic>): RuntimeException("Script '$name' compilation failed!\nReports:${reports.joinToString("\n\t", "\n\t")}")
class ScriptEvaluationException(name: String, val reports: List<Diagnostic>): RuntimeException("Script '$name' evaluation failed!\nReports:${reports.joinToString("\n\t", "\n\t")}")
