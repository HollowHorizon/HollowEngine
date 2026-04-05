package ru.hollowhorizon.hollowengine.common.utils.molang.lexer

data class Token(val type: Type, val value: String, val position: Int) {
    enum class Type {
        IDENTIFIER, ASSIGN, NUMBER, BOOLEAN,
        LPAREN, RPAREN, EOF,
        ADD, SUB, MUL, DIV, MOD,
        NOT, AND, OR, EQ, NEQ,
        LT, GT, LTE, GTE,
        DOT, COMMA, QUESTION, COLON, NOTNULL
    }
}