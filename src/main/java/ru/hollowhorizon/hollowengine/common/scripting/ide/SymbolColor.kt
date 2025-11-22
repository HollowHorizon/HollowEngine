package ru.hollowhorizon.hollowengine.common.scripting.ide

import ru.hollowhorizon.hollowengine.client.HighlightTheme

enum class SymbolColor {
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
    DEFAULT;

    fun toKool() = when (this) {
        COMMENT -> HighlightTheme.COMMENT
        STRING -> HighlightTheme.STRING
        PROPERTY_IDENTIFIER -> HighlightTheme.PROPERTY_IDENTIFIER
        EXTENSION_RECEIVER -> HighlightTheme.EXTENSION_RECEIVER
        ANNOTATION -> HighlightTheme.ANNOTATION
        VALUE_ARGUMENT_NAME -> HighlightTheme.VALUE_ARGUMENT_NAME
        NAME_REFERENCE -> HighlightTheme.NAME_REFERENCE
        NUMERIC_LITERAL -> HighlightTheme.NUMERIC_LITERAL
        TOP_LEVEL -> HighlightTheme.TOP_LEVEL
        DEFAULT -> HighlightTheme.DEFAULT
        KEYWORD -> HighlightTheme.KEYWORD
    }
}