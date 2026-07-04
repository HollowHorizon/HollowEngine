package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.text.TextColor
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.inlineWidgetMetrics
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.text.caretIndexAt
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import ru.hollowhorizon.hollowengine.generated.Assets

internal class HollowIdeEditorOverlays(
    private val surface: HollowUiRuntime,
) {
    private val completionPopups = mutableStateMapOf<String, HollowIdeCompletionPopupState>()
    private val diagnosticTooltips = mutableStateMapOf<String, HollowIdeDiagnosticTooltipState>()

    @Composable
    fun CompletionPopup(fileId: String) {
        val state = completionPopups[fileId]
        if (state == null || state.items.isEmpty()) return

        Column(
            tags = listOf("ide-completion-popup"),
            modifier = Modifier.position(state.x.px, state.y.px, 40f)
                .size(state.width.px, state.height.px)
        ) {
            LazyColumn(
                id = completionListId(fileId),
                tags = listOf("ide-completion-list"),
                modifier =
                    Modifier.size(100.percent, state.listHeight.px).scroll(vertical = true, horizontal = true),
            ) {
                if (state.firstIndex > 0) {
                    Box(modifier = Modifier.size(100.percent, (state.firstIndex * state.rowHeight).px))
                }
                state.items.forEach { item ->
                    CompletionRow(state, item)
                }
                val remaining = state.totalCount - state.firstIndex - state.items.size
                if (remaining > 0) {
                    Box(modifier = Modifier.size(100.percent, (remaining * state.rowHeight).px))
                }
            }
            Box(
                modifier = Modifier.size(UiLength.Fill, 1.px)
                    .background(parseColor("#31343D"))
            )
            Row(tags = listOf("ide-completion-hint")) {
                Text(
                    UiTextContent(
                        buildList {
                            this += UiTextSegment.Image(
                                UiBoundString(Assets.Hollowengine.Textures.Gui.Icons.COMPLETIONS.toString()),
                                16f,
                                16f
                            )

                            this += UiTextSegment.Text(
                                UiBoundString("to navigate.  "),
                                style = UiInlineStyle(listOf(TextColor(parseColor("#5F6677"))))
                            )

                            this += UiTextSegment.Text(
                                UiBoundString(" Enter "),
                                style = UiInlineStyle(listOf(TextColor(parseColor("#C4CBDA"))))
                            )

                            this += UiTextSegment.Text(
                                UiBoundString("or"),
                                style = UiInlineStyle(listOf(TextColor(parseColor("#5F6677"))))
                            )

                            this += UiTextSegment.Text(
                                UiBoundString(" Tab "),
                                style = UiInlineStyle(listOf(TextColor(parseColor("#C4CBDA"))))
                            )

                            this += UiTextSegment.Text(
                                UiBoundString("to insert."),
                                style = UiInlineStyle(listOf(TextColor(parseColor("#5F6677"))))
                            )
                        }
                    ),
                    modifier = Modifier.textWrap(false)
                )
            }
        }
    }

    @Composable
    fun DiagnosticTooltip(fileId: String) {
        val state = diagnosticTooltips[fileId] ?: return
        Box(
            mode = UiBoxMode.STACK,
            tags = listOf("ide-diagnostic-tooltip", state.severity.name.lowercase()),
            modifier = Modifier.position(state.x.px, state.y.px, 50f)
                .maxSize(state.maxWidth.px, UiLength.Auto),
        ) {
            Text(
                state.message,
                tags = listOf("ide-diagnostic-tooltip-message"),
                modifier = Modifier.maxSize(100.percent, UiLength.Auto),
            )
        }
    }

    @Composable
    private fun CompletionRow(state: HollowIdeCompletionPopupState, itemState: HollowIdeCompletionItemState) {
        val index = itemState.index
        val item = itemState.item
        Row(
            tags = listOf(
                "ide-completion-row",
                if (index == state.selectedIndex) "selected" else "idle",
            ),
            modifier = Modifier.size(100.percent, state.rowHeight.px)
                .input(clickable = true, hoverable = true)
                .onClick { event ->
                    if (state.node.acceptCompletion(index)) {
                        surface.saveState(state.node)
                    }
                    event.consume()
                }
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

    fun update(frame: HollowUiFrame, mouseX: Float, mouseY: Float): Boolean {
        val nextCompletionPopups = linkedMapOf<String, HollowIdeCompletionPopupState>()
        val nextDiagnosticTooltips = linkedMapOf<String, HollowIdeDiagnosticTooltipState>()
        frame.nodes.filterIsInstance<TextFieldNode>().forEach { node ->
            val editorId = node.id?.removePrefix("editor-") ?: return@forEach
            if (editorId == node.id) return@forEach
            val stackNode =
                frame.nodes.firstOrNull { it.id == "editor-stack-$editorId" } ?: return@forEach
            val layoutNode = frame.layout.nodes[node] ?: return@forEach
            val stackLayout = frame.layout.nodes[stackNode] ?: return@forEach
            val style = node.resolvedSnapshot
            val localOriginX = layoutNode.content.x - stackLayout.content.x
            val localOriginY = layoutNode.content.y - stackLayout.content.y

            textFieldCompletionPopupGeometry(node, style, layoutNode)?.let { geometry ->
                node.visibleCompletionItems(CompletionPopupWindowSize)
                val totalItems = node.completionItems
                val selectedIndex = node.completionSelectedIndex.coerceIn(0, totalItems.lastIndex)
                val listNode = frame.nodes.firstOrNull { it.id == completionListId(editorId) }
                val scrollOffset = listNode?.let { frame.layout.nodes[it]?.scrollOffset?.y } ?: 0f
                val previousSelectedIndex = completionPopups[editorId]?.selectedIndex
                val selectedChanged = previousSelectedIndex != null && previousSelectedIndex != selectedIndex
                val firstIndex = completionWindowStart(
                    totalCount = totalItems.size,
                    selectedIndex = selectedIndex,
                    selectedChanged = selectedChanged,
                    scrollOffset = scrollOffset,
                    rowHeight = geometry.rowHeight,
                    visibleRows = geometry.visibleRows,
                )
                if (selectedChanged && listNode != null) {
                    val currentScrollIndex = completionScrollIndex(
                        totalCount = totalItems.size,
                        scrollOffset = scrollOffset,
                        rowHeight = geometry.rowHeight,
                        visibleRows = geometry.visibleRows,
                    )
                    val targetScroll = completionSelectionScrollIndex(
                        totalCount = totalItems.size,
                        selectedIndex = selectedIndex,
                        scrollIndex = currentScrollIndex,
                        visibleRows = geometry.visibleRows,
                    ) * geometry.rowHeight
                    if (frame.layout.nodes[listNode]?.scrollOffset?.y != targetScroll) {
                        surface.setScrollImmediate(listNode, UiScrollOffset(y = targetScroll))
                    }
                }
                val items = totalItems
                    .asSequence()
                    .drop(firstIndex)
                    .take(CompletionPopupWindowSize)
                    .mapIndexed { offset, item -> HollowIdeCompletionItemState(firstIndex + offset, item) }
                    .toList()
                if (items.isNotEmpty()) {
                    nextCompletionPopups[editorId] = HollowIdeCompletionPopupState(
                        node = node,
                        items = items,
                        selectedIndex = selectedIndex,
                        firstIndex = firstIndex,
                        totalCount = totalItems.size,
                        x = localOriginX + geometry.x,
                        y = localOriginY + geometry.y,
                        globalX = layoutNode.content.x + geometry.x,
                        globalY = layoutNode.content.y + geometry.y,
                        width = geometry.width,
                        height = geometry.height,
                        listHeight = geometry.listHeight,
                        rowHeight = geometry.rowHeight,
                    )
                }
            }

            val inlayMetrics = layoutNode.inlineWidgetMetrics()
            diagnosticTooltipAtPointer(
                node,
                style,
                layoutNode,
                inlayMetrics,
                localOriginX,
                localOriginY,
                mouseX,
                mouseY
            )?.let {
                nextDiagnosticTooltips[editorId] = it
            }
        }
        val completionChanged = completionPopups.replaceWith(nextCompletionPopups)
        val tooltipChanged = diagnosticTooltips.replaceWith(nextDiagnosticTooltips)
        return completionChanged || tooltipChanged
    }

    private fun diagnosticTooltipAtPointer(
        node: TextFieldNode,
        style: UiComputedStyle,
        layoutNode: UiLayoutNode,
        inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics>,
        localOriginX: Float,
        localOriginY: Float,
        mouseX: Float,
        mouseY: Float,
    ): HollowIdeDiagnosticTooltipState? {
        if (node.diagnostics.isEmpty()) return null
        val local = layoutNode.inputTransform.inverse()?.transform(mouseX, mouseY, 0f) ?: return null
        val textOffset = textFieldTextOffset(node, style)
        val contentX = local.x - (layoutNode.content.x - layoutNode.rect.x) - textOffset + layoutNode.scrollOffset.x
        val contentY = local.y - (layoutNode.content.y - layoutNode.rect.y) + layoutNode.scrollOffset.y
        if (contentX < 0f || contentY < 0f) return null
        val editLayout = textFieldEditLayout(node, style, layoutNode, inlayWidgetMetrics)
        if (contentY > editLayout.height) return null
        val index = editLayout.caretIndexAt(contentX, contentY, style.fontSize, style.fontFamily)
        val diagnostic = node.diagnostics.firstOrNull { diagnostic ->
            val end = diagnostic.end.coerceAtLeast(diagnostic.start + 1)
            index in diagnostic.start until end
        } ?: return null
        val message = diagnostic.message.take(220)
        val maxWidth = (layoutNode.content.width - 12f).coerceIn(140f, 420f)
        val measured = UiTextLayouter.measure(
            text = message,
            availableWidth = maxWidth - DiagnosticTooltipHorizontalPadding,
            knownWidth = null,
            wrap = true,
            fontSize = DiagnosticTooltipFontSize,
        )
        val width = (measured.width + DiagnosticTooltipHorizontalPadding)
            .coerceIn(140f, maxWidth)
        val height = (measured.height + DiagnosticTooltipVerticalPadding)
            .coerceIn(DiagnosticTooltipMinHeight, DiagnosticTooltipMaxHeight)
        val x = (local.x - (layoutNode.content.x - layoutNode.rect.x) + 12f)
            .coerceIn(4f, (layoutNode.content.width - width - 4f).coerceAtLeast(4f))
        val y = (local.y - (layoutNode.content.y - layoutNode.rect.y) + 16f)
            .coerceIn(4f, (layoutNode.content.height - height - 4f).coerceAtLeast(4f))
        return HollowIdeDiagnosticTooltipState(
            message = message,
            severity = diagnostic.severity,
            x = localOriginX + x,
            y = localOriginY + y,
            maxWidth = maxWidth,
        )
    }
}


private data class HollowIdeCompletionPopupState(
    val node: TextFieldNode,
    val items: List<HollowIdeCompletionItemState>,
    val selectedIndex: Int,
    val firstIndex: Int,
    val totalCount: Int,
    val x: Float,
    val y: Float,
    val globalX: Float,
    val globalY: Float,
    val width: Float,
    val height: Float,
    val listHeight: Float,
    val rowHeight: Float,
) {
    fun contains(mouseX: Float, mouseY: Float): Boolean {
        return mouseX >= globalX && mouseX <= globalX + width && mouseY >= globalY && mouseY <= globalY + height
    }
}

private data class HollowIdeCompletionItemState(
    val index: Int,
    val item: UiTextCompletion,
)

private data class HollowIdeDiagnosticTooltipState(
    val message: String,
    val severity: UiTextDiagnosticSeverity,
    val x: Float,
    val y: Float,
    val maxWidth: Float,
)

private const val DiagnosticTooltipFontSize = 11f
private const val DiagnosticTooltipHorizontalPadding = 18f
private const val DiagnosticTooltipVerticalPadding = 10f
private const val DiagnosticTooltipMinHeight = 24f
private const val DiagnosticTooltipMaxHeight = 128f
private const val CompletionPopupWindowSize = 48
private const val CompletionPopupOverscanRows = 12

private fun completionListId(editorId: String) = "ide-completion-list-$editorId"

internal fun completionWindowStart(
    totalCount: Int,
    selectedIndex: Int,
    selectedChanged: Boolean,
    scrollOffset: Float,
    rowHeight: Float,
    visibleRows: Int,
): Int {
    if (totalCount <= CompletionPopupWindowSize) return 0
    val scrollIndex = completionScrollIndex(totalCount, scrollOffset, rowHeight, visibleRows)
    val virtualStart = if (selectedChanged) {
        completionSelectionScrollIndex(
            totalCount,
            selectedIndex,
            scrollIndex,
            visibleRows
        ) - CompletionPopupOverscanRows
    } else {
        scrollIndex - CompletionPopupOverscanRows
    }
    return virtualStart.coerceIn(0, totalCount - CompletionPopupWindowSize)
}

internal fun completionScrollIndex(
    totalCount: Int,
    scrollOffset: Float,
    rowHeight: Float,
    visibleRows: Int,
): Int {
    val rows = visibleRows.coerceAtLeast(1)
    val maxScrollIndex = (totalCount - rows).coerceAtLeast(0)
    return (scrollOffset / rowHeight.coerceAtLeast(1f)).toInt().coerceIn(0, maxScrollIndex)
}

internal fun completionSelectionScrollIndex(
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

private fun <K, V> MutableMap<K, V>.replaceWith(next: Map<K, V>): Boolean {
    var changed = false
    keys.toList().forEach { key ->
        if (key !in next) {
            remove(key)
            changed = true
        }
    }
    next.forEach { (key, value) ->
        if (this[key] != value) {
            this[key] = value
            changed = true
        }
    }
    return changed
}
