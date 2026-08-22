package ru.hollowhorizon.hollowengine.common.utils.expressions

enum class TokenType {
    IDENTIFIER, NUMBER, STRING, BOOLEAN,
    LPAREN, RPAREN, LBRACKET, RBRACKET,
    COMMA, DOT, SEMICOLON,
    ASSIGN,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    NOT, AND, OR, EQ, NEQ, LT, GT, LTE, GTE,
    QUESTION, COLON, COALESCE,
    EOF,
}

data class Token(val type: TokenType, val text: String, val span: Span, val number: Float? = null)

class Lexer(
    private val input: String,
    private val diagnostics: Diagnostics,
    private val offset: Int = 0,
    private val suffixes: Map<String, Float> = emptyMap(),
) {
    private var position = 0

    private fun span(start: Int, end: Int = position) = Span(start + offset, end + offset)

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            skipWhitespace()
            if (position >= input.length) break
            val token = nextToken() ?: continue
            tokens += token
        }
        tokens += Token(TokenType.EOF, "", span(input.length, input.length))
        return tokens
    }

    private fun skipWhitespace() {
        while (position < input.length && input[position].isWhitespace()) position++
    }

    private fun nextToken(): Token? {
        val char = input[position]
        return when {
            char.isLetter() || char == '_' -> lexIdentifier()
            char.isDigit() -> lexNumber()
            char == '.' && input.getOrNull(position + 1)?.isDigit() == true -> lexNumber()
            char == '"' || char == '\'' -> lexString(char)
            else -> lexSymbol()
        }
    }

    private fun lexIdentifier(): Token {
        val start = position
        while (position < input.length && (input[position].isLetterOrDigit() || input[position] == '_')) position++
        val text = input.substring(start, position)
        val type = if (text == "true" || text == "false") TokenType.BOOLEAN else TokenType.IDENTIFIER
        return Token(type, text, span(start))
    }

    private fun lexNumber(): Token {
        val start = position
        if (input[position] == '.') position++
        while (position < input.length && input[position].isDigit()) position++
        if (position < input.length && input[position] == '.' && !input.substring(start, position).contains('.')) {
            position++
            while (position < input.length && input[position].isDigit()) position++
        }
        val text = input.substring(start, position)
        val value = text.toFloatOrNull()
        if (suffixes.isEmpty() || value == null) return Token(TokenType.NUMBER, text, span(start))

        val suffixStart = position
        while (position < input.length && input[position].isLetter()) position++
        if (position == suffixStart) return Token(TokenType.NUMBER, text, span(start), value)

        val suffix = input.substring(suffixStart, position)
        val factor = suffixes[suffix]
        if (factor == null) {
            diagnostics.error(
                "Unknown suffix '$suffix' (expected ${suffixes.keys.joinToString()})",
                span(suffixStart),
            )
            return Token(TokenType.NUMBER, text, span(start), value)
        }
        return Token(TokenType.NUMBER, input.substring(start, position), span(start), value * factor)
    }

    private fun lexString(quote: Char): Token {
        val start = position
        position++
        val text = buildString {
            while (position < input.length && input[position] != quote) {
                if (input[position] == '\\' && position + 1 < input.length) {
                    position++
                    append(
                        when (val escaped = input[position]) {
                            'n' -> '\n'
                            't' -> '\t'
                            'r' -> '\r'
                            else -> escaped
                        }
                    )
                } else {
                    append(input[position])
                }
                position++
            }
        }
        if (position >= input.length) {
            diagnostics.error("Unterminated string", span(start))
        } else {
            position++
        }
        return Token(TokenType.STRING, text, span(start))
    }

    private fun lexSymbol(): Token? {
        val start = position
        val char = input[position++]

        fun token(type: TokenType) = Token(type, input.substring(start, position), span(start))
        fun ifNext(next: Char, whenMatched: TokenType, otherwise: TokenType): Token {
            if (position < input.length && input[position] == next) {
                position++
                return token(whenMatched)
            }
            return token(otherwise)
        }

        return when (char) {
            '(' -> token(TokenType.LPAREN)
            ')' -> token(TokenType.RPAREN)
            '[' -> token(TokenType.LBRACKET)
            ']' -> token(TokenType.RBRACKET)
            ',' -> token(TokenType.COMMA)
            '.' -> token(TokenType.DOT)
            ';' -> token(TokenType.SEMICOLON)
            ':' -> token(TokenType.COLON)
            '+' -> token(TokenType.PLUS)
            '-' -> token(TokenType.MINUS)
            '*' -> token(TokenType.STAR)
            '/' -> token(TokenType.SLASH)
            '%' -> token(TokenType.PERCENT)
            '=' -> ifNext('=', TokenType.EQ, TokenType.ASSIGN)
            '!' -> ifNext('=', TokenType.NEQ, TokenType.NOT)
            '<' -> ifNext('=', TokenType.LTE, TokenType.LT)
            '>' -> ifNext('=', TokenType.GTE, TokenType.GT)
            '?' -> ifNext('?', TokenType.COALESCE, TokenType.QUESTION)
            '&' -> expectPair('&', TokenType.AND, start)
            '|' -> expectPair('|', TokenType.OR, start)
            else -> {
                diagnostics.error("Unexpected character '$char'", span(start))
                null
            }
        }
    }

    private fun expectPair(char: Char, type: TokenType, start: Int): Token? {
        if (position < input.length && input[position] == char) {
            position++
            return Token(type, input.substring(start, position), span(start))
        }
        diagnostics.error("Expected '$char$char'", span(start))
        return null
    }
}
