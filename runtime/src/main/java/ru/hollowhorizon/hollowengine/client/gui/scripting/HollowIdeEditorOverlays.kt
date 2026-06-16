package ru.hollowhorizon.hollowengine.client.gui.scripting

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import ru.hollowhorizon.hollowengine.client.ui.*

internal class HollowIdeEditorOverlays(
    private val input: HollowUiInputController,
    private val invalidateUi: () -> Unit,
) {
    private val completionPopups = mutableStateMapOf<String, HollowIdeCompletionPopupState>()
    private val diagnosticTooltips = mutableStateMapOf<String, HollowIdeDiagnosticTooltipState>()

    @Composable
    fun CompletionPopup(fileId: String) {
        val state = completionPopups[fileId]
        if (state == null || state.items.isEmpty()) return
        Box(
            tags = listOf("ide-completion-popup"),
            modifier = Modifier.then(
                Modifier.position(state.x.px, state.y.px, 40f),
                Modifier.size(state.width.px, state.height.px),
            ),
        ) {
            LazyColumn(
                id = "ide-completion-list-$fileId",
                tags = listOf("ide-completion-list"),
                modifier = Modifier.then(
                    Modifier.size(100.percent, 100.percent),
                    Modifier.input(scrollable = true),
                ),
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
        }
    }

    @Composable
    fun DiagnosticTooltip(fileId: String) {
        val state = diagnosticTooltips[fileId] ?: return
        Box(
            tags = listOf("ide-diagnostic-tooltip", state.severity.name.lowercase()),
            modifier = Modifier.then(
                Modifier.position(state.x.px, state.y.px, 50f),
                Modifier.size(state.width.px, state.height.px),
            ),
        ) {
            Text(state.message, tags = listOf("ide-diagnostic-tooltip-message"))
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
            modifier = Modifier.then(
                Modifier.size(100.percent, state.rowHeight.px),
                Modifier.input(clickable = true, hoverable = true),
                Modifier.onEnter { event ->
                    if (state.node.selectCompletion(index)) invalidateUi()
                    event.consume()
                },
                Modifier.onHover { event ->
                    if (state.node.selectCompletion(index)) invalidateUi()
                    event.consume()
                },
                Modifier.onClick { event ->
                    if (state.node.acceptCompletion(index)) {
                        input.saveState(state.node)
                        invalidateUi()
                    }
                    event.consume()
                },
            ),
        ) {
            item.icon?.let { icon ->
                Image(icon, tags = listOf("ide-completion-icon"))
            }
            Text(item.label, tags = listOf("ide-completion-label"))
            if (item.detail.isNotBlank()) {
                Text(item.detail, tags = listOf("ide-completion-detail"))
            }
            Box(modifier = Modifier.then(Modifier.size(0.px, 100.percent), Modifier.grow(1f)))
            if (item.tail.isNotBlank()) {
                Text(item.tail, tags = listOf("ide-completion-tail"))
            }
        }
    }

    fun update(frame: HollowUiFrame, mouseX: Float, mouseY: Float): Boolean {
        val nextCompletionPopups = linkedMapOf<String, HollowIdeCompletionPopupState>()
        val nextDiagnosticTooltips = linkedMapOf<String, HollowIdeDiagnosticTooltipState>()
        frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().forEach { node ->
            val editorId = node.id?.removePrefix("editor-") ?: return@forEach
            if (editorId == node.id) return@forEach
            val stackNode = frame.resolved.styles.keys.firstOrNull { it.id == "editor-stack-$editorId" } ?: return@forEach
            val layoutNode = frame.layout.nodes[node] ?: return@forEach
            val stackLayout = frame.layout.nodes[stackNode] ?: return@forEach
            val style = frame.resolved[node]
            val localOriginX = layoutNode.content.x - stackLayout.content.x
            val localOriginY = layoutNode.content.y - stackLayout.content.y

            textFieldCompletionPopupGeometry(node, style, layoutNode)?.let { geometry ->
                node.visibleCompletionItems(CompletionPopupWindowSize)
                val totalItems = node.completionItems
                val selectedIndex = node.completionSelectedIndex.coerceIn(0, totalItems.lastIndex)
                val scrollOffset = frame.resolved.styles.keys
                    .firstOrNull { it.id == "ide-completion-list-$editorId" }
                    ?.let { frame.layout.nodes[it]?.scrollOffset?.y }
                    ?: 0f
                val firstIndex = completionWindowStart(
                    totalCount = totalItems.size,
                    selectedIndex = selectedIndex,
                    scrollOffset = scrollOffset,
                    rowHeight = geometry.rowHeight,
                )
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
                        width = geometry.width,
                        height = geometry.height,
                        rowHeight = geometry.rowHeight,
                    )
                }
            }

            val inlayMetrics = textFieldInlineWidgetMetrics(node, frame.layout.nodes)
            diagnosticTooltipAtPointer(node, style, layoutNode, inlayMetrics, localOriginX, localOriginY, mouseX, mouseY)?.let {
                nextDiagnosticTooltips[editorId] = it
            }
        }
        val completionChanged = completionPopups.replaceWith(nextCompletionPopups)
        val tooltipChanged = diagnosticTooltips.replaceWith(nextDiagnosticTooltips)
        return completionChanged || tooltipChanged
    }

    fun needsPointerUpdate(frame: HollowUiFrame): Boolean {
        return frame.resolved.styles.keys
            .filterIsInstance<TextFieldNode>()
            .any { node -> node.diagnostics.isNotEmpty() }
    }

    private fun diagnosticTooltipAtPointer(
        node: TextFieldNode,
        style: ComputedStyle,
        layoutNode: UiLayoutNode,
        inlayWidgetMetrics: Map<String, UiInlineWidgetMetrics>,
        localOriginX: Float,
        localOriginY: Float,
        mouseX: Float,
        mouseY: Float,
    ): HollowIdeDiagnosticTooltipState? {
        if (node.diagnostics.isEmpty()) return null
        val local = layoutNode.inputTransform.inverse()?.transform(mouseX, mouseY, 0f) ?: return null
        val textOffset = textFieldTextOffset(node, style, layoutNode)
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
        val maxWidth = layoutNode.content.width.coerceIn(140f, 360f)
        val width = (message.longestWordWidth() + 22f)
            .coerceIn(140f, maxWidth)
        val height = (message.estimatedWrappedLineCount(width - 18f) * DiagnosticTooltipLineHeight + 10f)
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
            width = width,
            height = height,
        )
    }
}

private fun textFieldInlineWidgetMetrics(
    node: TextFieldNode,
    layouts: Map<UiNode, UiLayoutNode>,
): Map<String, UiInlineWidgetMetrics> {
    return node.children.mapNotNull { child ->
        val id = child.id ?: return@mapNotNull null
        val rect = layouts[child]?.rect ?: return@mapNotNull null
        id to UiInlineWidgetMetrics(rect.width, rect.height)
    }.toMap()
}

private data class HollowIdeCompletionPopupState(
    val node: TextFieldNode,
    val items: List<HollowIdeCompletionItemState>,
    val selectedIndex: Int,
    val firstIndex: Int,
    val totalCount: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rowHeight: Float,
)

private data class HollowIdeCompletionItemState(
    val index: Int,
    val item: UiTextCompletion,
)

private data class HollowIdeDiagnosticTooltipState(
    val message: String,
    val severity: UiTextDiagnosticSeverity,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private const val DiagnosticTooltipCharacterWidth = 6f
private const val DiagnosticTooltipLineHeight = 13f
private const val DiagnosticTooltipMinHeight = 24f
private const val DiagnosticTooltipMaxHeight = 92f
private const val CompletionPopupWindowSize = 48

private fun String.longestWordWidth(): Float {
    return splitToSequence(' ', '\n', '\t')
        .maxOfOrNull { word -> word.length * DiagnosticTooltipCharacterWidth }
        ?: 0f
}

private fun String.estimatedWrappedLineCount(width: Float): Int {
    val charactersPerLine = (width / DiagnosticTooltipCharacterWidth).toInt().coerceAtLeast(1)
    var lines = 1
    var lineLength = 0
    split(' ', '\n', '\t').forEach { word ->
        if (word.isEmpty()) return@forEach
        val extra = if (lineLength == 0) word.length else word.length + 1
        if (lineLength > 0 && lineLength + extra > charactersPerLine) {
            lines++
            lineLength = word.length
        } else {
            lineLength += extra
        }
    }
    return lines
}

private fun completionWindowStart(
    totalCount: Int,
    selectedIndex: Int,
    scrollOffset: Float,
    rowHeight: Float,
): Int {
    if (totalCount <= CompletionPopupWindowSize) return 0
    val scrollIndex = (scrollOffset / rowHeight.coerceAtLeast(1f)).toInt()
        .coerceIn(0, totalCount - CompletionPopupWindowSize)
    if (selectedIndex in scrollIndex until scrollIndex + CompletionPopupWindowSize) return scrollIndex
    val centered = selectedIndex - CompletionPopupWindowSize / 2
    return centered.coerceIn(0, totalCount - CompletionPopupWindowSize)
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
