package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAttributes

fun UiScope.MarkdownHeader(
    node: ASTNode,
    source: String,
    font: MsdfFont,
    color: Color,
) {
    val text = node.getTextInNode(source).toString().trim { it == '#' || it.isWhitespace() }
    val spans = listOf(text to TextAttributes(font, color))

    FlowText(spans) {}
}