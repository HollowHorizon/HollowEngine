package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.FlowScope
import de.fabmax.kool.modules.ui2.UiScope
import org.intellij.markdown.ast.ASTNode
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownStyle
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAttributes

fun UiScope.MarkdownText(
    node: ASTNode,
    content: String,
    style: MarkdownStyle,
) {
    val spans = with(content) {
        with(style) {
            buildMarkdownSpans(node)
        }
    }

    FlowText(spans) {}
}

fun UiScope.MarkdownText(text: String, style: MarkdownStyle, body: FlowScope.() -> Unit = {}) {
    FlowText(listOf(text to TextAttributes(style.bodyFont, ColorTheme.UI.WhiteReplacement))) {
        body()
    }
}