package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.layout.*
import ru.hollowhorizon.hollowengine.client.ui.style.ComputedStyle
import ru.hollowhorizon.hollowengine.client.ui.style.ResolvedUiTree
import ru.hollowhorizon.hollowengine.client.ui.widgets.*


private const val ScrollOverflowEpsilon = 0.01f

internal fun applyScrollRanges(
    resolved: ResolvedUiTree,
    layouts: Map<UiNode, UiLayoutNode>,
    scrollState: UiScrollState,
    layoutChildren: (UiNode) -> List<UiNode> = ::layoutChildren,
): Map<UiNode, UiLayoutNode> {
    val result = layouts.toMutableMap()
    for ((node, layout) in layouts) {
        val style = resolved[node]
        if (!style.input.scrollable) continue
        val childBounds = scrollableContentBounds(node, style, layout, layouts, layoutChildren)
        val range = UiScrollOffset(
            x = maxOf(0f, childBounds.x + childBounds.width - (layout.content.x + layout.content.width)),
            y = maxOf(0f, childBounds.y + childBounds.height - (layout.content.y + layout.content.height)),
        )
        val clamped = scrollState.clamp(node, range)
        val clip = layout.clip?.intersect(layout.content) ?: layout.content
        val scrolledLayout = layout.copy(
            content = layout.content,
            clip = clip,
            scrollOffset = clamped,
            scrollRange = range,
        )
        result[node] = scrolledLayout.copy(scrollbars = scrollbarGeometry(style, scrolledLayout))
    }
    return result
}

private fun scrollbarGeometry(style: ComputedStyle, layoutNode: UiLayoutNode): List<UiScrollbarGeometry> {
    val result = mutableListOf<UiScrollbarGeometry>()
    val verticalStyle = style.scrollbar.resolved(layoutNode.scrollArea.width)
    val horizontalStyle = style.scrollbar.resolved(layoutNode.scrollArea.height)
    val hasVerticalScrollbar = layoutNode.scrollRange.y > 0f && layoutNode.scrollArea.height > verticalStyle.gutter
    val hasHorizontalScrollbar = layoutNode.scrollRange.x > 0f && layoutNode.scrollArea.width > horizontalStyle.gutter
    if (hasVerticalScrollbar) {
        val horizontalReserve = if (hasHorizontalScrollbar) horizontalStyle.gutter else 0f
        val trackHeight = layoutNode.scrollArea.height - verticalStyle.margin * 2f - horizontalReserve
        if (trackHeight > 0f) {
            val track = UiRect(
                x = layoutNode.scrollArea.x - layoutNode.rect.x + layoutNode.scrollArea.width -
                        verticalStyle.thickness - verticalStyle.margin,
                y = layoutNode.scrollArea.y - layoutNode.rect.y + verticalStyle.margin,
                width = verticalStyle.thickness,
                height = trackHeight,
            )
            val contentHeight = layoutNode.content.height + layoutNode.scrollRange.y
            val thumbHeight =
                maxOf(verticalStyle.minThumbSize, track.height * layoutNode.content.height / contentHeight)
            val thumbY = track.y + (track.height - thumbHeight) * (layoutNode.scrollOffset.y / layoutNode.scrollRange.y)
            result += UiScrollbarGeometry(
                track = track,
                thumb = track.copy(y = thumbY, height = thumbHeight),
                orientation = ScrollbarOrientation.VERTICAL,
            )
        }
    }
    if (hasHorizontalScrollbar) {
        val verticalReserve = if (hasVerticalScrollbar) verticalStyle.gutter else 0f
        val trackWidth = layoutNode.scrollArea.width - horizontalStyle.margin * 2f - verticalReserve
        if (trackWidth > 0f) {
            val track = UiRect(
                x = layoutNode.scrollArea.x - layoutNode.rect.x + horizontalStyle.margin,
                y = layoutNode.scrollArea.y - layoutNode.rect.y + layoutNode.scrollArea.height -
                        horizontalStyle.thickness - horizontalStyle.margin,
                width = trackWidth,
                height = horizontalStyle.thickness,
            )
            val contentWidth = layoutNode.content.width + layoutNode.scrollRange.x
            val thumbWidth = maxOf(horizontalStyle.minThumbSize, track.width * layoutNode.content.width / contentWidth)
            val thumbX = track.x + (track.width - thumbWidth) * (layoutNode.scrollOffset.x / layoutNode.scrollRange.x)
            result += UiScrollbarGeometry(
                track = track,
                thumb = track.copy(x = thumbX, width = thumbWidth),
                orientation = ScrollbarOrientation.HORIZONTAL,
            )
        }
    }
    return result
}

internal fun detectScrollbarReserves(
    resolved: ResolvedUiTree,
    layouts: Map<UiNode, UiLayoutNode>,
    layoutChildren: (UiNode) -> List<UiNode> = ::layoutChildren,
): Map<UiNode, UiScrollbarReserve> {
    val reserves = linkedMapOf<UiNode, UiScrollbarReserve>()
    for ((node, layout) in layouts) {
        val style = resolved[node]
        if (!style.input.scrollable) continue
        val childBounds = scrollableContentBounds(node, style, layout, layouts, layoutChildren)
        val reserve = UiScrollbarReserve(
            vertical = (childBounds.y + childBounds.height).exceeds(layout.content.y + layout.content.height),
            horizontal = (childBounds.x + childBounds.width).exceeds(layout.content.x + layout.content.width),
        )
        if (reserve.active) reserves[node] = reserve
    }
    return reserves
}

private fun scrollableContentBounds(
    node: UiNode,
    style: ComputedStyle,
    layout: UiLayoutNode,
    layouts: Map<UiNode, UiLayoutNode>,
    layoutChildren: (UiNode) -> List<UiNode>,
): UiRect {
    layout.virtualContentBounds?.let { return it }
    if (node is ru.hollowhorizon.hollowengine.client.ui.TextNode || node is TextFieldNode) {
        val textLayout = if (node is ru.hollowhorizon.hollowengine.client.ui.TextNode) {
            layout.textLayout ?: textLayoutForScrollBounds(node, style, layout, layouts, layoutChildren)
        } else {
            val field = node as TextFieldNode
            layout.textLayout ?: textFieldDisplayLayout(
                field,
                style,
                layout,
                layout.inlineWidgetMetrics(),
            )
        }
        val textOffset = if (node is TextFieldNode) textFieldTextOffset(
            node,
            style
        ) else 0f
        val textViewportWidth =
            if (node is TextFieldNode) textFieldTextWidth(
                node,
                style,
                layout
            ) else layout.content.width
        val horizontalPadding =
            if (node is TextFieldNode) textFieldHorizontalScrollPadding(
                textViewportWidth
            ) else TextFieldCaretVisibilityPadding
        return UiRect(
            layout.content.x,
            layout.content.y,
            maxOf(
                layout.content.width,
                textOffset + textLayout.maxNaturalLineWidth() + TextFieldCaretWidth + horizontalPadding
            ),
            maxOf(
                layout.content.height,
                textLayout.height + TextFieldCaretVisibilityPadding
            ),
        )
    }
    return layoutChildren(node).mapNotNull { layouts[it]?.rect?.withScroll(layout.scrollOffset) }.union()
        ?: layout.content
}

private fun textLayoutForScrollBounds(
    node: ru.hollowhorizon.hollowengine.client.ui.TextNode,
    style: ComputedStyle,
    layout: UiLayoutNode,
    layouts: Map<UiNode, UiLayoutNode>,
    layoutChildren: (UiNode) -> List<UiNode>,
): UiTextLayout {
    val widgetMetrics = layoutChildren(node).mapNotNull { child ->
        val id = child.id ?: return@mapNotNull null
        val rect = layouts[child]?.rect ?: return@mapNotNull null
        id to UiInlineWidgetMetrics(rect.width, rect.height)
    }.toMap()
    return UiTextLayouter.layout(
        node.content.resolve().toRichText(widgetMetrics),
        layout.content.width,
        Float.POSITIVE_INFINITY,
        style.textWrap,
        style.textAlign,
        style.fontSize,
        style.fontFamily,
        lineSpacing = style.lineSpacing,
        spaceWidth = style.spaceWidth,
    )
}

private fun UiTextLayout.maxNaturalLineWidth(): Float = maxNaturalLineWidth

private fun layoutChildren(node: UiNode): List<UiNode> {
    return node.children.filterNot { it is ru.hollowhorizon.hollowengine.client.ui.PopupNode }
}

private fun UiRect.withScroll(scrollOffset: UiScrollOffset): UiRect {
    return copy(x = x + scrollOffset.x, y = y + scrollOffset.y)
}

private fun Float.exceeds(limit: Float): Boolean = this - limit > ScrollOverflowEpsilon

private fun List<UiRect>.union(): UiRect? {
    if (isEmpty()) return null
    var left = first().x
    var top = first().y
    var right = first().x + first().width
    var bottom = first().y + first().height
    for (rect in drop(1)) {
        left = minOf(left, rect.x)
        top = minOf(top, rect.y)
        right = maxOf(right, rect.x + rect.width)
        bottom = maxOf(bottom, rect.y + rect.height)
    }
    return UiRect(left, top, right - left, bottom - top)
}
