package ru.hollowhorizon.hollowengine.client.ui.style

/** A slice of a declaration value with its position inside that value. */
data class HssValueToken(val text: String, val start: Int, val end: Int)

/**
 * Splits [value] on [delimiter], ignoring delimiters inside strings and parentheses.
 * Empty slices are dropped, so `a, b,` yields two tokens.
 */
fun splitValueTokens(value: String, delimiter: Char): List<HssValueToken> =
    scanValue(value) { char, depth, inString -> !inString && depth == 0 && char == delimiter }

/** Splits [value] on top-level whitespace, ignoring whitespace inside strings and parentheses. */
fun splitValueWords(value: String): List<HssValueToken> =
    scanValue(value) { char, depth, inString -> !inString && depth == 0 && char.isWhitespace() }

/**
 * Runs a single left-to-right pass over [value], tracking string and parenthesis nesting,
 * and cuts a token every time [isSeparator] accepts the current character.
 */
private inline fun scanValue(
    value: String,
    isSeparator: (char: Char, depth: Int, inString: Boolean) -> Boolean,
): List<HssValueToken> {
    val tokens = ArrayList<HssValueToken>()
    var start = 0
    var depth = 0
    var inString = false
    var quote = '\u0000'
    for (index in value.indices) {
        val char = value[index]
        if (inString) {
            if (char == quote && value.getOrNull(index - 1) != '\\') inString = false
        } else {
            when (char) {
                '\'', '"' -> {
                    inString = true
                    quote = char
                }

                '(' -> depth++
                ')' -> depth--
            }
        }
        if (isSeparator(char, depth, inString)) {
            tokens.addToken(value, start, index)
            start = index + 1
        }
    }
    tokens.addToken(value, start, value.length)
    return tokens
}

private fun MutableList<HssValueToken>.addToken(value: String, start: Int, end: Int) {
    var from = start
    var to = end
    while (from < to && value[from].isWhitespace()) from++
    while (to > from && value[to - 1].isWhitespace()) to--
    if (from < to) this += HssValueToken(value.substring(from, to), from, to)
}

internal fun splitTopLevel(value: String, delimiter: Char): List<String> =
    splitValueTokens(value, delimiter).map { it.text }

internal fun splitTopLevelWhitespace(value: String): List<String> = splitValueWords(value).map { it.text }

internal fun splitWhitespace(value: String): List<String> =
    value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

internal fun unquote(value: String): String = value.trim().removeSurrounding("\"").removeSurrounding("'")

internal fun functionArgs(value: String, name: String): List<String> {
    val prefix = "$name("
    require(value.startsWith(prefix) && value.endsWith(")")) { "Expected $name(...) value, got '$value'" }
    return splitTopLevel(value.substring(prefix.length, value.length - 1), ',')
}
