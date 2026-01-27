package ru.hollowhorizon.hollowengine.client.gui.markdown.util

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.CompositeASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.entities.EntityConverter

internal fun ASTNode.findChildOfTypeRecursive(type: IElementType): ASTNode? {
    children.forEach {
        if (it.type == type) {
            return it
        } else {
            val found = it.findChildOfTypeRecursive(type)
            if (found != null) {
                return found
            }
        }
    }
    return null
}

internal fun List<ASTNode>.innerList(): List<ASTNode> = this.subList(1, this.size - 1)

fun ASTNode.getUnescapedTextInNode(allFileText: CharSequence): String {
    val escapedText = getTextInNode(allFileText).toString()
    return EntityConverter.replaceEntities(
        escapedText,
        processEntities = false,
        processEscapes = true
    )
}

fun List<ASTNode>.mapAutoLinkToType(targetType: IElementType = MarkdownTokenTypes.TEXT): List<ASTNode> {
    return map {
        if (it is LeafASTNode) {
            if (it.type == GFMTokenTypes.GFM_AUTOLINK || it.type == MarkdownElementTypes.AUTOLINK) {
                LeafASTNode(targetType, it.startOffset, it.endOffset)
            } else {
                it
            }
        } else if (it is CompositeASTNode) {
            CompositeASTNode(it.type, it.children.mapAutoLinkToType(targetType))
        } else {
            it
        }
    }
}