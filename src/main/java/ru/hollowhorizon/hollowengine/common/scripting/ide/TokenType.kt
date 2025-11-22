package ru.hollowhorizon.hollowengine.common.scripting.ide

import ru.hollowhorizon.hollowengine.client.HighlightTheme

enum class TokenType {
    COMMENT,
    KEYWORD,
    STRING,
    PROPERTY_IDENTIFIER,
    EXTENSION_RECEIVER,
    ANNOTATION,
    VALUE_ARGUMENT_NAME,
    NAME_REFERENCE,
    NUMERIC_LITERAL,
    TOP_LEVEL,
    DEFAULT,

    CLASS, INTERFACE, ENUM, OBJECT,
    FUNCTION, METHOD,
    PARAMETER, FIELD, VARIABLE;

    fun toKool() = when (this) {
        COMMENT -> HighlightTheme.COMMENT
        KEYWORD -> HighlightTheme.KEYWORD
        STRING -> HighlightTheme.STRING
        ANNOTATION -> HighlightTheme.ANNOTATION
        NUMERIC_LITERAL -> HighlightTheme.NUMERIC_LITERAL

        PROPERTY_IDENTIFIER, FIELD -> HighlightTheme.PROPERTY_IDENTIFIER
        VARIABLE -> HighlightTheme.VARIABLE
        EXTENSION_RECEIVER -> HighlightTheme.EXTENSION_RECEIVER
        VALUE_ARGUMENT_NAME -> HighlightTheme.VALUE_ARGUMENT_NAME
        PARAMETER -> HighlightTheme.PARAMETER

        NAME_REFERENCE -> HighlightTheme.NAME_REFERENCE
        TOP_LEVEL -> HighlightTheme.TOP_LEVEL

        CLASS -> HighlightTheme.CLASS
        INTERFACE -> HighlightTheme.INTERFACE
        ENUM, OBJECT -> HighlightTheme.CLASS

        FUNCTION -> HighlightTheme.FUNCTION
        METHOD -> HighlightTheme.METHOD

        DEFAULT -> HighlightTheme.DEFAULT
    }
}