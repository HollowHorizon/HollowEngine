package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollbarGeometry
import ru.hollowhorizon.hollowengine.client.ui.style.ComputedStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlineWidgetMetrics

data class UiRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    fun contains(px: Float, py: Float): Boolean = px >= x && py >= y && px <= x + width && py <= y + height
}


data class UiLayoutNode(
    val node: UiNode,
    val rect: UiRect,
    val content: UiRect,
    val clip: UiRect?,
    val worldTransform: UiMatrix4,
    val inputTransform: UiMatrix4,
    val needsFramebuffer: Boolean,
    val scrollOffset: UiScrollOffset = UiScrollOffset.Zero,
    val scrollRange: UiScrollOffset = UiScrollOffset.Zero,
    val scrollArea: UiRect = content,
    val virtualContentBounds: UiRect? = null,
    val textLayout: UiTextLayout? = null,
    val scrollbars: List<UiScrollbarGeometry> = emptyList(),
)

data class UiLayoutResult(
    val root: UiNode,
    val nodes: Map<UiNode, UiLayoutNode>,
    val traversalOrder: List<UiNode> = nodes.keys.toList(),
    val popupNodes: List<PopupNode> = traversalOrder.filterIsInstance<PopupNode>(),
) {
    operator fun get(node: UiNode): UiLayoutNode = nodes.getValue(node)
}
internal fun UiLayoutNode.inlineWidgetMetrics(): Map<String, UiInlineWidgetMetrics> {
    val layout = textLayout ?: return emptyMap()
    return layout.lines
        .flatMap { line -> line.fragments.filterIsInstance<UiInlineWidgetRun>() }
        .associate { fragment -> fragment.widget.id to UiInlineWidgetMetrics(fragment.width, fragment.height) }
}


internal data class UiScrollbarReserve(
    val vertical: Boolean = false,
    val horizontal: Boolean = false,
) {
    val active: Boolean get() = vertical || horizontal

    companion object {
        val None = UiScrollbarReserve()
    }
}

internal data class LayoutSize(val width: Float, val height: Float)

internal data class MeasuredChild(
    val node: UiNode,
    val style: ComputedStyle,
    val size: LayoutSize,
    val margin: ResolvedUiInsets,
    val parentData: ParentData = emptyMap(),
)

internal data class NodeBoxes(
    val scrollArea: UiRect,
    val content: UiRect,
)


internal data class ResolvedUiInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val horizontal: Float get() = left + right
    val vertical: Float get() = top + bottom
}
