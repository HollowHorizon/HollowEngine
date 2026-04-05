package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownStyle
import ru.hollowhorizon.hollowengine.client.gui.markdown.annotator.TextLineBuilder
import ru.hollowhorizon.hollowengine.client.gui.markdown.annotator.buildTextLine
import ru.hollowhorizon.hollowengine.client.gui.markdown.util.findChildOfTypeRecursive
import ru.hollowhorizon.hollowengine.client.gui.markdown.util.getUnescapedTextInNode
import ru.hollowhorizon.hollowengine.client.gui.markdown.util.innerList
import ru.hollowhorizon.hollowengine.client.gui.markdown.util.mapAutoLinkToType

context(content: String, style: MarkdownStyle)
fun buildMarkdownSpans(node: ASTNode) = buildTextLine {
    buildMarkdownSpans(node)
}


context(content: String, style: MarkdownStyle)
fun TextLineBuilder.buildMarkdownSpans(node: ASTNode) = buildMarkdownSpans(node.children)

context(content: String, style: MarkdownStyle)
fun TextLineBuilder.buildMarkdownSpans(children: List<ASTNode>) {
    var skipIfNext: Any? = null
    children.forEach { child ->
        if (skipIfNext == null || skipIfNext != child.type) {
            val parentType = child.parent?.type

            when (child.type) {
                MarkdownElementTypes.PARAGRAPH -> buildMarkdownSpans(child)
                MarkdownElementTypes.IMAGE -> child.findChildOfTypeRecursive(MarkdownElementTypes.LINK_DESTINATION)
                    ?.let {
                        // TODO: Images support
                    }

                MarkdownElementTypes.EMPH -> {
                    pushStyle(italic = true)
                    buildMarkdownSpans(child)
                    pop()
                }

                MarkdownElementTypes.STRONG -> {
                    pushStyle(bold = true)
                    buildMarkdownSpans(child)
                    pop()
                }
                // TODO: GFMElementTypes.STRIKETHROUGH -> {}
                MarkdownElementTypes.CODE_SPAN -> {
                    pushStyle(bgColor = style.codeBackgroundColor)
                    append(' ')
                    buildMarkdownSpans(child.children.innerList())
                    append(' ')
                    pop()
                }

                MarkdownElementTypes.AUTOLINK -> appendAutoLink(child)
                MarkdownElementTypes.INLINE_LINK -> appendMarkdownLink(child)
                // TODO: MarkdownElementTypes.SHORT_REFERENCE_LINK -> {}
                // TODO: MarkdownElementTypes.FULL_REFERENCE_LINK -> {}

                MarkdownTokenTypes.TEXT -> append(child.getUnescapedTextInNode(content))
                GFMTokenTypes.GFM_AUTOLINK -> if (child.parent == MarkdownElementTypes.LINK_TEXT) {
                    append(child.getUnescapedTextInNode(content))
                } else appendAutoLink(child)
                GFMTokenTypes.DOLLAR -> append('$')
                MarkdownTokenTypes.SINGLE_QUOTE -> append('\'')
                MarkdownTokenTypes.DOUBLE_QUOTE -> append('\"')
                MarkdownTokenTypes.LPAREN -> append('(')
                MarkdownTokenTypes.RPAREN -> append(')')
                MarkdownTokenTypes.LBRACKET -> append('[')
                MarkdownTokenTypes.RBRACKET -> append(']')
                MarkdownTokenTypes.LT -> append('<')
                MarkdownTokenTypes.GT -> append('>')
                MarkdownTokenTypes.COLON -> append(':')
                MarkdownTokenTypes.EXCLAMATION_MARK -> append('!')
                MarkdownTokenTypes.BACKTICK -> append('`')
                MarkdownTokenTypes.HARD_LINE_BREAK -> {
                    append('\n')
                    skipIfNext = MarkdownTokenTypes.EOL
                }
                MarkdownTokenTypes.EMPH -> {
                    if (parentType != MarkdownElementTypes.EMPH && parentType != MarkdownElementTypes.STRONG) {
                        append(child.getTextInNode(content))
                    }
                }
                MarkdownTokenTypes.EOL -> append('\n')
                MarkdownTokenTypes.WHITE_SPACE -> if (length > 0) append(' ')
                MarkdownTokenTypes.BLOCK_QUOTE -> {
                    skipIfNext = MarkdownTokenTypes.WHITE_SPACE
                }

                else -> {
                    if (child.type.name == "~" && parentType != GFMElementTypes.STRIKETHROUGH) {
                        append(child.getTextInNode(content))
                    }
                }
            }
        } else {
            skipIfNext = null
        }
    }
}

context(content: String, style: MarkdownStyle)
fun TextLineBuilder.appendAutoLink(
    node: ASTNode,
) {
    val targetNode = node.children.firstOrNull {
        it.type.name == MarkdownElementTypes.AUTOLINK.name
    } ?: node
    val destination = targetNode.getUnescapedTextInNode(content)

    pushStyle(color = style.linkColor)
    // TODO: Click link?
    append(destination)
    pop()
}

context(content: String, style: MarkdownStyle)
fun TextLineBuilder.appendMarkdownLink(
    node: ASTNode,
) {
    val linkText = node.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.children?.innerList()
    if (linkText == null) {
        append(node.getUnescapedTextInNode(content))
        return
    }
    val text = linkText.firstOrNull()?.getUnescapedTextInNode(content)
    val destination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getUnescapedTextInNode(content)
    val linkLabel = node.findChildOfType(MarkdownElementTypes.LINK_LABEL)?.getUnescapedTextInNode(content)
    val annotation = destination ?: linkLabel

    if (annotation != null) {
        pushStyle(color = style.linkColor)
        buildMarkdownSpans(linkText.mapAutoLinkToType())
        pop()
    } else {
        buildMarkdownSpans(linkText)
    }
}

