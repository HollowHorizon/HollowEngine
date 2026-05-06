package ru.hollowhorizon.hollowengine.common.scripting.ide

import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.InlayHint
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAttributes
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextLine as KoolLine

interface ScriptingAnalyzer {
    fun highlight(name: String, text: String, offset: Int): List<TextLine>
    fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(listOf(line to SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)), ArrayList())
    }

    fun completions(name: String, text: String, offset: Int): List<CompletionItem>
    fun diagnostic(name: String, text: String): List<Diagnostic>
}

data class TextLine(val spans: List<Pair<String, SpanStyle>>, val hints: ArrayList<InlayHint>) {


    fun toKool(font: MsdfFont): KoolLine {
        return KoolLine(spans.map { it.first to it.second.toKool(font) }, hints)
    }
}

data class SpanStyle(
    val color: TokenType,
    val italic: Boolean,
    val bold: Boolean,
    val highlight: Boolean,
) {
    fun toKool(font: MsdfFont): TextAttributes {
        return TextAttributes(
            font.copy(
                font.sizePts,
                if (italic) MsdfFont.ITALIC_STD else MsdfFont.ITALIC_NONE,
                if (bold) MsdfFont.WEIGHT_BOLD else MsdfFont.WEIGHT_REGULAR,
                font.cutoff,
                font.glowColor
            ),
            color.toKool(),
            if(highlight) color.toKool().withAlpha(0.33f) else null
        )
    }
}
