package ru.hollowhorizon.hc.common.utils.molang.parser

import ru.hollowhorizon.hc.common.utils.molang.compiler.optimizeBooleanConstants
import ru.hollowhorizon.hc.common.utils.molang.compiler.optimizeConstants
import ru.hollowhorizon.hc.common.utils.molang.lexer.Token

class Parser(private val tokens: List<Token>) {
    private var pos = 0
    private fun peek(offset: Int = 0) = tokens.getOrNull(pos + offset)
    private fun current() = tokens.getOrNull(pos)
    private fun advance() = tokens[pos++]
    private fun match(vararg types: Token.Type): Boolean {
        val tok = current() ?: return false
        return if (tok.type in types) {
            pos++; true
        } else false
    }

    private fun expect(type: Token.Type): Token {
        val tok = advance()
        if (tok.type != type)
            error("Expected $type but got ${tok.type} at position ${tok.position}")
        return tok
    }

    private fun parseQualifiedName(tok: Token): AstFloat {
        val path = mutableListOf(tok.value)
        // Собираем цепочку идентификаторов через точки
        while (match(Token.Type.DOT)) {
            path.add(expect(Token.Type.IDENTIFIER).value)
        }

        // Обрабатываем вызов функции или возвращаем доступ к переменной
        return if (match(Token.Type.LPAREN)) {
            val args = mutableListOf<AstFloat>()
            if (peek()?.type != Token.Type.RPAREN) {
                do {
                    args.add(parseFloatExpr())
                } while (match(Token.Type.COMMA))
            }
            expect(Token.Type.RPAREN)

            if (peek()?.type == Token.Type.DOT) error("Complex calls (like `${path.joinToString(".")}(...).other.variables`) not supported yet!")

            FunctionCall(path.joinToString("."), args)
        } else {
            if(path[0]=="variable" || path[0] == "v") VariableAccess(path.drop(1))
            else VariableAccess(path)
        }
    }

    private fun parsePrimary(): AstFloat {
        val tok = current() ?: error("Unexpected EOF")
        return when (tok.type) {
            Token.Type.NUMBER -> {
                advance()
                NumberLiteral(tok.value.toFloat())
            }

            Token.Type.BOOLEAN -> {
                advance()
                NumberLiteral(if (tok.value == "true") 1f else 0f)
            }

            Token.Type.IDENTIFIER -> {
                advance()
                parseQualifiedName(tok)
            }

            Token.Type.LPAREN -> {
                advance()
                val expr = parseFloatExpr()
                expect(Token.Type.RPAREN)
                expr
            }

            else -> error("Unexpected token ${tok.type}")
        }
    }

    private fun parseUnary(): AstFloat {
        return when {
            match(Token.Type.SUB) -> BinaryOp(NumberLiteral(0f), Token.Type.SUB, parseUnary())
            match(Token.Type.NOT) -> {
                val notResult = NotOp(FloatToBool(parseUnary()))
                Conditional(notResult, NumberLiteral(1f), NumberLiteral(0f))
            }

            else -> parsePrimary()
        }
    }

    private fun parseMulDivMod(): AstFloat {
        var expr = parseUnary()
        while (true) {
            when (peek()?.type) {
                Token.Type.MUL, Token.Type.DIV, Token.Type.MOD -> {
                    val op = advance().type
                    expr = BinaryOp(expr, op, parseUnary())
                }

                else -> break
            }
        }
        return expr
    }

    private fun parseAddSub(): AstFloat {
        var expr = parseMulDivMod()
        while (true) {
            when (peek()?.type) {
                Token.Type.ADD, Token.Type.SUB -> {
                    val op = advance().type
                    expr = BinaryOp(expr, op, parseMulDivMod())
                }

                else -> break
            }
        }
        return expr
    }

    private fun parseComparison(): AstFloat {
        var expr = parseAddSub()
        while (true) {
            val op = peek()?.type
            if (op != null && op in setOf(
                    Token.Type.EQ,
                    Token.Type.NEQ,
                    Token.Type.LT,
                    Token.Type.GT,
                    Token.Type.LTE,
                    Token.Type.GTE
                )
            ) {
                advance()
                val right = parseAddSub()
                expr = Conditional(CompareOp(expr, op, right), NumberLiteral(1f), NumberLiteral(0f))
            } else break
        }
        return expr
    }

    private fun parseLogicalAnd(): AstFloat {
        var expr = parseComparison()
        while (match(Token.Type.AND)) {
            val logicalResult = LogicalOp(FloatToBool(expr), Token.Type.AND, FloatToBool(parseComparison()))
            expr = Conditional(logicalResult, NumberLiteral(1f), NumberLiteral(0f))
        }
        return expr
    }

    private fun parseLogicalOr(): AstFloat {
        var expr = parseLogicalAnd()
        while (match(Token.Type.OR)) {
            val logicalResult = LogicalOp(FloatToBool(expr), Token.Type.OR, FloatToBool(parseLogicalAnd()))
            expr = Conditional(logicalResult, NumberLiteral(1f), NumberLiteral(0f))
        }
        return expr
    }

    fun parseFloatExpr(): AstFloat {
        var expr = parseLogicalOr()
        expr = if (match(Token.Type.QUESTION)) {
            val thenExpr = parseFloatExpr()
            expect(Token.Type.COLON)
            val elseExpr = parseFloatExpr()
            Conditional(FloatToBool(expr), thenExpr, elseExpr)
        } else expr

        expr = if(peek()?.type == Token.Type.ASSIGN) {
            if (expr is VariableAccess) {
                advance() // Съедаем ASSIGN
                val value = parseFloatExpr()
                Assignment(expr, value)
            } else {
                error("Left-hand side of assignment must be a variable")
            }
        } else expr

        return optimizeConstants(expr)
    }

    fun parseBooleanExpr(): AstBoolean {
        var expr = parseLogicalAndBoolean()
        while (match(Token.Type.OR)) {
            expr = LogicalOp(expr, Token.Type.OR, parseLogicalAndBoolean())
        }
        return optimizeBooleanConstants(expr)
    }

    private fun parseLogicalAndBoolean(): AstBoolean {
        var expr = parseUnaryBoolean()
        while (match(Token.Type.AND)) {
            expr = LogicalOp(expr, Token.Type.AND, parseUnaryBoolean())
        }
        return expr
    }

    private fun parseUnaryBoolean(): AstBoolean {
        return if (match(Token.Type.NOT)) {
            NotOp(parseUnaryBoolean())
        } else {
            if (match(Token.Type.LPAREN)) {
                val expr = parseBooleanExpr()
                expect(Token.Type.RPAREN)
                expr
            } else {
                val floatExpr = parseAddSub()
                when {
                    match(Token.Type.EQ) -> CompareOp(floatExpr, Token.Type.EQ, parseAddSub())
                    match(Token.Type.NEQ) -> CompareOp(floatExpr, Token.Type.NEQ, parseAddSub())
                    match(Token.Type.LT) -> CompareOp(floatExpr, Token.Type.LT, parseAddSub())
                    match(Token.Type.GT) -> CompareOp(floatExpr, Token.Type.GT, parseAddSub())
                    match(Token.Type.LTE) -> CompareOp(floatExpr, Token.Type.LTE, parseAddSub())
                    match(Token.Type.GTE) -> CompareOp(floatExpr, Token.Type.GTE, parseAddSub())
                    floatExpr is VariableAccess -> floatExpr
                    else -> FloatToBool(floatExpr)
                }
            }
        }
    }
}