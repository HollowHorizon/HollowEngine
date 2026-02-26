package ru.hollowhorizon.hollowengine.client.gui.markdown.annotator

import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAttributes
import java.util.*

class TextLineBuilder {
    val spans = mutableListOf<Pair<String, TextAttributes>>()
    private var style = TextAttributes(MsdfFont(ColorTheme.Fonts.MONOCRAFT, 16f), ColorTheme.UI.WhiteReplacement)
    private val attributes = Stack<TextAttributes>()

    fun pushStyle(
        color: Color = ColorTheme.UI.WhiteReplacement,
        bgColor: Color? = null,
        bold: Boolean = false,
        italic: Boolean = false,
    ) {
        style = attributes.push(
            style.copy(
                style.font.copy(
                    weight = if (bold) MsdfFont.WEIGHT_EXTRA_BOLD else MsdfFont.WEIGHT_REGULAR,
                    italic = if (italic) MsdfFont.ITALIC_STD else MsdfFont.ITALIC_NONE
                ),
                color,
                bgColor
            )
        )
    }

    fun pop() {
        style = attributes.pop()
    }

    fun append(text: CharSequence) = spans.add(text.toString() to style)
    fun append(char: Char) = spans.add(char.toString() to style)

    val length: Int get() = spans.sumOf { it.first.length }
}

fun buildTextLine(builder: TextLineBuilder.() -> Unit): List<Pair<String, TextAttributes>> {
    return TextLineBuilder().apply(builder).spans
}