package ru.hollowhorizon.hollowengine.client.ui.render

import ru.hollowhorizon.hollowengine.client.ui.DrawTextCommand
import ru.hollowhorizon.hollowengine.client.ui.UiInlineImageRun
import ru.hollowhorizon.hollowengine.client.ui.UiInlineStyle
import ru.hollowhorizon.hollowengine.client.ui.UiInlineWidgetRun
import ru.hollowhorizon.hollowengine.client.ui.UiTextFragment
import ru.hollowhorizon.hollowengine.client.ui.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.UiTextLine
import ru.hollowhorizon.hollowengine.client.ui.UiTextRun
import ru.hollowhorizon.hollowengine.client.ui.UiTextSpaceRun
import ru.hollowhorizon.hollowengine.client.ui.fontFamily

internal object UiTextOverflowResolver {
    private const val ELLIPSIS = "…"

    fun ellipsizeLine(command: DrawTextCommand, line: UiTextLine): UiTextLine {
        val availableWidth = (command.rect.width - line.x).coerceAtLeast(0f)
        if (line.naturalWidth <= availableWidth + 0.01f) return line
        if (availableWidth <= 0f) return line.copy(text = "", width = 0f, naturalWidth = 0f, fragments = emptyList())

        val result = EllipsisBuilder(command, availableWidth)
        for (fragment in line.fragments.ifEmpty { listOf(line.asTextRun(command)) }) {
            if (!result.append(fragment)) return result.toLine(line)
        }

        result.appendEllipsis()
        return result.toLine(line)
    }

    private class EllipsisBuilder(
        private val command: DrawTextCommand,
        private val availableWidth: Float,
    ) {
        private val fragments = mutableListOf<UiTextFragment>()
        private val text = StringBuilder()
        private var cursorX = 0f
        private var ellipsisStyle = UiInlineStyle()
        private var ellipsisHeight = command.fontSize

        fun append(fragment: UiTextFragment): Boolean {
            return when (fragment) {
                is UiTextRun -> appendText(fragment)
                is UiTextSpaceRun -> appendFixed(fragment, fragment.width) { it.copy(x = cursorX) }.also {
                    if (it) text.append(' ')
                }
                is UiInlineImageRun -> appendFixed(fragment, fragment.width) { it.copy(x = cursorX) }
                is UiInlineWidgetRun -> appendFixed(fragment, fragment.width) { it.copy(x = cursorX) }
            }
        }

        fun appendEllipsis() {
            val width = ellipsisWidth(ellipsisStyle)
            if (width > availableWidth + 0.01f) return
            fragments += UiTextRun(
                text = ELLIPSIS,
                style = ellipsisStyle,
                x = cursorX.coerceAtMost((availableWidth - width).coerceAtLeast(0f)),
                y = 0f,
                width = width,
                height = ellipsisHeight,
            )
            text.append(ELLIPSIS)
        }

        fun toLine(line: UiTextLine): UiTextLine {
            return line.copy(text = text.toString(), width = availableWidth, naturalWidth = availableWidth, fragments = fragments)
        }

        private fun appendText(fragment: UiTextRun): Boolean {
            ellipsisStyle = fragment.style
            ellipsisHeight = fragment.height
            val maxRunWidth = (availableWidth - cursorX - ellipsisWidth(fragment.style)).coerceAtLeast(0f)
            if (fragment.width <= maxRunWidth + 0.01f) {
                fragments += fragment.copy(x = cursorX)
                text.append(fragment.text)
                cursorX += fragment.width
                return true
            }

            val prefix = fittingPrefix(fragment.text, maxRunWidth, fragment.style)
            if (prefix.isNotEmpty()) {
                val prefixWidth = textWidth(prefix, fragment.style)
                fragments += fragment.copy(text = prefix, x = cursorX, width = prefixWidth)
                text.append(prefix)
                cursorX += prefixWidth
            }
            appendEllipsis()
            return false
        }

        private fun <T : UiTextFragment> appendFixed(
            fragment: T,
            width: Float,
            copyAtCursor: (T) -> UiTextFragment,
        ): Boolean {
            if (cursorX + width + ellipsisWidth(ellipsisStyle) > availableWidth + 0.01f) {
                appendEllipsis()
                return false
            }
            fragments += copyAtCursor(fragment)
            cursorX += width
            return true
        }

        private fun fittingPrefix(text: String, maxWidth: Float, style: UiInlineStyle): String {
            if (maxWidth <= 0f || text.isEmpty()) return ""
            var low = 0
            var high = text.length
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (textWidth(text.substring(0, mid), style) <= maxWidth + 0.01f) low = mid else high = mid - 1
            }
            return text.substring(0, low)
        }

        private fun ellipsisWidth(style: UiInlineStyle): Float = textWidth(ELLIPSIS, style)

        private fun textWidth(text: String, style: UiInlineStyle): Float {
            return UiTextLayouter.measureTextWidth(
                text,
                style.resolvedFontSize(command.fontSize),
                style.fontFamily ?: command.fontFamily,
            )
        }
    }
}

private fun UiTextLine.asTextRun(command: DrawTextCommand): UiTextRun {
    return UiTextRun(
        text = text,
        style = UiInlineStyle(),
        x = 0f,
        y = 0f,
        width = naturalWidth,
        height = command.fontSize,
    )
}
