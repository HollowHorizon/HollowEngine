package ru.hollowhorizon.hollowengine.client.ui.scroll

import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.layout.*
import ru.hollowhorizon.hollowengine.client.ui.scrollAxes
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import java.util.*


private const val ScrollOverflowEpsilon = 0.01f

internal fun applyScrollRanges(
    layouts: Map<UiNode, UiLayoutNode>,
    scrollState: UiScrollState,
    layoutChildren: (UiNode) -> List<UiNode> = ::layoutChildren,
): Map<UiNode, UiLayoutNode> {
    val result = layouts.toMutableMap()
    for ((node, layout) in layouts) {
        val style = node.resolvedSnapshot
        if (!style.scrollable) continue
        val axes = node.scrollAxes()
        val childBounds = scrollableContentBounds(node, style, layout, layouts, layoutChildren)
        val range = UiScrollOffset(
            x = if (axes.horizontal) maxOf(0f, childBounds.x + childBounds.width - (layout.content.x + layout.content.width)) else 0f,
            y = if (axes.vertical) maxOf(0f, childBounds.y + childBounds.height - (layout.content.y + layout.content.height)) else 0f,
        )
        val clamped = scrollState.clamp(node, range)
        val clip = layout.clip?.intersect(layout.content) ?: layout.content
        result[node] = layout.copy(
            content = layout.content,
            clip = clip,
            scrollOffset = clamped,
            scrollRange = range,
        )
    }
    return result
}

internal class ScrollbarCache {
    private val byContainer = WeakHashMap<UiNode, MutableMap<ScrollbarOrientation, ScrollbarNode>>()

    fun scrollbar(container: UiNode, orientation: ScrollbarOrientation): ScrollbarNode {
        val map = byContainer.getOrPut(container) { mutableMapOf() }
        return map.getOrPut(orientation) {
            ScrollbarNode(orientation).also { it.layoutState.attachTo(container) }
        }
    }
}

internal fun placeScrollbarNodes(
    layouts: Map<UiNode, UiLayoutNode>,
    cache: ScrollbarCache,
): Pair<Map<UiNode, UiLayoutNode>, Map<UiNode, List<ScrollbarNode>>> {
    val result = layouts.toMutableMap()
    val scrollbars = HashMap<UiNode, List<ScrollbarNode>>()
    for ((container, containerLayout) in layouts) {
        if (container.resolvedSnapshot.scrollAxes == null) continue
        val geometry = scrollbarGeometry(container.resolvedSnapshot, containerLayout)
        if (geometry.isEmpty()) continue
        val bars = ArrayList<ScrollbarNode>(geometry.size)
        for (geom in geometry) {
            val bar = cache.scrollbar(container, geom.orientation)
            result[bar] = scrollbarPartLayout(bar, geom.track, containerLayout)
            result[bar.thumb] = scrollbarPartLayout(bar.thumb, geom.thumb, containerLayout)
            bars += bar
        }
        scrollbars[container] = bars
    }
    return result to scrollbars
}

private fun scrollbarPartLayout(node: UiNode, relativeRect: UiRect, container: UiLayoutNode): UiLayoutNode {
    val offset = UiMatrix4.translation(relativeRect.x, relativeRect.y, 0f)
    val absolute = UiRect(
        container.rect.x + relativeRect.x,
        container.rect.y + relativeRect.y,
        relativeRect.width,
        relativeRect.height,
    )
    return UiLayoutNode(
        node = node,
        rect = absolute,
        content = absolute,
        clip = container.scrollArea,
        worldTransform = container.worldTransform * offset,
        inputTransform = container.inputTransform * offset,
        needsFramebuffer = false,
    )
}

private fun scrollbarGeometry(style: UiComputedStyle, layoutNode: UiLayoutNode): List<UiScrollbarGeometry> {
    val result = mutableListOf<UiScrollbarGeometry>()
    val scrollArea = layoutNode.scrollArea
    val rect = layoutNode.rect
    val verticalStyle = style.scrollbar.resolved(scrollArea.width)
    val horizontalStyle = style.scrollbar.resolved(scrollArea.height)
    val hasVertical = layoutNode.scrollRange.y > 0f && scrollArea.height > verticalStyle.gutter
    val hasHorizontal = layoutNode.scrollRange.x > 0f && scrollArea.width > horizontalStyle.gutter

    fun buildScrollbar(
        orientation: ScrollbarOrientation,
        isVisible: Boolean,
        margin: Float,
        minThumbSize: Float,
        reserve: Float,
        scrollRangeAxis: Float,
        scrollOffsetAxis: Float,
        scrollAreaSize: Float,
        contentSize: Float,
        createGeometry: (trackSize: Float, thumbSize: Float, thumbOffset: Float) -> Pair<UiRect, UiRect>
    ) {
        if (!isVisible) return

        val trackSize = scrollAreaSize - margin * 2f - reserve
        if (trackSize <= 0f) return

        val totalContentSize = contentSize + scrollRangeAxis
        val thumbSize = maxOf(minThumbSize, trackSize * contentSize / totalContentSize)

        val thumbRatio = if (scrollRangeAxis > 0f) scrollOffsetAxis / scrollRangeAxis else 0f
        val thumbOffset = (trackSize - thumbSize) * thumbRatio

        val (track, thumb) = createGeometry(trackSize, thumbSize, thumbOffset)
        result += UiScrollbarGeometry(track, thumb, orientation)
    }

    buildScrollbar(
        ScrollbarOrientation.VERTICAL, hasVertical,
        verticalStyle.margin, verticalStyle.minThumbSize,
        if (hasHorizontal) horizontalStyle.gutter else 0f,
        layoutNode.scrollRange.y, layoutNode.scrollOffset.y,
        scrollArea.height, layoutNode.content.height
    ) { trackSize, thumbSize, thumbOffset ->
        val track = UiRect(
            x = scrollArea.x - rect.x + scrollArea.width - verticalStyle.thickness - verticalStyle.margin,
            y = scrollArea.y - rect.y + verticalStyle.margin,
            width = verticalStyle.thickness,
            height = trackSize
        )
        val thumb = track.copy(y = track.y + thumbOffset, height = thumbSize)
        track to thumb
    }

    buildScrollbar(
        ScrollbarOrientation.HORIZONTAL, hasHorizontal,
        horizontalStyle.margin, horizontalStyle.minThumbSize,
        if (hasVertical) verticalStyle.gutter else 0f,
        layoutNode.scrollRange.x, layoutNode.scrollOffset.x,
        scrollArea.width, layoutNode.content.width
    ) { trackSize, thumbSize, thumbOffset ->
        val track = UiRect(
            x = scrollArea.x - rect.x + horizontalStyle.margin,
            y = scrollArea.y - rect.y + scrollArea.height - horizontalStyle.thickness - horizontalStyle.margin,
            width = trackSize,
            height = horizontalStyle.thickness
        )
        val thumb = track.copy(x = track.x + thumbOffset, width = thumbSize)
        track to thumb
    }

    return result
}

internal fun detectScrollbarReserves(
    layouts: Map<UiNode, UiLayoutNode>,
    layoutChildren: (UiNode) -> List<UiNode> = ::layoutChildren,
): Map<UiNode, UiScrollbarReserve> {
    val reserves = linkedMapOf<UiNode, UiScrollbarReserve>()
    for ((node, layout) in layouts) {
        val style = node.resolvedSnapshot
        if (!style.scrollable) continue
        val axes = node.scrollAxes()
        val childBounds = scrollableContentBounds(node, style, layout, layouts, layoutChildren)
        val reserve = UiScrollbarReserve(
            vertical = axes.vertical && (childBounds.y + childBounds.height).exceeds(layout.content.y + layout.content.height),
            horizontal = axes.horizontal && (childBounds.x + childBounds.width).exceeds(layout.content.x + layout.content.width),
        )
        if (reserve.active) reserves[node] = reserve
    }
    return reserves
}

private fun scrollableContentBounds(
    node: UiNode,
    style: UiComputedStyle,
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
    style: UiComputedStyle,
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
        node.content.toRichText(widgetMetrics),
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
