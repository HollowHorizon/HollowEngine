package ru.hollowhorizon.hollowengine.common.scripting.source

/**
 * The part of the script that determines what it compiles into.
 */
object ScriptText {
    private const val BOM = "\uFEFF"

    /**
     * [text] with removed comments and all sequences of spaces merged into a single new line or a single space,
     * if the sequence fits on a single line.
     */
    fun normalize(text: String): String {
        val builder = StringBuilder(text.length)
        ScriptTextScanner(text.removePrefix(BOM), builder).run()
        return builder.toString()
    }
}

private enum class Gap { NONE, SPACE, LINE }

private enum class LiteralKind(val allowsTemplates: Boolean) {
    STRING(allowsTemplates = true), RAW_STRING(allowsTemplates = true), CHARACTER(allowsTemplates = false), IDENTIFIER(
        allowsTemplates = false
    );
}

private sealed interface ScanContext {
    class Code(val insideTemplate: Boolean) : ScanContext {
        var depth = 0
    }

    class Literal(val kind: LiteralKind) : ScanContext
}

/**
 * Removes spaces and characters that do not affect compilation, so that conditional line breaks
 * or modified comments do not require the installation of a 100 MB compiler
 */
private class ScriptTextScanner(private val text: String, private val out: StringBuilder) {
    private val contexts = ArrayDeque<ScanContext>().apply { addLast(ScanContext.Code(insideTemplate = false)) }
    private var index = 0
    private var gap = Gap.NONE

    fun run() {
        while (index < text.length) {
            when (val context = contexts.last()) {
                is ScanContext.Code -> code(context)
                is ScanContext.Literal -> literal(context)
            }
        }
    }

    private fun code(context: ScanContext.Code) {
        val char = text[index]
        when {
            char.isWhitespace() -> {
                gap = maxOf(gap, if (char == '\n' || char == '\r') Gap.LINE else Gap.SPACE)
                index++
            }

            char == '/' && peek(1) == '/' -> skipLineComment()
            char == '/' && peek(1) == '*' -> skipBlockComment()
            char == '"' -> openString()
            char == '\'' -> {
                emit(1)
                contexts.addLast(ScanContext.Literal(LiteralKind.CHARACTER))
            }

            char == '`' -> {
                emit(1)
                contexts.addLast(ScanContext.Literal(LiteralKind.IDENTIFIER))
            }

            !context.insideTemplate -> emit(1)
            char == '{' -> {
                context.depth++
                emit(1)
            }

            char == '}' && context.depth == 0 -> {
                emit(1)
                contexts.removeLast()
            }

            char == '}' -> {
                context.depth--
                emit(1)
            }

            else -> emit(1)
        }
    }

    private fun literal(context: ScanContext.Literal) {
        val char = text[index]
        when {
            char == '$' && peek(1) == '{' && context.kind.allowsTemplates -> {
                emit(2)
                contexts.addLast(ScanContext.Code(insideTemplate = true))
            }

            context.kind == LiteralKind.RAW_STRING -> rawString()
            char == '\\' && context.kind != LiteralKind.IDENTIFIER -> emit(if (index + 1 < text.length) 2 else 1)
            char == '"' && context.kind == LiteralKind.STRING -> {
                emit(1)
                contexts.removeLast()
            }

            char == '\'' && context.kind == LiteralKind.CHARACTER -> {
                emit(1)
                contexts.removeLast()
            }

            char == '`' && context.kind == LiteralKind.IDENTIFIER -> {
                emit(1)
                contexts.removeLast()
            }

            char == '\n' || char == '\r' -> contexts.removeLast()
            else -> emit(1)
        }
    }

    private fun openString() {
        if (text.startsWith(TRIPLE_QUOTE, index)) {
            emit(TRIPLE_QUOTE.length)
            contexts.addLast(ScanContext.Literal(LiteralKind.RAW_STRING))
        } else {
            emit(1)
            contexts.addLast(ScanContext.Literal(LiteralKind.STRING))
        }
    }

    private fun rawString() {
        if (text[index] != '"') {
            emit(1)
            return
        }
        var length = 0
        while (index + length < text.length && text[index + length] == '"') length++
        emit(length)
        if (length >= TRIPLE_QUOTE.length) contexts.removeLast()
    }

    private fun skipLineComment() {
        while (index < text.length && text[index] != '\n' && text[index] != '\r') index++
        gap = maxOf(gap, Gap.SPACE)
    }

    private fun skipBlockComment() {
        var depth = 0
        var multiline = false
        while (index < text.length) {
            when {
                text.startsWith("/*", index) -> {
                    depth++
                    index += 2
                }

                text.startsWith("*/", index) -> {
                    depth--
                    index += 2
                    if (depth == 0) break
                }

                else -> {
                    if (text[index] == '\n' || text[index] == '\r') multiline = true
                    index++
                }
            }
        }
        gap = maxOf(gap, if (multiline) Gap.LINE else Gap.SPACE)
    }

    private fun emit(length: Int) {
        if (out.isNotEmpty()) {
            when (gap) {
                Gap.NONE -> Unit
                Gap.SPACE -> out.append(' ')
                Gap.LINE -> out.append('\n')
            }
        }
        gap = Gap.NONE
        out.append(text, index, index + length)
        index += length
    }

    private fun peek(offset: Int): Char? = text.getOrNull(index + offset)

    private companion object {
        const val TRIPLE_QUOTE = "\"\"\""
    }
}
