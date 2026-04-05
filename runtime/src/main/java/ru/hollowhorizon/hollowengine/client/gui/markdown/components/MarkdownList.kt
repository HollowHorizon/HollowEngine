package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.*
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownElementTypes.ORDERED_LIST
import org.intellij.markdown.MarkdownElementTypes.UNORDERED_LIST
import org.intellij.markdown.MarkdownTokenTypes.Companion.LIST_BULLET
import org.intellij.markdown.MarkdownTokenTypes.Companion.LIST_NUMBER
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CHECK_BOX
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownElement
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownPadding
import ru.hollowhorizon.hollowengine.client.gui.markdown.MarkdownStyle
import ru.hollowhorizon.hollowengine.client.gui.markdown.util.getUnescapedTextInNode

fun UiScope.MarkdownListItems(
    content: String,
    node: ASTNode,
    style: MarkdownStyle,
    depth: Int = 0,
    markerModifier: RowScope.() -> Unit = {},
    listModifier: RowScope.() -> Unit = {},
    bullet: UiScope.(index: Int, listNumber: Int, child: ASTNode?) -> Unit,
) {
    Column {
        modifier.padding(
            start = MarkdownPadding.listIndent * depth,
            top = MarkdownPadding.list,
            bottom = MarkdownPadding.list
        )

        val initialListNumber = node.findChildOfType(MarkdownElementTypes.LIST_ITEM)
            ?.getUnescapedTextInNode(content)
            ?.takeWhile(Char::isDigit)
            ?.toIntOrNull()
            ?: 1

        var index = 0
        node.children.forEach { child ->
            if (child.type == MarkdownElementTypes.LIST_ITEM) {
                MarkdownListItem(
                    content = content,
                    child = child,
                    node = node,
                    index = index,
                    listNumber = initialListNumber,
                    markerModifier = markerModifier,
                    listModifier = listModifier,
                    bullet = bullet,
                    style = style,
                )
                index++
            }
        }
    }
}

private fun UiScope.MarkdownListItem(
    content: String,
    child: ASTNode,
    node: ASTNode,
    style: MarkdownStyle,
    index: Int,
    listNumber: Int,
    markerModifier: RowScope.() -> Unit,
    listModifier: RowScope.() -> Unit,
    bullet: UiScope.(index: Int, listNumber: Int, child: ASTNode?) -> Unit,
) {
    val checkboxNode = child.children.getOrNull(1)?.takeIf { it.type == CHECK_BOX }
    val listIndicator = when (node.type) {
        ORDERED_LIST -> child.findChildOfType(LIST_NUMBER)
        UNORDERED_LIST -> child.findChildOfType(LIST_BULLET)
        else -> null
    }

    Row(Grow.Std) {
        modifier.padding(top = MarkdownPadding.listItemTop, bottom = MarkdownPadding.listItemBottom)

        // Render marker symbol (checkbox or bullet)
        Row {
            markerModifier()
            if (checkboxNode != null) {
                var checkbox by remember(false)
                Checkbox(checkbox) {
                    modifier.onToggle { checkbox = it }
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .colors(
                            ColorTheme.UI.BackgroundAccent,
                            ColorTheme.UI.BackgroundSecondary,
                            ColorTheme.UI.BackgroundElements,
                            ColorTheme.Accents.Main
                        )
                }
            } else {
                bullet(index, listNumber, listIndicator)
            }

            modifier.margin(end = MarkdownPadding.listItemTop)
        }

        // Render list item content
        Column {
            if (checkboxNode == null) modifier.padding(top = MarkdownPadding.listItemTop)
            listModifier()
            child.children.forEach { nestedChild ->
                MarkdownNestedListItem(
                    nestedChild = nestedChild,
                    content = content,
                    style = style,
                )
            }
        }
    }
}

private fun UiScope.MarkdownNestedListItem(
    nestedChild: ASTNode,
    content: String,
    style: MarkdownStyle,
) {
    when (nestedChild.type) {
        ORDERED_LIST -> {
            MarkdownOrderedList(content, nestedChild, style)
        }

        UNORDERED_LIST -> {
            MarkdownBulletList(content, nestedChild, style)
        }

        else -> {
            MarkdownElement(
                node = nestedChild,
                content = content,
                includeSpacer = false,
                style = style,
            )
        }
    }
}

fun UiScope.MarkdownOrderedList(
    content: String,
    node: ASTNode,
    style: MarkdownStyle,
    depth: Int = 0,
    markerModifier: RowScope.() -> Unit = {},
    listModifier: RowScope.() -> Unit = {},
) {
    MarkdownListItems(
        content,
        node,
        style,
        depth,
        markerModifier,
        listModifier
    ) { index, listNumber, child ->
        MarkdownText(
            text = LocalOrderedListHandler.transform(
                type = LIST_NUMBER,
                bullet = child?.getUnescapedTextInNode(content),
                index = index,
                listNumber = listNumber,
                depth = depth
            ),
            style = style,
        )
    }
}

fun UiScope.MarkdownBulletList(
    content: String,
    node: ASTNode,
    style: MarkdownStyle,
    depth: Int = 0,
    markerModifier: RowScope.() -> Unit = {},
    listModifier: RowScope.() -> Unit = {},
) {
    MarkdownListItems(
        content,
        node,
        style,
        depth,
        markerModifier,
        listModifier
    ) { index, listNumber, child ->
        MarkdownText(
            text = LocalBulletListHandler.transform(
                type = LIST_BULLET,
                bullet = child?.getUnescapedTextInNode(content),
                index = index,
                listNumber = listNumber,
                depth = depth
            ),
            style = style
        ) {
            modifier.padding(bottom = MarkdownPadding.listItemBottom)
        }
    }
}

fun interface BulletHandler {
    fun transform(type: IElementType, bullet: CharSequence?, index: Int, listNumber: Int, depth: Int): String
}

val LocalBulletListHandler = BulletHandler { _, _, _, _, _ -> "•" }
val LocalOrderedListHandler = BulletHandler { _, _, index, listNumber, _ -> "${listNumber + index}. " }
