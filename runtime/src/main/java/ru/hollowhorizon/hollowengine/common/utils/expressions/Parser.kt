package ru.hollowhorizon.hollowengine.common.utils.expressions

class Parser(
    private val tokens: List<Token>,
    private val diagnostics: Diagnostics,
) {
    private var position = 0

    private val current: Token get() = tokens[position]

    fun parse(): Ast? {
        val expression = parseSequence() ?: return null
        if (current.type != TokenType.EOF) {
            diagnostics.error("Unexpected '${current.text}'", current.span)
            return null
        }
        return expression
    }

    private fun parseSequence(): Ast? {
        val first = parseAssignment() ?: return null
        if (current.type != TokenType.SEMICOLON) return first

        val statements = mutableListOf(first)
        while (match(TokenType.SEMICOLON)) {
            if (current.type == TokenType.EOF) break
            statements += parseAssignment() ?: return null
        }
        return Ast.Sequence(statements, statements.first().span + statements.last().span)
    }

    private fun parseAssignment(): Ast? {
        val target = parseTernary() ?: return null
        if (!match(TokenType.ASSIGN)) return target

        if (target !is Ast.Name && target !is Ast.Access && target !is Ast.Index) {
            diagnostics.error("Cannot assign to this expression", target.span)
            return null
        }
        val value = parseAssignment() ?: return null
        return Ast.Assign(target, value, target.span + value.span)
    }

    private fun parseTernary(): Ast? {
        val condition = parseBinary(0) ?: return null
        if (!match(TokenType.QUESTION)) return condition

        val ifTrue = parseAssignment() ?: return null
        if (!expect(TokenType.COLON)) return null
        val ifFalse = parseAssignment() ?: return null
        return Ast.Conditional(condition, ifTrue, ifFalse, condition.span + ifFalse.span)
    }

    private fun parseBinary(level: Int): Ast? {
        if (level >= PRECEDENCE.size) return parseUnary()

        var left = parseBinary(level + 1) ?: return null
        while (true) {
            val operator = PRECEDENCE[level][current.type] ?: return left
            position++
            val right = parseBinary(level + 1) ?: return null
            left = Ast.Binary(operator, left, right, left.span + right.span)
        }
    }

    private fun parseUnary(): Ast? {
        val operator = when (current.type) {
            TokenType.NOT -> UnaryOp.NOT
            TokenType.MINUS -> UnaryOp.NEGATE
            else -> return parsePostfix()
        }
        val start = current.span
        position++
        val operand = parseUnary() ?: return null
        return Ast.Unary(operator, operand, start + operand.span)
    }

    private fun parsePostfix(): Ast? {
        var target = parsePrimary() ?: return null
        while (true) {
            when {
                match(TokenType.DOT) -> {
                    val name = current
                    if (!expect(TokenType.IDENTIFIER)) return null
                    target = if (current.type == TokenType.LPAREN) {
                        val arguments = parseArguments() ?: return null
                        Ast.Call(target, name.text, arguments, target.span + tokens[position - 1].span)
                    } else {
                        Ast.Access(target, name.text, name.span, target.span + name.span)
                    }
                }

                current.type == TokenType.LBRACKET -> {
                    position++
                    val index = parseAssignment() ?: return null
                    val closing = current
                    if (!expect(TokenType.RBRACKET)) return null
                    target = Ast.Index(target, index, target.span + closing.span)
                }

                else -> return target
            }
        }
    }

    private fun parsePrimary(): Ast? {
        val token = current
        return when (token.type) {
            TokenType.NUMBER -> {
                position++
                val value = token.number ?: token.text.toFloatOrNull()
                if (value == null) {
                    diagnostics.error("'${token.text}' is not a number", token.span)
                    null
                } else {
                    Ast.NumberLit(value, token.span)
                }
            }

            TokenType.BOOLEAN -> {
                position++
                Ast.BoolLit(token.text == "true", token.span)
            }

            TokenType.STRING -> {
                position++
                Ast.StringLit(token.text, token.span)
            }

            TokenType.IDENTIFIER -> {
                position++
                if (current.type == TokenType.LPAREN) {
                    val arguments = parseArguments() ?: return null
                    Ast.Call(null, token.text, arguments, token.span + tokens[position - 1].span)
                } else {
                    Ast.Name(token.text, token.span)
                }
            }

            TokenType.LPAREN -> {
                position++
                val inner = parseSequence() ?: return null
                if (!expect(TokenType.RPAREN)) return null
                inner
            }

            TokenType.LBRACKET -> parseList()

            else -> {
                diagnostics.error(
                    if (token.type == TokenType.EOF) "Expression is incomplete" else "Unexpected '${token.text}'",
                    token.span,
                )
                null
            }
        }
    }

    private fun parseList(): Ast? {
        val start = current.span
        position++
        val items = mutableListOf<Ast>()
        if (current.type != TokenType.RBRACKET) {
            do {
                items += parseAssignment() ?: return null
            } while (match(TokenType.COMMA))
        }
        val closing = current
        if (!expect(TokenType.RBRACKET)) return null
        return Ast.ListLit(items, start + closing.span)
    }

    private fun parseArguments(): List<Ast>? {
        position++
        val arguments = mutableListOf<Ast>()
        if (current.type != TokenType.RPAREN) {
            do {
                arguments += parseAssignment() ?: return null
            } while (match(TokenType.COMMA))
        }
        if (!expect(TokenType.RPAREN)) return null
        return arguments
    }

    private fun match(type: TokenType): Boolean {
        if (current.type != type) return false
        position++
        return true
    }

    private fun expect(type: TokenType): Boolean {
        if (match(type)) return true
        diagnostics.error("Expected ${type.describe()}, got '${current.text}'", current.span)
        return false
    }

    private companion object {
        val PRECEDENCE: List<Map<TokenType, BinaryOp>> = listOf(
            mapOf(TokenType.COALESCE to BinaryOp.COALESCE),
            mapOf(TokenType.OR to BinaryOp.OR),
            mapOf(TokenType.AND to BinaryOp.AND),
            mapOf(TokenType.EQ to BinaryOp.EQ, TokenType.NEQ to BinaryOp.NEQ),
            mapOf(
                TokenType.LT to BinaryOp.LT,
                TokenType.GT to BinaryOp.GT,
                TokenType.LTE to BinaryOp.LTE,
                TokenType.GTE to BinaryOp.GTE,
            ),
            mapOf(TokenType.PLUS to BinaryOp.ADD, TokenType.MINUS to BinaryOp.SUBTRACT),
            mapOf(
                TokenType.STAR to BinaryOp.MULTIPLY,
                TokenType.SLASH to BinaryOp.DIVIDE,
                TokenType.PERCENT to BinaryOp.REMAINDER,
            ),
        )

        fun TokenType.describe(): String = when (this) {
            TokenType.RPAREN -> "')'"
            TokenType.RBRACKET -> "']'"
            TokenType.COLON -> "':'"
            TokenType.IDENTIFIER -> "a name"
            else -> name.lowercase()
        }
    }
}

/**
 * Parses [source]; returns null and fills [diagnostics] when it does not parse.
 */
fun parseExpression(
    source: String,
    diagnostics: Diagnostics,
    options: Options = Options(),
    offset: Int = 0,
): Ast? = Parser(Lexer(source, diagnostics, offset, options.numberSuffixes).tokenize(), diagnostics).parse()
