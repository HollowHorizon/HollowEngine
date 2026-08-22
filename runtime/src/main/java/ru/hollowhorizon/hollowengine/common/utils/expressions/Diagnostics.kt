package ru.hollowhorizon.hollowengine.common.utils.expressions

/** A half-open range in the expression source, used to point an error in text. */
data class Span(val start: Int, val end: Int) {
    operator fun plus(other: Span): Span = Span(minOf(start, other.start), maxOf(end, other.end))

    companion object {
        val NONE = Span(0, 0)
    }
}

enum class Severity { WARNING, ERROR }

data class Diagnostic(val severity: Severity, val message: String, val span: Span) {
    override fun toString(): String = "$message (at ${span.start}..${span.end})"
}

class Diagnostics {
    private val entries = mutableListOf<Diagnostic>()

    val all: List<Diagnostic> get() = entries
    val errors: List<Diagnostic> get() = entries.filter { it.severity == Severity.ERROR }
    val hasErrors: Boolean get() = entries.any { it.severity == Severity.ERROR }

    fun warn(message: String, span: Span) {
        entries += Diagnostic(Severity.WARNING, message, span)
    }

    fun error(message: String, span: Span) {
        entries += Diagnostic(Severity.ERROR, message, span)
    }

    fun report(diagnostic: Diagnostic) {
        entries += diagnostic
    }
}

class ExpressionException(val diagnostics: List<Diagnostic>) : RuntimeException(
    diagnostics.joinToString("\n") { it.toString() }
)
