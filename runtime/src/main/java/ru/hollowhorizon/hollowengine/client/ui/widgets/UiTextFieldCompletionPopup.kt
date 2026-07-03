package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.caretPosition
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.style.*
import kotlin.math.max

internal data class UiTextFieldCompletionPopupGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val listHeight: Float,
    val rowHeight: Float,
    val itemCount: Int,
    val visibleRows: Int,
) {
    fun rowAt(localX: Float, localY: Float): Int? {
        if (localX < x || localX > x + width || localY < y || localY > y + height) return null
        if (localY > y + listHeight) return null
        val index = ((localY - y - CompletionPopupVerticalPadding) / rowHeight).toInt()
        return index.takeIf { it in 0 until itemCount }
    }
}

internal fun textFieldCompletionPopupGeometry(
    node: TextFieldNode,
    style: UiComputedStyle,
    layoutNode: UiLayoutNode,
): UiTextFieldCompletionPopupGeometry? {
    val items = node.visibleCompletionItems(CompletionPopupMeasuredItems)
    if (items.isEmpty()) return null
    val fontSize = style.fontSize
    val rowHeight = (fontSize + 6f).coerceAtLeast(14f)
    val itemCount = node.completionItems.size.coerceAtLeast(items.size)
    val visibleRows = itemCount.coerceAtMost(TextFieldCompletionPopupMaxItems)
    val listHeight = rowHeight * visibleRows + CompletionPopupVerticalPadding * 2f
    val popupHeight = listHeight + rowHeight + 1f
    val labelWidth = items.maxOfOrNull { item ->
        val detail = if (item.detail.isBlank()) "" else item.detail
        val tail = if (item.tail.isBlank()) "" else item.tail
        (item.label.length + detail.length + tail.length) * fontSize * 0.56f
    } ?: 0f
    val popupWidth = (labelWidth + 52f).coerceIn(160f, max(160f, layoutNode.content.width - 8f))
    val editLayout = textFieldEditLayout(node, style, layoutNode)
    val textOffset = textFieldTextOffset(node, style)
    val caret = editLayout.caretPosition(node.completionAnchor, fontSize, style.fontFamily)
    val preferredX = textOffset + caret.x - layoutNode.scrollOffset.x
    val popupX = preferredX.coerceIn(4f, (layoutNode.content.width - popupWidth - 4f).coerceAtLeast(4f))
    val belowY = caret.y + fontSize - layoutNode.scrollOffset.y + 4f
    val aboveY = caret.y - layoutNode.scrollOffset.y - popupHeight - 4f
    val popupY = if (belowY + popupHeight <= layoutNode.content.height) {
        belowY
    } else {
        aboveY.coerceAtLeast(4f)
    }
    return UiTextFieldCompletionPopupGeometry(
        x = popupX,
        y = popupY,
        width = popupWidth,
        height = popupHeight,
        listHeight = listHeight,
        rowHeight = rowHeight,
        itemCount = itemCount,
        visibleRows = visibleRows,
    )
}

private const val CompletionPopupVerticalPadding = 3f
private const val CompletionPopupMeasuredItems = 128
