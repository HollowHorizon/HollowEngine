package ru.hollowhorizon.hollowengine.common.scripting.ide

data class Position(val line: Int, val column: Int)
data class Range(val start: Position, val end: Position)
enum class Severity {
    DEBUG, INFO, WARNING, ERROR, FATAL;

    fun isError(): Boolean = this == ERROR || this == FATAL
}

data class Diagnostic(val range: Range, val severity: Severity, val message: String)