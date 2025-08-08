package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.clamp

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

    private fun Int.clamp(min: Int, max: Int) = when {
        this < min -> min
        this > max -> max
        else -> this
    }

    fun startOfExpression(text: String, caretPos: Int): Int {
        if (text.isEmpty()) return 0
        var i = caretPos.clamp(0, text.lastIndex)
        val currentCategory = text[i].category()
        while (i > 0 && text[i - 1].category() == currentCategory) i--
        return i
    }

    fun endOfExpression(text: String, caretPos: Int): Int {
        if (text.isEmpty()) return 0
        var i = caretPos.clamp(0, text.lastIndex)
        val currentCategory = text[i].category()
        while (i < text.lastIndex && text[i + 1].category() == currentCategory) i++
        return i + 1
    }

    fun moveExpressionLeft(text: String, caretPos: Int): Int {
        var i = (caretPos - 1).clamp(0, text.lastIndex)
        if (i == 0) return 0
        val category = text[i].category()
        while (i > 0 && text[i - 1].category() == category) i--
        return i
    }

    fun moveExpressionRight(text: String, caretPos: Int): Int {
        var i = caretPos.clamp(0, text.length)
        if (i >= text.length) return text.length
        val category = text[i].category()
        while (i < text.length && text[i].category() == category) i++
        return i
    }

}