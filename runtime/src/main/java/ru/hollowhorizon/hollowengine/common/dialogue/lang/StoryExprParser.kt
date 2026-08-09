package ru.hollowhorizon.hollowengine.common.dialogue.lang

import ru.hollowhorizon.hollowengine.common.dialogue.StoryBool
import ru.hollowhorizon.hollowengine.common.dialogue.StoryNumber
import ru.hollowhorizon.hollowengine.common.dialogue.StoryString

class StoryParseException(message: String, val span: StorySpan) : RuntimeException(message)

/**
 * Recursive-descent parser for story expressions. Operates on a fragment of the source file so that
 * every span it produces is positioned in whole-file coordinates.
 *
 * Precedence, loosest first: `||`, `&&`, comparisons, `+ -`, `* / %`, unary `! -`.
 */
class StoryExprParser(
    private val text: String,
    private val baseOffset: Int,
    private val line: Int,
    private var pos: Int = 0,
    private val to: Int = text.length,
) {
    companion object {
        /** Millisecond multipliers of duration literal suffixes (`1s`, `500ms`, `2min`, `1h`). */
        private val DURATION_SUFFIXES = mapOf(
            "ms" to 1f,
            "s" to 1_000f,
            "sec" to 1_000f,
            "min" to 60_000f,
            "h" to 3_600_000f,
        )

        fun isIdentStart(c: Char) = c.isLetter() || c == '_'
        fun isIdentPart(c: Char) = c.isLetterOrDigit() || c == '_'
    }

    /** Parses the whole fragment as one expression; anything left over is an error. */
    fun parse(): StoryExpr {
        val expr = parseExpression()
        skipWs()
        if (pos < to) fail("Unexpected '${text[pos]}'", pos)
        return expr
    }

    /** Parses one expression and stops, leaving the cursor after it. Returns the end position too. */
    fun parsePartial(): Pair<StoryExpr, Int> {
        val expr = parseExpression()
        return expr to pos
    }

    private fun parseExpression(): StoryExpr = parseOr()

    private fun parseOr(): StoryExpr = parseLeftAssoc(::parseAnd) { if (match("||")) BinaryOp.OR else null }

    private fun parseAnd(): StoryExpr = parseLeftAssoc(::parseComparison) { if (match("&&")) BinaryOp.AND else null }

    private fun parseComparison(): StoryExpr = parseLeftAssoc(::parseAdditive) {
        when {
            match("==") -> BinaryOp.EQ
            match("!=") -> BinaryOp.NE
            match("<=") -> BinaryOp.LE
            match(">=") -> BinaryOp.GE
            match("<") -> BinaryOp.LT
            match(">") -> BinaryOp.GT
            else -> null
        }
    }

    private fun parseAdditive(): StoryExpr = parseLeftAssoc(::parseMultiplicative) {
        when {
            match("+") -> BinaryOp.ADD
            match("-") -> BinaryOp.SUB
            else -> null
        }
    }

    private fun parseMultiplicative(): StoryExpr = parseLeftAssoc(::parseUnary) {
        when {
            match("*") -> BinaryOp.MUL
            match("/") -> BinaryOp.DIV
            match("%") -> BinaryOp.MOD
            else -> null
        }
    }

    private inline fun parseLeftAssoc(next: () -> StoryExpr, op: () -> BinaryOp?): StoryExpr {
        var left = next()
        while (true) {
            skipWs()
            val operator = op() ?: return left
            val right = next()
            left = StoryExpr.Binary(operator, left, right, spanOf(left.span.start - baseOffset, right.span.end - baseOffset))
        }
    }

    private fun parseUnary(): StoryExpr {
        skipWs()
        val start = pos
        return when {
            match("!") -> {
                val operand = parseUnary()
                StoryExpr.Unary(UnaryOp.NOT, operand, spanOf(start, operand.span.end - baseOffset))
            }

            match("-") -> {
                val operand = parseUnary()
                StoryExpr.Unary(UnaryOp.NEG, operand, spanOf(start, operand.span.end - baseOffset))
            }

            else -> parsePostfix(parsePrimary())
        }
    }

    /** `items[0]`, `items.size`, `Vitalik.uuid`, chained as far as they go. */
    private fun parsePostfix(base: StoryExpr): StoryExpr {
        var expr = base
        while (pos < to) {
            when {
                text[pos] == '[' -> {
                    val start = pos
                    pos++
                    val index = parseExpression()
                    skipWs()
                    if (pos >= to || text[pos] != ']') fail("Expected ']'", pos)
                    pos++
                    expr = StoryExpr.Index(expr, index, spanOf(start, pos))
                }

                text[pos] == '.' && pos + 1 < to && isIdentStart(text[pos + 1]) -> {
                    pos++
                    val start = pos
                    while (pos < to && isIdentPart(text[pos])) pos++
                    expr = StoryExpr.Property(expr, text.substring(start, pos), spanOf(start, pos))
                }

                else -> return expr
            }
        }
        return expr
    }

    private fun parsePrimary(): StoryExpr {
        skipWs()
        if (pos >= to) fail("Expected expression", pos)
        val c = text[pos]
        return when {
            c == '(' -> {
                pos++
                val inner = parseExpression()
                skipWs()
                if (pos >= to || text[pos] != ')') fail("Expected ')'", pos)
                pos++
                inner
            }

            c == '[' -> parseList()
            c == '"' -> parseString()
            c.isDigit() || (c == '.' && pos + 1 < to && text[pos + 1].isDigit()) -> parseNumber()
            isIdentStart(c) -> parseIdentifier()
            else -> fail("Unexpected '$c'", pos)
        }
    }

    private fun parseList(): StoryExpr {
        val start = pos
        pos++ // '['
        val items = mutableListOf<StoryExpr>()
        skipWs()
        if (pos < to && text[pos] == ']') {
            pos++
            return StoryExpr.ListLit(items, spanOf(start, pos))
        }
        while (true) {
            items += parseExpression()
            skipWs()
            when {
                pos < to && text[pos] == ',' -> pos++
                pos < to && text[pos] == ']' -> {
                    pos++
                    return StoryExpr.ListLit(items, spanOf(start, pos))
                }

                else -> fail("Expected ',' or ']' in list", pos)
            }
        }
    }

    private fun parseString(): StoryExpr {
        val start = pos
        pos++ // opening quote
        val sb = StringBuilder()
        while (pos < to) {
            when (val c = text[pos]) {
                '"' -> {
                    pos++
                    return StoryExpr.Lit(StoryString(sb.toString()), spanOf(start, pos))
                }

                '\\' -> {
                    if (pos + 1 >= to) fail("Unterminated escape", pos)
                    sb.append(
                        when (val esc = text[pos + 1]) {
                            'n' -> '\n'
                            't' -> '\t'
                            else -> esc // covers \" \\ \{ \[
                        },
                    )
                    pos += 2
                }

                else -> {
                    sb.append(c)
                    pos++
                }
            }
        }
        fail("Unterminated string", start)
    }

    private fun parseNumber(): StoryExpr {
        val start = pos
        while (pos < to && (text[pos].isDigit() || text[pos] == '.')) pos++
        val numberText = text.substring(start, pos)
        val number = numberText.toFloatOrNull() ?: fail("Bad number '$numberText'", start)

        val suffixStart = pos
        while (pos < to && text[pos].isLetter()) pos++
        val suffix = text.substring(suffixStart, pos)
        val value = when {
            suffix.isEmpty() -> number
            else -> {
                val factor = DURATION_SUFFIXES[suffix]
                    ?: fail("Unknown duration suffix '$suffix' (expected ms, s, sec, min or h)", suffixStart)
                number * factor
            }
        }
        return StoryExpr.Lit(StoryNumber(value), spanOf(start, pos))
    }

    private fun parseIdentifier(): StoryExpr {
        val start = pos
        while (pos < to && isIdentPart(text[pos])) pos++
        return when (val name = text.substring(start, pos)) {
            "true" -> StoryExpr.Lit(StoryBool(true), spanOf(start, pos))
            "false" -> StoryExpr.Lit(StoryBool(false), spanOf(start, pos))
            else -> StoryExpr.VarRef(name, spanOf(start, pos))
        }
    }

    private fun match(op: String): Boolean {
        if (!text.startsWith(op, pos) || pos + op.length > to) return false
        if ((op == "<" || op == ">") && pos + 1 < to && text[pos + 1] == '=') return false
        if (op == "!" && pos + 1 < to && text[pos + 1] == '=') return false
        pos += op.length
        return true
    }

    private fun skipWs() {
        while (pos < to && (text[pos] == ' ' || text[pos] == '\t')) pos++
    }

    private fun spanOf(startLocal: Int, endLocal: Int) =
        StorySpan(baseOffset + startLocal, baseOffset + endLocal, line)

    private fun fail(message: String, at: Int): Nothing =
        throw StoryParseException(message, StorySpan(baseOffset + at, baseOffset + minOf(at + 1, to), line))
}
