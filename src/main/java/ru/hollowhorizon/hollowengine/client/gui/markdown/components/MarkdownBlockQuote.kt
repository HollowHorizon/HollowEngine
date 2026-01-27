package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.*
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes.Companion.EOL
import org.intellij.markdown.ast.ASTNode
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.markdown.*

fun UiScope.MarkdownBlockQuote(
    content: String,
    node: ASTNode,
    style: MarkdownStyle,
    availableWidth: Float,
) {
    val blockQuoteThickness = MarkdownDimens.blockQuoteThickness
    val blockQuote = MarkdownPadding.blockQuote
    val blockQuoteText = MarkdownPadding.blockQuoteText
    val blockQuoteBar = MarkdownPadding.blockQuoteBar

    Column {
        modifier
            .background(object : UiRenderer<UiNode> {
                override fun renderUi(node: UiNode) {
                    node.apply {

                        getPlainBuilder().withColor(ColorTheme.Accents.Main) {
                            line(
                                blockQuoteBar.left.px,
                                blockQuoteBar.top.px,
                                blockQuoteBar.right.px,
                                heightPx - blockQuoteBar.bottom.px,
                                blockQuoteThickness.px
                            )
                        }
                    }
                }
            })
            .padding(blockQuote)

        var priorNestedQuote = false
        node.children.onEachIndexed { index, child ->
            if (child.type == MarkdownElementTypes.BLOCK_QUOTE) {
                // if block quote is nested, and comes after non block quote, add padding
                if (!priorNestedQuote && index != 0) Box { modifier.height(blockQuoteText.bottom) }
                MarkdownBlockQuote(content = content, node = child, style = style, availableWidth = availableWidth)
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
                    availableWidth = availableWidth
                )
                // if last item, add bottom padding
                if (index == node.children.lastIndex) Box { modifier.height(blockQuoteText.bottom) }
            }
        }
    }
}