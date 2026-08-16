package ru.hollowhorizon.hollowengine.common.scripting.ide

import ru.hollowhorizon.hollowengine.client.HighlightTheme

enum class TokenType {
    COMMENT, TODO_COMMENT, KEYWORD, STRING, ANNOTATION, NUMERIC_LITERAL,
    PROPERTY_IDENTIFIER, FIELD, VARIABLE,
    EXTENSION_RECEIVER, VALUE_ARGUMENT_NAME, PARAMETER,
    NAME_REFERENCE, TOP_LEVEL,
    CLASS, INTERFACE, ENUM, OBJECT,
    FUNCTION, METHOD,
    DSL_MARKER,
    DEFAULT;

    fun toKool() = when (this) {
        COMMENT -> HighlightTheme.COMMENT
        TODO_COMMENT -> HighlightTheme.TODO_COMMENT
        DSL_MARKER -> HighlightTheme.DSL_MARKER
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