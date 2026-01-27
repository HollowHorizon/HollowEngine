package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import ru.hollowhorizon.hollowengine.client.gui.markdown.rememberTarget

fun UiScope.MarkdownHeader(
    node: ASTNode,
    source: String,
    font: MsdfFont,
    color: Color,
    maxWidth: Float,
) {
    val text = node.getTextInNode(source).toString().trim { it == '#' || it.isWhitespace() }
    val spans = listOf(text to TextAttributes(font, color))

    val visualLines = rememberTarget(node, maxWidth) {
        wrapText(spans, maxWidth)
    }

    Column(width = Grow.Std) {
        modifier.padding(top = Dp(16f), bottom = Dp(8f))
        visualLines.forEach { line ->
            AttributedText(line) {
                modifier.width(Grow.Std)
            }
        }
    }
}