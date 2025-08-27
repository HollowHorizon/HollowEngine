package ru.hollowhorizon.hollowengine.common.utils.molang.lexer

class Lexer(input: String) {
    private val input = input.filter { it.isLetterOrDigit() || it in "._()+-*/%=<>!&|,?:" }
    private var position = 0

    private fun peek(offset: Int = 0): Char? = input.getOrNull(position + offset)
    private fun advance() = input[position++]

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (position < input.length) {
            when (val char = peek()!!) {
                in " \t\n\r" -> { position++; continue }
                in 'a'..'z', in 'A'..'Z', '_' -> tokens.add(lexIdentifier())
                in '0'..'9' -> tokens.add(lexNumber())
                '.' -> {
                    if (peek(1)?.isDigit() == true) {
                        tokens.add(lexNumber())
                    } else {
                        tokens.add(lexSymbol())
                    }
                }
                else -> tokens.add(lexSymbol())
            }
        }
        tokens += Token(Token.Type.EOF, "", position)
        return tokens
    }

    private fun lexIdentifier(): Token {
        val start = position
        val value = buildString {
            while (peek()?.isLetterOrDigit() == true || peek() == '_') {
                append(advance())
            }
        }
        val type = when (value) {
            "true", "false" -> Token.Type.BOOLEAN
            else -> Token.Type.IDENTIFIER
        }
        return Token(type, value, start)
    }

    private fun lexNumber(): Token {
        val start = position
        val value = buildString {
            if (peek() == '.') {
                append(advance())
            }

            while (peek()?.isDigit() == true) {
                append(advance())
            }

            if (peek() == '.' && !contains('.')) {
                append(advance())
                while (peek()?.isDigit() == true) {
                    append(advance())
                }
            }

            if (peek()?.lowercaseChar() == 'e') {
                append(advance())
                if (peek() == '+' || peek() == '-') {
                    append(advance())
                }
                while (peek()?.isDigit() == true) {
                    append(advance())
                }
            }

            if(peek()?.lowercaseChar() in setOf('f', 'l', 'd')) {
                advance() // Выражения вроде 1f или 1.0d можно сделать допустимыми
            }
        }
        return Token(Token.Type.NUMBER, value, start)
    }

    private fun lexSymbol(): Token {
        val start = position
        return when (val first = advance()) {
            '=' -> lexNextOrSingle(first, '=', Token.Type.EQ, Token.Type.ASSIGN, start)
            '+' -> Token(Token.Type.ADD, first.toString(), start)
            '-' -> Token(Token.Type.SUB, first.toString(), start)
            '*' -> Token(Token.Type.MUL, first.toString(), start)
            '/' -> Token(Token.Type.DIV, first.toString(), start)
            '%' -> Token(Token.Type.MOD, first.toString(), start)
            '!' -> lexNextOrSingle(first, '=', Token.Type.NEQ, Token.Type.NOT, start)
            '=' -> lexNextOrSingle(first, '=', Token.Type.EQ, null, start)
            '<' -> lexNextOrSingle(first, '=', Token.Type.LTE, Token.Type.LT, start)
            '>' -> lexNextOrSingle(first, '=', Token.Type.GTE, Token.Type.GT, start)
            '&' -> lexDouble(first, '&', Token.Type.AND, start)
            '|' -> lexDouble(first, '|', Token.Type.OR, start)
            '.' -> Token(Token.Type.DOT, first.toString(), start)
            ',' -> Token(Token.Type.COMMA, first.toString(), start)
            '(' -> Token(Token.Type.LPAREN, first.toString(), start)
            ')' -> Token(Token.Type.RPAREN, first.toString(), start)
            '?' -> {
                if (peek() == '?') {
                    advance()
                    Token(Token.Type.NOTNULL, "??", start)
                } else {
                    Token(Token.Type.QUESTION, first.toString(), start)
                }
            }
            ':' -> Token(Token.Type.COLON, first.toString(), start)
            else -> error("Unknown character: '$first' at position $start")
        }
    }

    private fun lexNextOrSingle(
        first: Char,
        expected: Char,
        doubleType: Token.Type,
        singleType: Token.Type?,
        start: Int
    ): Token {
        return if (peek() == expected) {
            val second = advance()
            Token(doubleType, "$first$second", start)
        } else {
            if (singleType == null) {
                error("Unexpected character after '$first' at position $position in '$input'")
            }
            Token(singleType, first.toString(), start)
        }
    }

    private fun lexDouble(
        first: Char,
        expected: Char,
        type: Token.Type,
        start: Int
    ): Token {
        if (peek() == expected) {
            advance()
            return Token(type, "$first$expected", start)
        }
        error("Expected '$expected' after '$first' at position $position")
    }
}