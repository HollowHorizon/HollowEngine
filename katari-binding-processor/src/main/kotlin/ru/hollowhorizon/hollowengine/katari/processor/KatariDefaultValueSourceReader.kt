package ru.hollowhorizon.hollowengine.katari.processor

import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSValueParameter
import java.io.File

internal class KatariDefaultValueSourceReader {
    private val sources = linkedMapOf<String, String>()

    fun defaultValueExpression(parameter: KSValueParameter): String? {
        if (!parameter.hasDefault) return null
        val location = parameter.location as? FileLocation ?: return null
        val source = sources.getOrPut(location.filePath) { File(location.filePath).readText() }
        val lineStart = source.offsetAt(location.lineNumber, 1) ?: return null
        val start = parameter.name?.asString()
            ?.let { source.indexOfParameterName(it, lineStart) }
            ?.takeIf { it >= 0 }
            ?: lineStart
        val parameterSource = source.substring(start)
        val defaultStart = parameterSource.indexOfTopLevel('=')
        if (defaultStart < 0) return null
        val expressionStart = defaultStart + 1
        val expressionEnd = parameterSource.endOfDefaultExpression(expressionStart)
        return parameterSource.substring(expressionStart, expressionEnd).trim().takeIf { it.isNotEmpty() }
    }

    private fun String.offsetAt(lineNumber: Int, columnNumber: Int): Int? {
        var line = 1
        var column = 1
        forEachIndexed { index, char ->
            if (line == lineNumber && column == columnNumber) return index
            if (char == '\n') {
                line++
                column = 1
            } else {
                column++
            }
        }
        return length.takeIf { line == lineNumber && column == columnNumber }
    }

    private fun String.indexOfParameterName(name: String, start: Int): Int {
        val pattern = Regex("""\b${Regex.escape(name)}\b\s*:""")
        val match = pattern.find(this, start) ?: return -1
        return match.range.first
    }

    private fun String.indexOfTopLevel(target: Char): Int {
        var state = ScanState()
        forEachIndexed { index, char ->
            if (state.isTopLevel && char == target) return index
            if (state.isTopLevel && (char == ',' || char == ')')) return -1
            state = state.next(char, getOrNull(index - 1), getOrNull(index + 1), getOrNull(index + 2))
        }
        return -1
    }

    private fun String.endOfDefaultExpression(start: Int): Int {
        var state = ScanState()
        for (index in start until length) {
            val char = this[index]
            if (state.isTopLevel && (char == ',' || char == ')')) return index
            state = state.next(char, getOrNull(index - 1), getOrNull(index + 1), getOrNull(index + 2))
        }
        return length
    }

    private data class ScanState(
        val roundDepth: Int = 0,
        val squareDepth: Int = 0,
        val curlyDepth: Int = 0,
        val inSingleQuotedString: Boolean = false,
        val inDoubleQuotedString: Boolean = false,
        val inTripleQuotedString: Boolean = false,
    ) {
        val isTopLevel: Boolean
            get() = roundDepth == 0 &&
                    squareDepth == 0 &&
                    curlyDepth == 0 &&
                    !inSingleQuotedString &&
                    !inDoubleQuotedString &&
                    !inTripleQuotedString

        fun next(char: Char, previous: Char?, next: Char?, nextNext: Char?): ScanState {
            if (inTripleQuotedString) return tripleQuotedNext(char, next, nextNext)
            if (inSingleQuotedString) return copy(inSingleQuotedString = char != '\'' || previous == '\\')
            if (inDoubleQuotedString) return copy(inDoubleQuotedString = char != '"' || previous == '\\')

            return when (char) {
                '\'' -> copy(inSingleQuotedString = true)
                '"' -> if (next == '"' && nextNext == '"') {
                    copy(inTripleQuotedString = true)
                } else {
                    copy(inDoubleQuotedString = true)
                }
                '(' -> copy(roundDepth = roundDepth + 1)
                ')' -> copy(roundDepth = (roundDepth - 1).coerceAtLeast(0))
                '[' -> copy(squareDepth = squareDepth + 1)
                ']' -> copy(squareDepth = (squareDepth - 1).coerceAtLeast(0))
                '{' -> copy(curlyDepth = curlyDepth + 1)
                '}' -> copy(curlyDepth = (curlyDepth - 1).coerceAtLeast(0))
                else -> this
            }
        }

        private fun tripleQuotedNext(char: Char, next: Char?, nextNext: Char?): ScanState {
            return if (char == '"' && next == '"' && nextNext == '"') copy(inTripleQuotedString = false) else this
        }
    }
}
