package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.*
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownStyle
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.AttributedText
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextLine
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAttributes

fun UiScope.MarkdownCodeFence(
    content: String,
    node: ASTNode,
    style: MarkdownStyle,
    block: UiScope.(String, String?, MarkdownStyle) -> Unit = { code, language, style ->
        MarkdownCode(
            code = code,
            language = language ?: "txt",
            style = style
        )
    },
) {
    val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)?.toString()
    if (node.children.size >= 3) {
        val start = node.children[2].startOffset
        val minCodeFenceCount = if (language != null && node.children.size > 3) 3 else 2
        val end = node.children[(node.children.size - 2).coerceAtLeast(minCodeFenceCount)].endOffset
        block(content.subSequence(start, end).toString().replaceIndent(), language, style)
    } else {
        // invalid code block, skipping
    }
}

fun UiScope.MarkdownCodeBlock(
    content: String,
    node: ASTNode,
    style: MarkdownStyle,
    block: UiScope.(String, String?, MarkdownStyle) -> Unit = { code, language, style ->
        MarkdownCode(
            code = code,
            language = language ?: "txt",
            style = style
        )
    },
) {
    val start = node.children[0].startOffset
    val end = node.children[node.children.size - 1].endOffset
    val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(content)?.toString()
    block(content.subSequence(start, end).toString().replaceIndent(), language, style)
}

fun UiScope.MarkdownCode(code: String, language: String, style: MarkdownStyle) {
    Column(width = Grow.Std) {
        modifier.padding(Dimensions.PaddingMedium)
            .background(RoundRectBackground(style.codeBackgroundColor, Dimensions.PaddingMedium))

        code.lines()
            .map { ScriptTextLine(listOf(it to TextAttributes(style.codeFont, ColorTheme.UI.WhiteReplacement))) }
            .forEach { line ->
                AttributedText(line) {
                    modifier.width(Grow.Std)
                }
            }
    }
}