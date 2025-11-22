package ru.hollowhorizon.hollowengine.common.scripting.ide

import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.modules.ui2.TextLine as KoolLine

interface ScriptingAnalyzer {
    fun highlight(name: String, text: String, offset: Int): List<TextLine>
    fun diagnostic(name: String, text: String): List<Diagnostic>
}

data class TextLine(val spans: List<Pair<String, SpanStyle>>) {
    fun toKool(font: MsdfFont): KoolLine {
        return KoolLine(spans.map { it.first to it.second.toKool(font) })
    }
}

data class SpanStyle(
    val color: SymbolColor,
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