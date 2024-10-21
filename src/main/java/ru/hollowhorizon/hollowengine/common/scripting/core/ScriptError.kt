package ru.hollowhorizon.hollowengine.common.scripting.core

data class ScriptError(
    val severity: Severity,
    val message: String,
    val source: String,
    val line: Int,
    val column: Int,
    val exception: Throwable?,
) {

    enum class Severity { DEBUG, INFO, WARNING, ERROR, FATAL }

    override fun toString() = "$message at $source:$line:$column"
}

enum class ErrorType {
    COMPILATION_ERROR, RUNTIME_ERROR
}