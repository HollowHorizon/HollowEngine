package ru.hollowhorizon.hollowengine.common.dialogue.lang

/**
 * What the caret is sitting on, worked out by scanning the line-up to it.
 */
sealed interface StoryCompletionContext {
    /** The part of the word already typed, which the offered items must start with. */
    val typed: String

    /** Plain dialogue text, or the start of a line: nothing to offer. */
    data object None : StoryCompletionContext {
        override val typed: String get() = ""
    }

    /** The command name itself. */
    data class Command(override val typed: String) : StoryCompletionContext

    /** Label of this file. */
    data class Label(override val typed: String) : StoryCompletionContext

    /**
     * An argument of [command]. [parameter] is set once `name=` has been typed; until then the word
     * could still become either a parameter name or the value at [positional], and the caller decides
     * what is worth offering.
     */
    data class Argument(
        val command: String,
        val parameter: String?,
        val positional: Int,
        override val typed: String,
    ) : StoryCompletionContext

    /** Inside an interpolation or an expression, where variables live. */
    data class Expression(override val typed: String) : StoryCompletionContext
}

/**
 * Reads [line] up to [caret] and reports what belongs there.
 */
fun storyCompletionContext(line: String, caret: Int): StoryCompletionContext {
    val end = caret.coerceIn(0, line.length)
    var i = 0

    while (i < end && (line[i] == ' ' || line[i] == '\t')) i++
    if (i >= end || line[i] != '@') {
        return if (hasOpenBrace(line, i, end)) StoryCompletionContext.Expression(wordBefore(line, end))
        else StoryCompletionContext.None
    }

    val nameStart = ++i
    while (i < end && StoryParser.isNameChar(line[i])) i++
    if (i >= end) return StoryCompletionContext.Command(line.substring(nameStart, end))
    val command = line.substring(nameStart, i)

    var parameter: String? = null
    var positional = 0
    var tokenStart = -1
    var typedFrom = i

    while (i < end) {
        when {
            line[i] == ' ' || line[i] == '\t' -> {
                if (tokenStart >= 0) {
                    if (parameter == null) positional++
                    parameter = null
                    tokenStart = -1
                }
                i++
                typedFrom = i
            }

            line[i] == '"' -> {
                val closing = skipQuoted(line, i, end) ?: return StoryCompletionContext.None
                i = closing
                tokenStart = 0
            }

            line[i] == '{' -> {
                val closing = skipBracketed(line, i, end, '{', '}')
                    ?: return StoryCompletionContext.Expression(wordBefore(line, end))
                i = closing
                tokenStart = 0
            }

            line[i] == '[' -> {
                val closing = skipBracketed(line, i, end, '[', ']')
                    ?: return StoryCompletionContext.Argument(
                        command,
                        parameter,
                        positional,
                        line.substring(i + 1, end).substringAfterLast(',').trim(),
                    )
                i = closing
                tokenStart = 0
            }

            line[i] == '=' && tokenStart >= 0 && parameter == null -> {
                parameter = line.substring(typedFrom, i)
                i++
                typedFrom = i
                tokenStart = -1
            }

            else -> {
                if (tokenStart < 0) tokenStart = i
                i++
            }
        }
    }

    val typed = line.substring(typedFrom.coerceAtMost(end), end)
    if (command in LABEL_COMMANDS && (typed.startsWith('#') || parameter == null && positional == 0)) {
        return StoryCompletionContext.Label(typed.removePrefix("#"))
    }
    return StoryCompletionContext.Argument(command, parameter, positional, typed)
}

private val LABEL_COMMANDS = setOf("jump", "call")

/** True when a `{` before [end] has not been closed, so the caret sits in an expression. */
private fun hasOpenBrace(line: String, from: Int, end: Int): Boolean {
    var depth = 0
    var i = from
    while (i < end) {
        when (line[i]) {
            '\\' -> i++
            '{' -> depth++
            '}' -> if (depth > 0) depth--
        }
        i++
    }
    return depth > 0
}

/** Position just past the closing quote, or null when the string is still open. */
private fun skipQuoted(line: String, opening: Int, end: Int): Int? {
    var i = opening + 1
    while (i < end) {
        when (line[i]) {
            '\\' -> i++
            '"' -> return i + 1
        }
        i++
    }
    return null
}

private fun skipBracketed(line: String, opening: Int, end: Int, open: Char, close: Char): Int? {
    var depth = 0
    var i = opening
    while (i < end) {
        when (line[i]) {
            '\\' -> i++
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return i + 1
            }
        }
        i++
    }
    return null
}

/** The identifier immediately before [end], which is what an expression is completing. */
private fun wordBefore(line: String, end: Int): String {
    var start = end
    while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_')) start--
    return line.substring(start, end)
}
