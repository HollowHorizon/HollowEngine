package ru.hollowhorizon.hollowengine.common.scripting.ide

import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.TextSpan

fun buildTextLines(text: String, spans: List<TextSpan>, hints: List<InlayHint>): List<TextLine> {
    val hintsByOffset = hints.groupBy { it.index }
    val lines = text.split('\n')
    val result = ArrayList<TextLine>(lines.size)
    var lineStart = 0
    var spanIndex = 0
    for (line in lines) {
        val lineEnd = lineStart + line.length
        val pieces = ArrayList<Pair<String, SpanStyle>>()
        var cursor = lineStart
        while (spanIndex < spans.size && spans[spanIndex].start < lineEnd) {
            val span = spans[spanIndex]
            val start = maxOf(span.start, cursor)
            val end = minOf(span.end, lineEnd)
            if (end > start) {
                if (start > cursor) pieces += text.substring(cursor, start) to DefaultStyle
                pieces += text.substring(start, end) to span.style()
                cursor = end
            }
            if (span.end <= lineEnd) spanIndex++ else break
        }
        if (cursor < lineEnd) pieces += text.substring(cursor, lineEnd) to DefaultStyle
        result += TextLine(pieces, lineHints(hintsByOffset, lineStart, lineEnd))
        lineStart = lineEnd + 1
    }
    return result
}

private fun lineHints(
    hintsByOffset: Map<Int, List<InlayHint>>,
    lineStart: Int,
    lineEnd: Int,
): ArrayList<InlayHint> {
    val hints = ArrayList<InlayHint>()
    if (hintsByOffset.isEmpty()) return hints
    for (offset in lineStart..lineEnd) {
        hintsByOffset[offset]?.forEach { hints += it.copy(index = offset - lineStart) }
    }
    return hints
}

private fun TextSpan.style(): SpanStyle = SpanStyle(type, italic = italic, bold = false, highlight = false)

private val DefaultStyle = SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)
