package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.*
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.MarkdownTokenTypes.Companion.EOL
import org.intellij.markdown.ast.ASTNode
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.markdown.*

fun UiScope.MarkdownBlockQuote(
    content: String,
    node: ASTNode,
    style: MarkdownStyle,
) {
    val blockQuoteThickness = MarkdownDimens.blockQuoteThickness
    val blockQuote = MarkdownPadding.blockQuote
    val blockQuoteText = MarkdownPadding.blockQuoteText

    Row(Grow.Std) {
        Box {
            modifier.size(blockQuoteThickness, Grow.Std).padding(blockQuoteThickness)
                .backgroundColor(ColorTheme.Accents.Main)
        }
        Column(Grow.Std) {
            modifier.padding(blockQuote)

            var priorNestedQuote = false
            node.children.onEachIndexed { index, child ->
                if (child.type == MarkdownTokenTypes.BLOCK_QUOTE) {
                    // if block quote is nested, and comes after non block quote, add padding
                    if (!priorNestedQuote && index != 0) Box { modifier.height(blockQuoteText.bottom) }
                    MarkdownBlockQuote(content = content, node = child, style = style)
                    priorNestedQuote = true
                } else if (child.type == EOL) {
                    Box { modifier.height(Dp(style.bodyFont.sizePts)) }
                } else {
                    // if first item either completely, or after a nested quote, add top padding
                    if (index == 0 || priorNestedQuote) Box { modifier.height(blockQuoteText.top) }
                    priorNestedQuote = false
                    MarkdownElement(
                        node = child,
                        content = content,
                        style = style,
                        includeSpacer = false,
                    )
                    // if last item, add bottom padding
                    if (index == node.children.lastIndex) Box { modifier.height(blockQuoteText.bottom) }
                }
            }
        }
    }
}