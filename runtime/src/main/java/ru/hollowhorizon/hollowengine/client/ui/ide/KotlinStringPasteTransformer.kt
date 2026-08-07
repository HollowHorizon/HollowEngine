package ru.hollowhorizon.hollowengine.client.ui.ide

import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextCaret
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextPasteTransformer

internal object KotlinStringPasteTransformer : UiTextPasteTransformer {
    override fun transform(document: String, caret: UiTextCaret, pastedText: String): String {
        return when (document.kotlinStringContextAt(caret.position)) {
            KotlinStringContext.REGULAR -> pastedText.escapeForRegularKotlinString()
            KotlinStringContext.RAW -> pastedText.escapeForRawKotlinString()
            null -> pastedText
        }
    }
}

private fun String.escapeForRegularKotlinString(): String = buildString(length) {
    for (char in this@escapeForRegularKotlinString) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> {
                append('\\')
                append('$')
            }

            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (char.isUnsafeSourceControl()) appendUnicodeEscape(char) else append(char)
        }
    }
}

private fun String.escapeForRawKotlinString(): String = buildString(length) {
    var index = 0
    while (index < this@escapeForRawKotlinString.length) {
        val char = this@escapeForRawKotlinString[index]
        when {
            char == '$' -> appendRawCharacterExpression('$')
            char == '"' -> {
                var quoteEnd = index + 1
                while (this@escapeForRawKotlinString.getOrNull(quoteEnd) == '"') quoteEnd++
                var remaining = quoteEnd - index
                while (remaining >= 3) {
                    appendRawCharacterExpression('"')
                    remaining--
                }
                repeat(remaining) { append('"') }
                index = quoteEnd - 1
            }

            char.isUnsafeSourceControl() && char != '\n' && char != '\t' -> appendRawUnicodeExpression(char)
            else -> append(char)
        }
        index++
    }
}

private fun Char.isUnsafeSourceControl(): Boolean = code < 0x20 || code == 0x7F || this == '\u2028' || this == '\u2029'

private fun StringBuilder.appendUnicodeEscape(char: Char) {
    append("\\u")
    append(char.code.toString(16).padStart(4, '0'))
}

private fun StringBuilder.appendRawCharacterExpression(char: Char) {
    append('$')
    append('{')
    append('\'')
    append(char)
    append('\'')
    append('}')
}

private fun StringBuilder.appendRawUnicodeExpression(char: Char) {
    append('$')
    append("{'\\u")
    append(char.code.toString(16).padStart(4, '0'))
    append("'}")
}

private fun String.kotlinStringContextAt(offset: Int): KotlinStringContext? {
    val limit = offset.coerceIn(0, length)
    var index = 0
    var mode = KotlinLexerMode.CODE
    var blockCommentDepth = 0
    var simpleTemplateParent: KotlinLexerMode? = null
    val templates = ArrayDeque<KotlinTemplateContext>()

    while (index < limit) {
        when (mode) {
            KotlinLexerMode.CODE -> when {
                startsWith("//", index) -> {
                    mode = KotlinLexerMode.LINE_COMMENT
                    index += 2
                }

                startsWith("/*", index) -> {
                    mode = KotlinLexerMode.BLOCK_COMMENT
                    blockCommentDepth = 1
                    index += 2
                }

                startsWith("\"\"\"", index) -> {
                    mode = KotlinLexerMode.RAW_STRING
                    index += 3
                }

                this[index] == '"' -> {
                    mode = KotlinLexerMode.REGULAR_STRING
                    index++
                }

                this[index] == '\'' -> {
                    mode = KotlinLexerMode.CHARACTER
                    index++
                }

                this[index] == '{' && templates.isNotEmpty() -> {
                    templates.last().braceDepth++
                    index++
                }

                this[index] == '}' && templates.isNotEmpty() -> {
                    val template = templates.last()
                    template.braceDepth--
                    index++
                    if (template.braceDepth == 0) {
                        mode = template.parent
                        templates.removeLast()
                    }
                }

                else -> index++
            }

            KotlinLexerMode.REGULAR_STRING -> when {
                this[index] == '\\' -> index = (index + 2).coerceAtMost(limit)
                this[index] == '"' -> {
                    mode = KotlinLexerMode.CODE
                    index++
                }

                this[index] == '\n' || this[index] == '\r' -> {
                    mode = KotlinLexerMode.CODE
                    index++
                }

                this[index] == '$' && getOrNull(index + 1) == '{' -> {
                    templates.addLast(KotlinTemplateContext(mode))
                    mode = KotlinLexerMode.CODE
                    index += 2
                }

                this[index] == '$' && getOrNull(index + 1)?.isKotlinIdentifierStart() == true -> {
                    simpleTemplateParent = mode
                    mode = KotlinLexerMode.SIMPLE_TEMPLATE
                    index++
                }

                else -> index++
            }

            KotlinLexerMode.RAW_STRING -> when {
                startsWith("\"\"\"", index) -> {
                    mode = KotlinLexerMode.CODE
                    index += 3
                }

                this[index] == '$' && getOrNull(index + 1) == '{' -> {
                    templates.addLast(KotlinTemplateContext(mode))
                    mode = KotlinLexerMode.CODE
                    index += 2
                }

                this[index] == '$' && getOrNull(index + 1)?.isKotlinIdentifierStart() == true -> {
                    simpleTemplateParent = mode
                    mode = KotlinLexerMode.SIMPLE_TEMPLATE
                    index++
                }

                else -> index++
            }

            KotlinLexerMode.CHARACTER -> when {
                this[index] == '\\' -> index = (index + 2).coerceAtMost(limit)
                this[index] == '\'' || this[index] == '\n' || this[index] == '\r' -> {
                    mode = KotlinLexerMode.CODE
                    index++
                }

                else -> index++
            }

            KotlinLexerMode.LINE_COMMENT -> {
                if (this[index] == '\n' || this[index] == '\r') mode = KotlinLexerMode.CODE
                index++
            }

            KotlinLexerMode.BLOCK_COMMENT -> when {
                startsWith("/*", index) -> {
                    blockCommentDepth++
                    index += 2
                }

                startsWith("*/", index) -> {
                    blockCommentDepth--
                    index += 2
                    if (blockCommentDepth == 0) mode = KotlinLexerMode.CODE
                }

                else -> index++
            }

            KotlinLexerMode.SIMPLE_TEMPLATE -> {
                if (this[index].isKotlinIdentifierPart()) {
                    index++
                } else {
                    mode = requireNotNull(simpleTemplateParent)
                    simpleTemplateParent = null
                }
            }
        }
    }

    if (mode == KotlinLexerMode.SIMPLE_TEMPLATE && getOrNull(limit)?.isKotlinIdentifierPart() != true) {
        mode = requireNotNull(simpleTemplateParent)
    }
    return when (mode) {
        KotlinLexerMode.REGULAR_STRING -> KotlinStringContext.REGULAR
        KotlinLexerMode.RAW_STRING -> KotlinStringContext.RAW
        else -> null
    }
}

private fun Char.isKotlinIdentifierStart(): Boolean = Character.isJavaIdentifierStart(this)

private fun Char.isKotlinIdentifierPart(): Boolean = Character.isJavaIdentifierPart(this)

private enum class KotlinStringContext {
    REGULAR,
    RAW,
}

private enum class KotlinLexerMode {
    CODE,
    REGULAR_STRING,
    RAW_STRING,
    CHARACTER,
    LINE_COMMENT,
    BLOCK_COMMENT,
    SIMPLE_TEMPLATE,
}

private data class KotlinTemplateContext(
    val parent: KotlinLexerMode,
    var braceDepth: Int = 1,
)

internal fun String.isKotlinSource(): Boolean =
    endsWith(".kt", ignoreCase = true) || endsWith(".kts", ignoreCase = true)
