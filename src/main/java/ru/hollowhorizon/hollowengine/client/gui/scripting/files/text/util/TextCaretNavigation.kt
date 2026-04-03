package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

object TextCaretNavigation {
    private enum class CharCategory {
        LETTER, DIGIT, WHITESPACE, PUNCTUATION, OTHER
    }

    private fun Char.category(): CharCategory = when {
        isLetter() -> CharCategory.LETTER
        isDigit() -> CharCategory.DIGIT
        isWhitespace() -> CharCategory.WHITESPACE
        isLetterOrDigit() -> CharCategory.LETTER // fallback
        else -> if (isJavaIdentifierPart()) CharCategory.LETTER else CharCategory.PUNCTUATION
    }

    fun isIdentifierChar(char: Char): Boolean = char == '_' || char.isJavaIdentifierPart()

    fun startOfIdentifier(text: String, caretPos: Int): Int {
        if (text.isEmpty()) return 0
        var i = caretPos.clamp(0, text.length)
        while (i > 0 && isIdentifierChar(text[i - 1])) i--
        return i
    }

    fun endOfIdentifier(text: String, caretPos: Int): Int {
        if (text.isEmpty()) return 0
        var i = caretPos.clamp(0, text.length)
        while (i < text.length && isIdentifierChar(text[i])) i++
        return i
    }

    private fun Int.clamp(min: Int, max: Int) = when {
        this < min -> min
        this > max -> max
        else -> this
    }

    fun startOfExpression(text: String, caretPos: Int): Int {
        if (text.isEmpty()) return 0
        val safeIdentifierIndex = (caretPos - 1).coerceIn(0, text.lastIndex)
        if (isIdentifierChar(text[safeIdentifierIndex])) {
            return startOfIdentifier(text, caretPos)
        }
        var i = caretPos.clamp(0, text.lastIndex)
        val currentCategory = text[i].category()
        while (i > 0 && text[i - 1].category() == currentCategory) i--
        return i
    }

    fun endOfExpression(text: String, caretPos: Int): Int {
        if (text.isEmpty()) return 0
        val safeIdentifierIndex = caretPos.coerceIn(0, text.lastIndex)
        if (isIdentifierChar(text[safeIdentifierIndex])) {
            return endOfIdentifier(text, caretPos)
        }
        var i = caretPos.clamp(0, text.lastIndex)
        val currentCategory = text[i].category()
        while (i < text.lastIndex && text[i + 1].category() == currentCategory) i++
        return i + 1
    }

    fun moveExpressionLeft(text: String, caretPos: Int): Int {
        val identifierIndex = (caretPos - 1).coerceIn(0, text.lastIndex)
        if (caretPos > 0 && isIdentifierChar(text[identifierIndex])) {
            return startOfIdentifier(text, caretPos)
        }
        var i = (caretPos - 1).clamp(0, text.lastIndex)
        if (i == 0) return 0
        val category = text[i].category()
        while (i > 0 && text[i - 1].category() == category) i--
        return i
    }

    fun moveExpressionRight(text: String, caretPos: Int): Int {
        var i = caretPos.clamp(0, text.length)
        if (i >= text.length) return text.length
        if (isIdentifierChar(text[i])) {
            return endOfIdentifier(text, i)
        }
        val category = text[i].category()
        while (i < text.length && text[i].category() == category) i++
        return i
    }

}
