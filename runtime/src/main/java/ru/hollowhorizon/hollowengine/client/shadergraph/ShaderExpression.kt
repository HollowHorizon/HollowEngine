package ru.hollowhorizon.hollowengine.client.shadergraph

/** What can be wrong with the text of an expression node; each has a text under `expression.<name>`. */
enum class ShaderExpressionProblem {
    EMPTY, UNEXPECTED_CHARACTER, UNEXPECTED_TOKEN, EXPECTED_VALUE, UNCLOSED, UNKNOWN_FUNCTION, RESERVED_NAME,
}

class ShaderExpressionError(val problem: ShaderExpressionProblem, val start: Int, val end: Int, val detail: String = "")

enum class ShaderTokenKind {
    NUMBER,

    /** A name that becomes a pin of the node. */
    VARIABLE,

    /** A name that stands for a number, such as `PI`. */
    CONSTANT,

    BOOLEAN, FUNCTION,

    /** What follows a dot: a swizzle such as `xy`. */
    MEMBER, OPERATOR, END,
}

class ShaderToken(val kind: ShaderTokenKind, val text: String, val start: Int, val end: Int)

/**
 * The text of an expression node: one GLSL expression over names of its own choosing, such as
 * `(sin(x) + 1) / 2`. Every free name becomes an input of the node, and the expression is its output.
 */
class ShaderExpression private constructor(
    val source: String,
    val tokens: List<ShaderToken>,
    val error: ShaderExpressionError?,
) {
    /** The names that become pins, in the order they first appear. */
    val variables: List<String> = tokens.filter { it.kind == ShaderTokenKind.VARIABLE }.map { it.text }.distinct()

    /** The GLSL of the expression with each name read through [codeOf], or null when the text is not valid. */
    fun glsl(codeOf: (String) -> String): String? {
        if (error != null) return null
        return buildString {
            tokens.forEach { token ->
                when (token.kind) {
                    ShaderTokenKind.VARIABLE -> append('(').append(codeOf(token.text)).append(')')
                    ShaderTokenKind.CONSTANT -> append(Constants.getValue(token.text))
                    ShaderTokenKind.NUMBER -> append(floatLiteral(token.text))
                    ShaderTokenKind.OPERATOR -> when (token.text) {
                        ".", "(", ")" -> append(token.text)
                        "," -> append(", ")
                        else -> append(' ').append(token.text).append(' ')
                    }

                    ShaderTokenKind.END -> Unit
                    else -> append(token.text)
                }
            }
        }.trim()
    }

    /**
     * How many components the expression has, one to four, given how many each name has through
     * [widthOf]; null when the text is not valid or that cannot be told.
     */
    fun width(widthOf: (String) -> Int?): Int? {
        if (error != null) return null
        val parser = Parser(tokens, widthOf)
        parser.run()
        return parser.width.takeIf { it in 1..4 }
    }

    companion object {
        /** The functions an expression can call, with their parameters, for completion. */
        val Functions: Map<String, String> = linkedMapOf(
            "sin" to "(x)", "cos" to "(x)", "tan" to "(x)", "asin" to "(x)", "acos" to "(x)", "atan" to "(y, x)",
            "sinh" to "(x)", "cosh" to "(x)", "tanh" to "(x)", "radians" to "(degrees)", "degrees" to "(radians)",
            "pow" to "(x, y)", "exp" to "(x)", "log" to "(x)", "exp2" to "(x)", "log2" to "(x)", "sqrt" to "(x)",
            "inversesqrt" to "(x)", "abs" to "(x)", "sign" to "(x)", "floor" to "(x)", "ceil" to "(x)",
            "round" to "(x)", "trunc" to "(x)", "fract" to "(x)", "mod" to "(x, y)", "min" to "(x, y)",
            "max" to "(x, y)", "clamp" to "(x, min, max)", "mix" to "(a, b, t)", "step" to "(edge, x)",
            "smoothstep" to "(edge0, edge1, x)", "length" to "(v)", "distance" to "(a, b)", "dot" to "(a, b)",
            "cross" to "(a, b)", "normalize" to "(v)", "reflect" to "(i, n)", "refract" to "(i, n, eta)",
            "float" to "(x)", "vec2" to "(x, y)", "vec3" to "(x, y, z)", "vec4" to "(x, y, z, w)",
            "dFdx" to "(x)", "dFdy" to "(x)", "fwidth" to "(x)",
        )

        /** Names that read as numbers, and the number each is written as. */
        val Constants: Map<String, String> = linkedMapOf(
            "PI" to "3.14159265",
            "TAU" to "6.28318531",
            "E" to "2.71828183",
        )

        /** The literals of GLSL that look like names; without them here they would become pins. */
        val Booleans = setOf("true", "false")

        private val Reserved = setOf(
            "if", "else", "for", "while", "do", "return", "break", "continue", "discard", "struct", "const",
            "uniform", "in", "out", "inout", "int", "uint", "bool", "mat2", "mat3", "mat4", "sampler2D", "void",
        )

        private val TwoCharOperators = setOf("<=", ">=", "==", "!=", "&&", "||")
        private const val OneCharOperators = "+-*/<>!?:.(),"

        fun parse(source: String): ShaderExpression {
            val lexed = lex(source)
            val tokens = lexed.first
            val error = lexed.second ?: check(source, tokens) ?: Parser(tokens).run()
            return ShaderExpression(source, tokens, error)
        }

        private fun lex(source: String): Pair<List<ShaderToken>, ShaderExpressionError?> {
            val tokens = ArrayList<ShaderToken>()
            var at = 0
            while (at < source.length) {
                val char = source[at]
                val pair = if (at + 1 < source.length) source.substring(at, at + 2) else ""
                when {
                    char.isWhitespace() -> at++
                    char.isDigit() || char == '.' && source.getOrNull(at + 1)?.isDigit() == true -> {
                        val end = numberEnd(source, at)
                        tokens += ShaderToken(ShaderTokenKind.NUMBER, source.substring(at, end), at, end)
                        at = end
                    }

                    char.isLetter() || char == '_' -> {
                        var end = at
                        while (end < source.length && (source[end].isLetterOrDigit() || source[end] == '_')) end++
                        val name = source.substring(at, end)
                        tokens += ShaderToken(nameKind(source, name, tokens.lastOrNull(), end), name, at, end)
                        at = end
                    }

                    pair in TwoCharOperators -> {
                        tokens += ShaderToken(ShaderTokenKind.OPERATOR, pair, at, at + 2)
                        at += 2
                    }

                    char in OneCharOperators -> {
                        tokens += ShaderToken(ShaderTokenKind.OPERATOR, char.toString(), at, at + 1)
                        at++
                    }

                    else -> return tokens to ShaderExpressionError(
                        ShaderExpressionProblem.UNEXPECTED_CHARACTER,
                        at,
                        at + 1,
                        char.toString()
                    )
                }
            }
            tokens += ShaderToken(ShaderTokenKind.END, "", source.length, source.length)
            return tokens to null
        }

        private fun numberEnd(source: String, start: Int): Int {
            var end = start
            while (end < source.length && source[end].isDigit()) end++
            if (end < source.length && source[end] == '.') {
                end++
                while (end < source.length && source[end].isDigit()) end++
            }
            if (end < source.length && (source[end] == 'e' || source[end] == 'E')) {
                var exponent = end + 1
                if (exponent < source.length && (source[exponent] == '+' || source[exponent] == '-')) exponent++
                if (exponent < source.length && source[exponent].isDigit()) {
                    while (exponent < source.length && source[exponent].isDigit()) exponent++
                    end = exponent
                }
            }
            return end
        }

        /** A name after a dot is a member, one before a parenthesis a call, a known one a constant, anything else a pin. */
        private fun nameKind(source: String, name: String, previous: ShaderToken?, end: Int): ShaderTokenKind = when {
            previous?.kind == ShaderTokenKind.OPERATOR && previous.text == "." -> ShaderTokenKind.MEMBER
            source.substring(end).trimStart().startsWith("(") -> ShaderTokenKind.FUNCTION
            name in Constants -> ShaderTokenKind.CONSTANT
            name in Booleans -> ShaderTokenKind.BOOLEAN
            else -> ShaderTokenKind.VARIABLE
        }

        private fun check(source: String, tokens: List<ShaderToken>): ShaderExpressionError? {
            if (source.isBlank()) return ShaderExpressionError(ShaderExpressionProblem.EMPTY, 0, source.length)
            tokens.forEach { token ->
                when (token.kind) {
                    ShaderTokenKind.FUNCTION -> if (token.text !in Functions) {
                        return ShaderExpressionError(
                            ShaderExpressionProblem.UNKNOWN_FUNCTION,
                            token.start,
                            token.end,
                            token.text
                        )
                    }

                    ShaderTokenKind.VARIABLE -> if (isReserved(token.text)) {
                        return ShaderExpressionError(
                            ShaderExpressionProblem.RESERVED_NAME,
                            token.start,
                            token.end,
                            token.text
                        )
                    }

                    else -> Unit
                }
            }
            return null
        }

        private fun isReserved(name: String): Boolean =
            name in Reserved || name in Functions || name.startsWith("gl_") || name.startsWith("sg_") || name.startsWith(
                "p_"
            ) || "__" in name

        /** GLSL takes `2` as an int; every number is written as a float so it mixes with the rest. */
        private fun floatLiteral(text: String): String = when {
            '.' in text || 'e' in text || 'E' in text -> text
            else -> "$text.0"
        }
    }
}

private class Parser(private val tokens: List<ShaderToken>, private val widthOf: (String) -> Int? = { null }) {
    private var at = 0

    var width: Int = UNKNOWN
        private set

    private class Failure(val error: ShaderExpressionError) : RuntimeException(null, null, false, false)

    fun run(): ShaderExpressionError? = try {
        width = expression()
        if (peek().kind != ShaderTokenKind.END) fail(ShaderExpressionProblem.UNEXPECTED_TOKEN)
        null
    } catch (failure: Failure) {
        failure.error
    }

    private fun peek(): ShaderToken = tokens[at]
    private fun isOperator(text: String): Boolean = peek().kind == ShaderTokenKind.OPERATOR && peek().text == text

    private fun fail(problem: ShaderExpressionProblem): Nothing {
        val token = peek()
        throw Failure(ShaderExpressionError(problem, token.start, token.end.coerceAtLeast(token.start), token.text))
    }

    private fun expect(text: String) {
        if (isOperator(text)) {
            at++
            return
        }
        fail(if (peek().kind == ShaderTokenKind.END) ShaderExpressionProblem.UNCLOSED else ShaderExpressionProblem.UNEXPECTED_TOKEN)
    }

    private fun expression(): Int {
        val condition = level(0)
        if (!isOperator("?")) return condition
        at++
        val whenTrue = expression()
        expect(":")
        return widest(whenTrue, expression())
    }

    private fun level(index: Int): Int {
        if (index == Levels.size) return unary()
        var width = level(index + 1)
        while (peek().kind == ShaderTokenKind.OPERATOR && peek().text in Levels[index]) {
            val operator = peek().text
            at++
            val right = level(index + 1)
            width = if (operator in Arithmetic) widest(width, right) else 1
        }
        return width
    }

    private fun unary(): Int {
        if (isOperator("-") || isOperator("+")) {
            at++
            return unary()
        }
        if (isOperator("!")) {
            at++
            unary()
            return 1
        }
        var width = primary()
        while (isOperator(".")) {
            at++
            val member = peek()
            if (member.kind != ShaderTokenKind.MEMBER) fail(ShaderExpressionProblem.UNEXPECTED_TOKEN)
            at++
            width = member.text.length.takeIf { it in 1..4 } ?: UNKNOWN
        }
        return width
    }

    private fun primary(): Int {
        val token = peek()
        return when {
            token.kind in Literals -> {
                at++
                1
            }

            token.kind == ShaderTokenKind.VARIABLE -> {
                at++
                widthOf(token.text) ?: UNKNOWN
            }

            token.kind == ShaderTokenKind.FUNCTION -> {
                at++
                expect("(")
                val arguments = ArrayList<Int>()
                if (!isOperator(")")) {
                    arguments += expression()
                    while (isOperator(",")) {
                        at++
                        arguments += expression()
                    }
                }
                expect(")")
                resultWidth(token.text, arguments)
            }

            isOperator("(") -> {
                at++
                expression().also { expect(")") }
            }

            else -> fail(ShaderExpressionProblem.EXPECTED_VALUE)
        }
    }

    private fun resultWidth(function: String, arguments: List<Int>): Int = when (function) {
        "length", "distance", "dot", "float" -> 1
        "vec2" -> 2
        "vec3", "cross" -> 3
        "vec4" -> 4
        else -> arguments.fold(1, ::widest)
    }

    private fun widest(a: Int, b: Int): Int = if (a == UNKNOWN || b == UNKNOWN) UNKNOWN else maxOf(a, b)

    companion object {
        private val Literals = setOf(ShaderTokenKind.NUMBER, ShaderTokenKind.CONSTANT, ShaderTokenKind.BOOLEAN)

        const val UNKNOWN = -1

        private val Levels = listOf(
            setOf("||"), setOf("&&"), setOf("==", "!="), setOf("<", ">", "<=", ">="), setOf("+", "-"), setOf("*", "/"),
        )
        private val Arithmetic = setOf("+", "-", "*", "/")
    }
}
