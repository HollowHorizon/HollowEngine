package ru.hollowhorizon.hollowengine.client.models.internal.animator

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal class FastAnimationExpression private constructor(
    private val root: Expr,
) {
    fun float(context: AnimatorEvaluationContext): Float =
        root.eval(context).number.toFloat()

    fun boolean(context: AnimatorEvaluationContext): Boolean =
        root.eval(context).boolean

    companion object {
        fun compile(source: String): FastAnimationExpression =
            FastAnimationExpression(Parser(Lexer(source).tokenize()).parse())
    }
}

private data class Value(val number: Double) {
    val boolean: Boolean get() = number != 0.0

    companion object {
        fun boolean(value: Boolean) = Value(if (value) 1.0 else 0.0)
    }
}

private sealed interface Expr {
    fun eval(context: AnimatorEvaluationContext): Value
}

private data class NumberExpr(val value: Double) : Expr {
    override fun eval(context: AnimatorEvaluationContext): Value = Value(value)
}

private data class BooleanExpr(val value: Boolean) : Expr {
    override fun eval(context: AnimatorEvaluationContext): Value = Value.boolean(value)
}

private data class VariableExpr(val name: String) : Expr {
    override fun eval(context: AnimatorEvaluationContext): Value =
        Value(
            when (name) {
                "delta_time" -> context.deltaTime.toDouble()
                "time", "anim_time" -> context.time.toDouble()
                else -> context.values[name]?.toDouble() ?: 0.0
            }
        )
}

private data class UnaryExpr(val op: String, val value: Expr) : Expr {
    override fun eval(context: AnimatorEvaluationContext): Value {
        val evaluated = value.eval(context)
        return when (op) {
            "-" -> Value(-evaluated.number)
            "+" -> evaluated
            "!" -> Value.boolean(!evaluated.boolean)
            else -> error("Unsupported unary operator $op")
        }
    }
}

private data class BinaryExpr(val left: Expr, val op: String, val right: Expr) : Expr {
    override fun eval(context: AnimatorEvaluationContext): Value =
        when (op) {
            "&&" -> Value.boolean(left.eval(context).boolean && right.eval(context).boolean)
            "||" -> Value.boolean(left.eval(context).boolean || right.eval(context).boolean)
            else -> {
                val a = left.eval(context).number
                val b = right.eval(context).number
                when (op) {
                    "+" -> Value(a + b)
                    "-" -> Value(a - b)
                    "*" -> Value(a * b)
                    "/" -> Value(if (b == 0.0) 0.0 else a / b)
                    "%" -> Value(if (b == 0.0) 0.0 else a % b)
                    "==" -> Value.boolean(a == b)
                    "!=" -> Value.boolean(a != b)
                    "<" -> Value.boolean(a < b)
                    "<=" -> Value.boolean(a <= b)
                    ">" -> Value.boolean(a > b)
                    ">=" -> Value.boolean(a >= b)
                    else -> error("Unsupported binary operator $op")
                }
            }
        }
}

private data class FunctionExpr(val name: String, val args: List<Expr>) : Expr {
    override fun eval(context: AnimatorEvaluationContext): Value {
        val values = args.map { it.eval(context).number }
        return Value(
            when (name) {
                "abs" -> abs(values.getOrElse(0) { 0.0 })
                "min" -> min(values.getOrElse(0) { 0.0 }, values.getOrElse(1) { 0.0 })
                "max" -> max(values.getOrElse(0) { 0.0 }, values.getOrElse(1) { 0.0 })
                "clamp" -> values.getOrElse(0) { 0.0 }
                    .coerceIn(values.getOrElse(1) { 0.0 }, values.getOrElse(2) { 1.0 })
                "sin" -> sin(values.getOrElse(0) { 0.0 })
                "cos" -> cos(values.getOrElse(0) { 0.0 })
                else -> error("Unsupported function $name")
            }
        )
    }
}

private class Parser(private val tokens: List<Token>) {
    private var index = 0

    fun parse(): Expr {
        val expr = parseOr()
        expect(TokenType.End)
        return expr
    }

    private fun parseOr(): Expr {
        var expr = parseAnd()
        while (matchOperator("||")) expr = BinaryExpr(expr, "||", parseAnd())
        return expr
    }

    private fun parseAnd(): Expr {
        var expr = parseEquality()
        while (matchOperator("&&")) expr = BinaryExpr(expr, "&&", parseEquality())
        return expr
    }

    private fun parseEquality(): Expr {
        var expr = parseComparison()
        while (peek().text == "==" || peek().text == "!=") {
            val op = advance().text
            expr = BinaryExpr(expr, op, parseComparison())
        }
        return expr
    }

    private fun parseComparison(): Expr {
        var expr = parseAdditive()
        while (peek().text in COMPARISON_OPERATORS) {
            val op = advance().text
            expr = BinaryExpr(expr, op, parseAdditive())
        }
        return expr
    }

    private fun parseAdditive(): Expr {
        var expr = parseMultiplicative()
        while (peek().text == "+" || peek().text == "-") {
            val op = advance().text
            expr = BinaryExpr(expr, op, parseMultiplicative())
        }
        return expr
    }

    private fun parseMultiplicative(): Expr {
        var expr = parseUnary()
        while (peek().text == "*" || peek().text == "/" || peek().text == "%") {
            val op = advance().text
            expr = BinaryExpr(expr, op, parseUnary())
        }
        return expr
    }

    private fun parseUnary(): Expr =
        if (peek().text == "-" || peek().text == "+" || peek().text == "!") {
            UnaryExpr(advance().text, parseUnary())
        } else {
            parsePrimary()
        }

    private fun parsePrimary(): Expr {
        val token = advance()
        return when (token.type) {
            TokenType.Number -> NumberExpr(token.text.toDouble())
            TokenType.Identifier -> parseIdentifier(token.text)
            TokenType.LeftParen -> parseOr().also { expect(TokenType.RightParen) }
            else -> error("Expected expression, got ${token.text}")
        }
    }

    private fun parseIdentifier(name: String): Expr =
        when {
            name == "true" -> BooleanExpr(true)
            name == "false" -> BooleanExpr(false)
            match(TokenType.LeftParen) -> {
                val args = mutableListOf<Expr>()
                if (!match(TokenType.RightParen)) {
                    do {
                        args += parseOr()
                    } while (match(TokenType.Comma))
                    expect(TokenType.RightParen)
                }
                FunctionExpr(name, args)
            }
            else -> VariableExpr(name)
        }

    private fun matchOperator(operator: String): Boolean =
        if (peek().type == TokenType.Operator && peek().text == operator) {
            index++
            true
        } else {
            false
        }

    private fun match(type: TokenType): Boolean =
        if (peek().type == type) {
            index++
            true
        } else {
            false
        }

    private fun expect(type: TokenType): Token {
        val token = advance()
        if (token.type != type) error("Expected $type, got ${token.text}")
        return token
    }

    private fun advance(): Token = tokens[index++]
    private fun peek(): Token = tokens[index]

    companion object {
        private val COMPARISON_OPERATORS = setOf("<", "<=", ">", ">=")
    }
}

private class Lexer(private val source: String) {
    private var index = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (index < source.length) {
            when (val char = source[index]) {
                ' ', '\t', '\r', '\n' -> index++
                '(' -> tokens += single(TokenType.LeftParen)
                ')' -> tokens += single(TokenType.RightParen)
                ',' -> tokens += single(TokenType.Comma)
                '+', '-', '*', '/', '%', '<', '>', '=', '!', '&', '|' -> tokens += operator()
                else -> when {
                    char.isDigit() || char == '.' -> tokens += number()
                    char.isIdentifierStart() -> tokens += identifier()
                    else -> error("Unexpected character `$char` in animation expression")
                }
            }
        }
        tokens += Token(TokenType.End, "")
        return tokens
    }

    private fun single(type: TokenType): Token =
        Token(type, source[index++].toString())

    private fun number(): Token {
        val start = index
        var hasDot = false
        while (index < source.length) {
            val char = source[index]
            if (char == '.') {
                if (hasDot) break
                hasDot = true
                index++
            } else if (char.isDigit()) {
                index++
            } else {
                break
            }
        }
        return Token(TokenType.Number, source.substring(start, index))
    }

    private fun identifier(): Token {
        val start = index
        index++
        while (index < source.length && source[index].isIdentifierPart()) index++
        return Token(TokenType.Identifier, source.substring(start, index))
    }

    private fun operator(): Token {
        val two = source.substring(index, min(index + 2, source.length))
        if (two in DOUBLE_OPERATORS) {
            index += 2
            return Token(TokenType.Operator, two)
        }
        return Token(TokenType.Operator, source[index++].toString())
    }

    companion object {
        private val DOUBLE_OPERATORS = setOf("&&", "||", "==", "!=", "<=", ">=")
    }
}

private data class Token(val type: TokenType, val text: String)

private enum class TokenType {
    Number,
    Identifier,
    Operator,
    LeftParen,
    RightParen,
    Comma,
    End,
}

private fun Char.isIdentifierStart(): Boolean =
    this == '_' || isLetter()

private fun Char.isIdentifierPart(): Boolean =
    this == '_' || isLetterOrDigit()
