package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.parseColor
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
import ru.hollowhorizon.hollowengine.generated.Assets
import kotlin.math.max

/** Completion popup placement in viewport coordinates (relative to the field's content box). */
internal data class EditableFieldCompletionGeometry(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val listHeight: Float,
    val rowHeight: Float,
    val visibleRows: Int,
)

internal fun editableFieldCompletionGeometry(
    layout: EditableFieldLayout,
    anchor: Int,
    items: List<UiTextCompletion>,
    scrollX: Float,
    scrollY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    caretOffsetX: Float = 0f,
): EditableFieldCompletionGeometry? {
    if (items.isEmpty() || viewportWidth <= 0f || viewportHeight <= 0f) return null
    val fontSize = layout.fontSize
    val fontFamily = layout.fontFamily
    val rowHeight = (fontSize + 6f).coerceAtLeast(14f)
    val visibleRows = items.size.coerceAtMost(CompletionPopupMaxVisibleRows)
    val listHeight = rowHeight * visibleRows + CompletionPopupVerticalPadding * 2f
    val height = listHeight + rowHeight + 1f
    val measured = items.asSequence().take(CompletionPopupMeasuredItems)
    val labelWidth = measured.maxOfOrNull { item ->
        var width = UiTextLayouter.measureTextWidth(item.label, fontSize, fontFamily)
        if (item.detail.isNotBlank()) {
            width += UiTextLayouter.measureTextWidth(item.detail, fontSize, fontFamily) + CompletionPopupTextGap
        }
        if (item.tail.isNotBlank()) {
            width += UiTextLayouter.measureTextWidth(item.tail, fontSize, fontFamily) + CompletionPopupTextGap
        }
        width
    } ?: 0f
    val width = (labelWidth + CompletionPopupRowChrome).coerceIn(160f, max(160f, viewportWidth - 8f))
    val caret = layout.caretAt(anchor)
    val caretX = caretOffsetX + caret.x - scrollX
    val caretY = caret.y - scrollY
    val x = caretX.coerceIn(4f, (viewportWidth - width - 4f).coerceAtLeast(4f))
    val belowY = caretY + fontSize + 4f
    val aboveY = caretY - height - 4f
    val y = if (belowY + height <= viewportHeight) belowY else aboveY.coerceAtLeast(4f)
    return EditableFieldCompletionGeometry(x, y, width, height, listHeight, rowHeight, visibleRows)
}

/**
 * The completion popup rendered inside the field's scroll container: positioned in viewport space
 * (compensating the field scroll), a windowed row list with its own scroll that follows the
 * keyboard selection, and the navigation hint footer.
 */
@Composable
internal fun EditableFieldCompletionPopup(
    completion: TextFieldCompletionState,
    layout: EditableFieldLayout,
    scrollState: UiScrollHandle,
    contentOffsetX: Float = 0f,
) {
    val items = completion.items
    val geometry = editableFieldCompletionGeometry(
        layout = layout,
        anchor = completion.anchor,
        items = items,
        scrollX = scrollState.offsetX,
        scrollY = scrollState.offsetY,
        viewportWidth = scrollState.viewport.width,
        viewportHeight = scrollState.viewport.height,
        caretOffsetX = contentOffsetX,
    ) ?: return

    val listScroll = rememberScrollState()
    val selectionFollow = remember { CompletionSelectionFollow() }
    val selectedIndex = completion.selectedIndex.coerceIn(0, items.lastIndex)
    val selectedChanged = selectionFollow.lastSelectedIndex != selectedIndex
    if (selectedChanged) {
        val scrollIndex = completionScrollRowIndex(items.size, listScroll.offsetY, geometry.rowHeight, geometry.visibleRows)
        val target = completionSelectionRowIndex(items.size, selectedIndex, scrollIndex, geometry.visibleRows) *
                geometry.rowHeight
        if (selectionFollow.lastSelectedIndex >= 0 && listScroll.offsetY != target) listScroll.scrollTo(y = target)
        selectionFollow.lastSelectedIndex = selectedIndex
    }
    val firstIndex = completionWindowStartIndex(
        totalCount = items.size,
        selectedIndex = selectedIndex,
        selectedChanged = selectedChanged,
        scrollOffset = listScroll.offsetY,
        rowHeight = geometry.rowHeight,
        visibleRows = geometry.visibleRows,
    )
    val windowed = items.asSequence()
        .drop(firstIndex)
        .take(CompletionPopupWindowSize)
        .toList()

    Column(
        tags = listOf("editable-text-field-completion-popup", "ide-completion-popup"),
        modifier = Modifier
            .position((geometry.x + scrollState.offsetX).px, (geometry.y + scrollState.offsetY).px, 40f)
            .size(geometry.width.px, geometry.height.px)
            .layer(30)
            .input(clickable = true, hoverable = true),
    ) {
        LazyColumn(
            tags = listOf("ide-completion-list"),
            modifier = Modifier.size(100.percent, geometry.listHeight.px)
                .scroll(vertical = true, horizontal = true, state = listScroll),
        ) {
            if (firstIndex > 0) {
                Box(modifier = Modifier.size(100.percent, (firstIndex * geometry.rowHeight).px))
            }
            windowed.forEachIndexed { offset, item ->
                CompletionPopupRow(
                    completion = completion,
                    index = firstIndex + offset,
                    item = item,
                    selected = firstIndex + offset == selectedIndex,
                    rowHeight = geometry.rowHeight,
                )
            }
            val remaining = items.size - firstIndex - windowed.size
            if (remaining > 0) {
                Box(modifier = Modifier.size(100.percent, (remaining * geometry.rowHeight).px))
            }
        }
        Box(
            modifier = Modifier.size(UiLength.Fill, 1.px)
                .background(parseColor("#31343D")),
        )
        Row(tags = listOf("ide-completion-hint")) {
            Text(modifier = Modifier.textWrap(false)) {
                Image(
                    Assets.Hollowengine.Textures.Gui.Icons.COMPLETIONS.toString(),
                    modifier = Modifier.size(16.px, 16.px),
                )
                Span("to navigate.  ", modifier = Modifier.foreground(parseColor("#5F6677")))
                Span(" Enter ", modifier = Modifier.foreground(parseColor("#C4CBDA")))
                Span("or", modifier = Modifier.foreground(parseColor("#5F6677")))
                Span(" Tab ", modifier = Modifier.foreground(parseColor("#C4CBDA")))
                Span("to insert.", modifier = Modifier.foreground(parseColor("#5F6677")))
            }
        }
    }
}

@Composable
private fun CompletionPopupRow(
    completion: TextFieldCompletionState,
    index: Int,
    item: UiTextCompletion,
    selected: Boolean,
    rowHeight: Float,
) {
    Row(
        tags = listOf("ide-completion-row", if (selected) "selected" else "idle"),
        modifier = Modifier.size(100.percent, rowHeight.px)
            .input(clickable = true, hoverable = true)
            .onClick { event ->
                completion.accept(index)
                event.consume()
            },
    ) {
        item.icon?.let { icon ->
            Image(icon, tags = listOf("ide-completion-icon"))
        }
        Text(item.label, tags = listOf("ide-completion-label"))
        if (item.detail.isNotBlank()) {
            Text(item.detail, tags = listOf("ide-completion-detail"))
        }
        Box(modifier = Modifier.size(100.percent, 100.percent).grow(1f))
        if (item.tail.isNotBlank()) {
            Text(item.tail, tags = listOf("ide-completion-tail"))
        }
    }
}

private class CompletionSelectionFollow {
    var lastSelectedIndex = -1
}

/** First row materialized by the windowed list (overscan rows above the scrolled/selected row). */
internal fun completionWindowStartIndex(
    totalCount: Int,
    selectedIndex: Int,
    selectedChanged: Boolean,
    scrollOffset: Float,
    rowHeight: Float,
    visibleRows: Int,
): Int {
    if (totalCount <= CompletionPopupWindowSize) return 0
    val scrollIndex = completionScrollRowIndex(totalCount, scrollOffset, rowHeight, visibleRows)
    val virtualStart = if (selectedChanged) {
        completionSelectionRowIndex(totalCount, selectedIndex, scrollIndex, visibleRows) - CompletionPopupOverscanRows
    } else {
        scrollIndex - CompletionPopupOverscanRows
    }
    return virtualStart.coerceIn(0, totalCount - CompletionPopupWindowSize)
}

/** The row index the list is currently scrolled to. */
internal fun completionScrollRowIndex(
    totalCount: Int,
    scrollOffset: Float,
    rowHeight: Float,
    visibleRows: Int,
): Int {
    val rows = visibleRows.coerceAtLeast(1)
    val maxScrollIndex = (totalCount - rows).coerceAtLeast(0)
    return (scrollOffset / rowHeight.coerceAtLeast(1f)).toInt().coerceIn(0, maxScrollIndex)
}

/** The scroll row that keeps [selectedIndex] visible, moving as little as possible. */
internal fun completionSelectionRowIndex(
    totalCount: Int,
    selectedIndex: Int,
    scrollIndex: Int,
    visibleRows: Int,
): Int {
    val rows = visibleRows.coerceAtLeast(1)
    val maxScrollIndex = (totalCount - rows).coerceAtLeast(0)
    return when {
        selectedIndex < scrollIndex -> selectedIndex
        selectedIndex >= scrollIndex + rows -> selectedIndex - rows + 1
        else -> scrollIndex
    }.coerceIn(0, maxScrollIndex)
}

private const val CompletionPopupMaxVisibleRows = 10
private const val CompletionPopupVerticalPadding = 3f
private const val CompletionPopupMeasuredItems = 128
private const val CompletionPopupWindowSize = 48
private const val CompletionPopupOverscanRows = 12
private const val CompletionPopupTextGap = 8f
private const val CompletionPopupRowChrome = 52f
