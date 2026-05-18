package ru.hollowhorizon.hollowengine.client.ui

import kotlin.math.abs

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
)

data class UiLayoutResult(
    val root: UiNode,
    val nodes: Map<UiNode, UiLayoutNode>,
) {
    operator fun get(node: UiNode): UiLayoutNode = nodes.getValue(node)
}

private const val DirectTextTransformEpsilon = 0.0001f
private const val ScrollOverflowEpsilon = 0.01f

private data class UiScrollbarReserve(
    val vertical: Boolean = false,
    val horizontal: Boolean = false,
) {
    val active: Boolean get() = vertical || horizontal

    companion object {
        val None = UiScrollbarReserve()
    }
}

internal data class LayoutSize(val width: Float, val height: Float)

private data class MeasuredChild(
    val node: UiNode,
    val style: ComputedStyle,
    val size: LayoutSize,
    val margin: ResolvedUiInsets,
)

private data class NodeBoxes(
    val scrollArea: UiRect,
    val content: UiRect,
)

class UiLayoutEngine {
    fun compute(
        resolved: ResolvedUiTree,
        width: Float,
        height: Float,
        scrollState: UiScrollState = UiScrollState(),
    ): UiLayoutResult {
        val initialLayouts = computeLayouts(resolved, width, height, scrollState, emptyMap())
        val scrollbarReserves = detectScrollbarReserves(resolved, initialLayouts)
        val layouts = if (scrollbarReserves.isEmpty()) {
            initialLayouts
        } else {
            computeLayouts(resolved, width, height, scrollState, scrollbarReserves)
        }
        val rangedLayouts = applyScrollRanges(resolved, layouts, scrollState)
        return UiLayoutResult(resolved.root, rangedLayouts)
    }

    private fun computeLayouts(
        resolved: ResolvedUiTree,
        width: Float,
        height: Float,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): Map<UiNode, UiLayoutNode> {
        val layouts = linkedMapOf<UiNode, UiLayoutNode>()
        val viewport = UiRect(0f, 0f, width, height)
        val rootRect = rootRect(resolved, width, height, scrollbarReserves)
        placeNode(
            node = resolved.root,
            resolved = resolved,
            rect = rootRect,
            parentRect = viewport,
            parentStyle = null,
            parentClip = null,
            parentTransform = UiMatrix4.identity(),
            parentInputTransform = UiMatrix4.identity(),
            scrollState = scrollState,
            scrollbarReserves = scrollbarReserves,
            layouts = layouts,
        )
        return layouts
    }

    private fun rootRect(
        resolved: ResolvedUiTree,
        width: Float,
        height: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): UiRect {
        val node = resolved.root
        val style = resolved[node]
        val margin = style.margin.resolve(width, height)
        val availableWidth = (width - margin.left - margin.right).coerceAtLeast(0f)
        val availableHeight = (height - margin.top - margin.bottom).coerceAtLeast(0f)
        val measured = measureNode(node, resolved, availableWidth, availableHeight, scrollbarReserves)
        val rootWidth = if (style.size.width is UiLength.Auto) availableWidth else measured.width
        val rootHeight = if (style.size.height is UiLength.Auto) availableHeight else measured.height
        val alignX = style.alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: UiAlign.START
        val alignY = style.alignVertical.takeUnless { it == UiAlign.AUTO } ?: UiAlign.START
        return UiRect(
            x = alignX.crossOffset(width, rootWidth, margin.left, margin.right),
            y = alignY.crossOffset(height, rootHeight, margin.top, margin.bottom),
            width = rootWidth.coerceIn(style.minSize.width, style.maxSize.width, width),
            height = rootHeight.coerceIn(style.minSize.height, style.maxSize.height, height),
        )
    }

    private fun placeNode(
        node: UiNode,
        resolved: ResolvedUiTree,
        rect: UiRect,
        parentRect: UiRect,
        parentStyle: ComputedStyle?,
        parentClip: UiRect?,
        parentTransform: UiMatrix4,
        parentInputTransform: UiMatrix4,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val style = resolved[node]
        val boxes = nodeBoxes(rect, style, scrollbarReserves[node] ?: UiScrollbarReserve.None)
        val scrollOffset = scrollState.offset(node)
        val clip = if (style.clip || style.input.scrollable) parentClip.intersect(boxes.content) else parentClip
        val localX = rect.x - parentRect.x
        val localY = rect.y - parentRect.y
        val pivot = style.transform.pivot.resolve(rect.width, rect.height)
        val transform = parentTransform * UiMatrix4.translation(localX, localY, style.position.z) *
                style.transform.matrix(pivot)
        val inputTransform = parentInputTransform * UiMatrix4.translation(localX, localY, style.position.z) *
                style.transform.matrix(pivot)
        val needsFramebuffer =
            style.transform.needsFramebuffer || node.requiresTextLayer(transform) || style.filter.requiresLayer || style.backdropFilter.requiresLayer

        layouts[node] = UiLayoutNode(
            node = node,
            rect = rect,
            content = boxes.content,
            clip = clip,
            worldTransform = transform,
            inputTransform = inputTransform,
            needsFramebuffer = needsFramebuffer,
            scrollOffset = scrollOffset,
            scrollArea = boxes.scrollArea,
        )

        placeChildren(node, resolved, style, boxes.content, rect, transform, inputTransform, clip, scrollState, scrollbarReserves, layouts)
    }

    private fun placeChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        if (node.children.isEmpty()) return
        val viewport = if (style.input.scrollable) {
            content.copy(x = content.x - scrollState.offset(node).x, y = content.y - scrollState.offset(node).y)
        } else {
            content
        }
        when (style.layout) {
            LayoutType.ROW -> placeRowChildren(node, resolved, style, viewport, parentRect, transform, inputTransform, clip, scrollState, scrollbarReserves, layouts)
            LayoutType.COLUMN -> placeColumnChildren(node, resolved, style, viewport, parentRect, transform, inputTransform, clip, scrollState, scrollbarReserves, layouts)
            LayoutType.GRID -> placeGridChildren(node, resolved, viewport, parentRect, transform, inputTransform, clip, scrollState, scrollbarReserves, layouts)
            LayoutType.STACK, LayoutType.FREE -> placeFreeChildren(node, resolved, style, viewport, parentRect, transform, inputTransform, clip, scrollState, scrollbarReserves, layouts)
        }
    }

    private fun placeRowChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val gap = style.gap.resolve(content.width)
        val measured = measureFlowChildren(node.children, resolved, content.width, content.height, scrollbarReserves)
        val grown = growRowChildren(measured, content.width, gap, resolved, scrollbarReserves)
        val totalWidth = grown.sumOfOuterWidth() + gap * (grown.size - 1).coerceAtLeast(0)
        var x = content.x + style.childAlignHorizontal().mainStartOffset(content.width, totalWidth, grown.size, gap)
        val actualGap = style.childAlignHorizontal().mainGap(content.width, totalWidth, grown.size, gap)
        for (child in grown) {
            val childStyle = child.style
            val position = childStyle.position.resolve(content.width, content.height)
            val align = childStyle.alignVertical.takeUnless { it == UiAlign.AUTO } ?: style.childAlignVertical() ?: UiAlign.START
            val childHeight = when (val height = childStyle.size.height) {
                UiLength.Auto -> if (align == UiAlign.STRETCH) {
                    (content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f)
                        .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)
                } else {
                    child.size.height
                }
                UiLength.Fill -> (content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f)
                    .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)
                is UiLength.Percent -> ((content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f) * height.value)
                    .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)
                is UiLength.Px -> child.size.height
            }
            val y = content.y + align.crossOffset(content.height, childHeight, child.margin.top, child.margin.bottom)
            val rect = UiRect(x + child.margin.left + position.x, y + position.y, child.size.width, childHeight)
            placeNode(child.node, resolved, rect, parentRect, style, clip, transform, inputTransform, scrollState, scrollbarReserves, layouts)
            x += child.margin.left + child.size.width + child.margin.right + actualGap
        }
    }

    private fun placeColumnChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val gap = style.gap.resolve(content.height)
        val measured = measureFlowChildren(node.children, resolved, content.width, content.height, scrollbarReserves)
        val grown = growColumnChildren(measured, content.height, gap, resolved, scrollbarReserves)
        val totalHeight = grown.sumOfOuterHeight() + gap * (grown.size - 1).coerceAtLeast(0)
        var y = content.y + style.childAlignVertical().mainStartOffset(content.height, totalHeight, grown.size, gap)
        val actualGap = style.childAlignVertical().mainGap(content.height, totalHeight, grown.size, gap)
        for (child in grown) {
            val childStyle = child.style
            val position = childStyle.position.resolve(content.width, content.height)
            val align = childStyle.alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: style.childAlignHorizontal() ?: UiAlign.STRETCH
            val childWidth = when (val width = childStyle.size.width) {
                UiLength.Auto -> if (align == UiAlign.STRETCH) {
                    (content.width - child.margin.left - child.margin.right).coerceAtLeast(0f)
                        .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)
                } else {
                    child.size.width
                }
                UiLength.Fill -> (content.width - child.margin.left - child.margin.right).coerceAtLeast(0f)
                    .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)
                is UiLength.Percent -> ((content.width - child.margin.left - child.margin.right).coerceAtLeast(0f) * width.value)
                    .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)
                is UiLength.Px -> child.size.width
            }
            val x = content.x + align.crossOffset(content.width, childWidth, child.margin.left, child.margin.right)
            val rect = UiRect(x + position.x, y + child.margin.top + position.y, childWidth, child.size.height)
            placeNode(child.node, resolved, rect, parentRect, style, clip, transform, inputTransform, scrollState, scrollbarReserves, layouts)
            y += child.margin.top + child.size.height + child.margin.bottom + actualGap
        }
    }

    private fun placeGridChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        var x = content.x
        var y = content.y
        var rowHeight = 0f
        for (child in measureFlowChildren(node.children, resolved, content.width, content.height, scrollbarReserves)) {
            val outerWidth = child.margin.left + child.size.width + child.margin.right
            if (x > content.x && x + outerWidth > content.x + content.width) {
                x = content.x
                y += rowHeight
                rowHeight = 0f
            }
            val position = child.style.position.resolve(content.width, content.height)
            val rect = UiRect(x + child.margin.left + position.x, y + child.margin.top + position.y, child.size.width, child.size.height)
            placeNode(child.node, resolved, rect, parentRect, resolved[node], clip, transform, inputTransform, scrollState, scrollbarReserves, layouts)
            x += outerWidth
            rowHeight = maxOf(rowHeight, child.margin.top + child.size.height + child.margin.bottom)
        }
    }

    private fun placeFreeChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        for (child in measureFlowChildren(node.children, resolved, content.width, content.height, scrollbarReserves)) {
            val position = child.style.position.resolve(content.width, content.height)
            val alignX = child.style.effectiveAlignHorizontal(style) ?: UiAlign.START
            val alignY = child.style.effectiveAlignVertical(style) ?: UiAlign.START
            val x = content.x + alignX.crossOffset(content.width, child.size.width, child.margin.left, child.margin.right)
            val y = content.y + alignY.crossOffset(content.height, child.size.height, child.margin.top, child.margin.bottom)
            val rect = UiRect(x + position.x, y + position.y, child.size.width, child.size.height)
            placeNode(child.node, resolved, rect, parentRect, style, clip, transform, inputTransform, scrollState, scrollbarReserves, layouts)
        }
    }

    private fun measureFlowChildren(
        children: List<UiNode>,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        deferFlexibleWidth: Boolean = false,
        deferFlexibleHeight: Boolean = false,
    ): List<MeasuredChild> {
        return children.map { child ->
            val style = resolved[child]
            val margin = style.margin.resolve(availableWidth, availableHeight)
            MeasuredChild(
                child,
                style,
                measureNode(
                    child,
                    resolved,
                    availableWidth,
                    availableHeight,
                    scrollbarReserves,
                    deferFlexibleWidth = deferFlexibleWidth,
                    deferFlexibleHeight = deferFlexibleHeight,
                ),
                margin
            )
        }
    }

    private fun growRowChildren(
        children: List<MeasuredChild>,
        availableWidth: Float,
        gap: Float,
        resolved: ResolvedUiTree,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): List<MeasuredChild> {
        val gapTotal = gap * (children.size - 1).coerceAtLeast(0)
        val fixedWidth = children
            .filterNot { it.isRowFlexible }
            .sumOf { (it.margin.left + it.size.width + it.margin.right).toDouble() }
            .toFloat()
        val flexible = children.filter { it.isRowFlexible }
        val flexibleMargins = flexible.sumOf { (it.margin.left + it.margin.right).toDouble() }.toFloat()
        val availableForFlexible = (availableWidth - fixedWidth - flexibleMargins - gapTotal).coerceAtLeast(0f)
        val totalWeight = flexible.sumOf { it.rowWeight().toDouble() }.toFloat()
        val distributed = if (flexible.isEmpty() || totalWeight <= 0f) children else children.map { child ->
            if (!child.isRowFlexible) child
            else {
                val weight = child.rowWeight()
                val targetWidth = availableForFlexible * (weight / totalWeight)
                child.copy(size = measureNode(child.node, resolved, targetWidth, child.size.height, scrollbarReserves, widthOverride = targetWidth))
            }
        }
        val overflow = (distributed.sumOfOuterWidth() + gapTotal - availableWidth).coerceAtLeast(0f)
        if (overflow <= 0f) return distributed
        val shrinkable = distributed.filter { it.style.size.width !is UiLength.Px && it.size.width > 0f }
        val shrinkableWidth = shrinkable.sumOf { it.size.width.toDouble() }.toFloat()
        if (shrinkableWidth <= 0f) return distributed
        return distributed.map {
            if (it.style.size.width is UiLength.Px || it.size.width <= 0f) it
            else {
                val targetWidth = (it.size.width - overflow * (it.size.width / shrinkableWidth)).coerceAtLeast(0f)
                it.copy(size = measureNode(it.node, resolved, targetWidth, it.size.height, scrollbarReserves, widthOverride = targetWidth))
            }
        }
    }

    private fun growColumnChildren(
        children: List<MeasuredChild>,
        availableHeight: Float,
        gap: Float,
        resolved: ResolvedUiTree,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): List<MeasuredChild> {
        val gapTotal = gap * (children.size - 1).coerceAtLeast(0)
        val fixedHeight = children
            .filterNot { it.isColumnFlexible }
            .sumOf { (it.margin.top + it.size.height + it.margin.bottom).toDouble() }
            .toFloat()
        val flexible = children.filter { it.isColumnFlexible }
        val flexibleMargins = flexible.sumOf { (it.margin.top + it.margin.bottom).toDouble() }.toFloat()
        val availableForFlexible = (availableHeight - fixedHeight - flexibleMargins - gapTotal).coerceAtLeast(0f)
        val totalWeight = flexible.sumOf { it.columnWeight().toDouble() }.toFloat()
        val distributed = if (flexible.isEmpty() || totalWeight <= 0f) children else children.map {
            if (!it.isColumnFlexible) it
            else {
                val weight = it.columnWeight()
                val targetHeight = availableForFlexible * (weight / totalWeight)
                it.copy(size = measureNode(it.node, resolved, it.size.width, targetHeight, scrollbarReserves, heightOverride = targetHeight))
            }
        }
        val overflow = (distributed.sumOfOuterHeight() + gapTotal - availableHeight).coerceAtLeast(0f)
        if (overflow <= 0f) return distributed
        val shrinkable = distributed.filter { it.style.size.height !is UiLength.Px && it.size.height > 0f }
        val shrinkableHeight = shrinkable.sumOf { it.size.height.toDouble() }.toFloat()
        if (shrinkableHeight <= 0f) return distributed
        return distributed.map {
            if (it.style.size.height is UiLength.Px || it.size.height <= 0f) it
            else {
                val targetHeight = (it.size.height - overflow * (it.size.height / shrinkableHeight)).coerceAtLeast(0f)
                it.copy(size = measureNode(it.node, resolved, it.size.width, targetHeight, scrollbarReserves, heightOverride = targetHeight))
            }
        }
    }

    private fun measureNode(
        node: UiNode,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        widthOverride: Float? = null,
        heightOverride: Float? = null,
        deferFlexibleWidth: Boolean = false,
        deferFlexibleHeight: Boolean = false,
    ): LayoutSize {
        val style = resolved[node]
        val referenceWidth = availableWidth.coerceAtLeast(0f)
        val referenceHeight = availableHeight.coerceAtLeast(0f)
        val insets = style.outerInsets(referenceWidth, referenceHeight, scrollbarReserves[node] ?: UiScrollbarReserve.None)
        var width = widthOverride ?: style.size.width.resolveOrNull(referenceWidth, deferFlexibleWidth)
        var height = heightOverride ?: style.size.height.resolveOrNull(referenceHeight, deferFlexibleHeight)
        style.aspectRatio?.let { ratio ->
            if (width == null && height != null) width = height * ratio
            if (height == null && width != null) height = width / ratio
        }
        val childAvailableWidth = ((width ?: referenceWidth) - insets.horizontal).coerceAtLeast(0f)
        val childAvailableHeight = ((height ?: referenceHeight) - insets.vertical).coerceAtLeast(0f)
        val intrinsic = intrinsicSize(
            node,
            resolved,
            style,
            childAvailableWidth,
            childAvailableHeight,
            scrollbarReserves,
            knownContentWidth = width?.let { (it - insets.horizontal).coerceAtLeast(0f) },
        )
        width = width ?: intrinsic.width + insets.horizontal
        height = height ?: intrinsic.height + insets.vertical
        style.aspectRatio?.let { ratio ->
            val resolvedWidth = width
            val resolvedHeight = height
            if (style.size.width is UiLength.Auto && style.size.height !is UiLength.Auto) {
                width = requireNotNull(resolvedHeight) * ratio
            }
            if (style.size.height is UiLength.Auto && style.size.width !is UiLength.Auto) {
                height = requireNotNull(resolvedWidth) / ratio
            }
        }
        val finalWidth = requireNotNull(width)
        val finalHeight = requireNotNull(height)
        return LayoutSize(
            width = finalWidth.coerceIn(style.minSize.width, style.maxSize.width, referenceWidth),
            height = finalHeight.coerceIn(style.minSize.height, style.maxSize.height, referenceHeight),
        )
    }

    private fun intrinsicSize(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        knownContentWidth: Float? = null,
    ): LayoutSize {
        if (node is TextNode) return UiTextLayouter.measure(
            text = node.text.template,
            availableWidth = availableWidth,
            knownWidth = knownContentWidth,
            wrap = style.textWrap,
            fontSize = style.fontSize,
        )
        if (node.children.isEmpty()) return replacedIntrinsicSize(node, style)
        val children = measureFlowChildren(
            node.children,
            resolved,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            deferFlexibleWidth = style.size.width is UiLength.Auto,
            deferFlexibleHeight = style.size.height is UiLength.Auto,
        )
        val gap = style.gap.resolve(if (style.layout == LayoutType.ROW) availableWidth else availableHeight)
        return when (style.layout) {
            LayoutType.ROW -> LayoutSize(
                children.sumOfOuterWidth() + gap * (children.size - 1).coerceAtLeast(0),
                children.maxOfOuterHeight(),
            )
            LayoutType.COLUMN -> LayoutSize(
                children.maxOfOuterWidth(),
                children.sumOfOuterHeight() + gap * (children.size - 1).coerceAtLeast(0),
            )
            LayoutType.GRID, LayoutType.STACK, LayoutType.FREE -> LayoutSize(children.maxOfOuterWidth(), children.maxOfOuterHeight())
        }
    }

    private fun nodeBoxes(rect: UiRect, style: ComputedStyle, reserve: UiScrollbarReserve): NodeBoxes {
        val border = style.border.width.resolve(rect.width, rect.height)
        val padding = style.padding.resolve(rect.width, rect.height)
        val scrollArea = UiRect(
            rect.x + border.left + padding.left,
            rect.y + border.top + padding.top,
            (rect.width - border.left - border.right - padding.left - padding.right).coerceAtLeast(0f),
            (rect.height - border.top - border.bottom - padding.top - padding.bottom).coerceAtLeast(0f),
        )
        return NodeBoxes(
            scrollArea = scrollArea,
            content = scrollArea.copy(
                width = (scrollArea.width - if (reserve.vertical) UiScrollbarGutter else 0f).coerceAtLeast(0f),
                height = (scrollArea.height - if (reserve.horizontal) UiScrollbarGutter else 0f).coerceAtLeast(0f),
            )
        )
    }

}

private fun UiNode.requiresTextLayer(transform: UiMatrix4): Boolean {
    return this is TextNode && !transform.isDirectTextTransform()
}

private fun UiMatrix4.isDirectTextTransform(): Boolean {
    val origin = transform(0f, 0f, 0f)
    val xAxis = transform(1f, 0f, 0f)
    val yAxis = transform(0f, 1f, 0f)
    val xDelta = UiVec3(xAxis.x - origin.x, xAxis.y - origin.y, xAxis.z - origin.z)
    val yDelta = UiVec3(yAxis.x - origin.x, yAxis.y - origin.y, yAxis.z - origin.z)
    return abs(xDelta.y) <= DirectTextTransformEpsilon && abs(xDelta.z) <= DirectTextTransformEpsilon &&
            abs(yDelta.x) <= DirectTextTransformEpsilon && abs(yDelta.z) <= DirectTextTransformEpsilon
}

private fun ComputedStyle.outerInsets(width: Float, height: Float, reserve: UiScrollbarReserve): ResolvedUiInsets {
    val border = border.width.resolve(width, height)
    val padding = padding.resolve(width, height)
    return ResolvedUiInsets(
        left = border.left + padding.left,
        top = border.top + padding.top,
        right = border.right + padding.right + if (reserve.vertical) UiScrollbarGutter else 0f,
        bottom = border.bottom + padding.bottom + if (reserve.horizontal) UiScrollbarGutter else 0f,
    )
}

private fun applyScrollRanges(
    resolved: ResolvedUiTree,
    layouts: Map<UiNode, UiLayoutNode>,
    scrollState: UiScrollState,
): Map<UiNode, UiLayoutNode> {
    val result = layouts.toMutableMap()
    for ((node, layout) in layouts) {
        val style = resolved[node]
        if (!style.input.scrollable) continue
        val childBounds = node.children.mapNotNull { layouts[it]?.rect?.withScroll(layout.scrollOffset) }.union() ?: layout.content
        val range = UiScrollOffset(
            x = maxOf(0f, childBounds.x + childBounds.width - (layout.content.x + layout.content.width)),
            y = maxOf(0f, childBounds.y + childBounds.height - (layout.content.y + layout.content.height)),
        )
        val clamped = scrollState.clamp(node, range)
        val clip = layout.clip?.let { it.intersect(layout.content) } ?: layout.content
        result[node] = layout.copy(content = layout.content, clip = clip, scrollOffset = clamped, scrollRange = range)
    }
    return result
}

private fun detectScrollbarReserves(
    resolved: ResolvedUiTree,
    layouts: Map<UiNode, UiLayoutNode>,
): Map<UiNode, UiScrollbarReserve> {
    val reserves = linkedMapOf<UiNode, UiScrollbarReserve>()
    for ((node, layout) in layouts) {
        val style = resolved[node]
        if (!style.input.scrollable) continue
        val childBounds = node.children.mapNotNull { layouts[it]?.rect?.withScroll(layout.scrollOffset) }.union() ?: layout.content
        val reserve = UiScrollbarReserve(
            vertical = (childBounds.y + childBounds.height).exceeds(layout.content.y + layout.content.height),
            horizontal = (childBounds.x + childBounds.width).exceeds(layout.content.x + layout.content.width),
        )
        if (reserve.active) reserves[node] = reserve
    }
    return reserves
}

private fun List<MeasuredChild>.sumOfOuterWidth(): Float = sumOf { (it.margin.left + it.size.width + it.margin.right).toDouble() }.toFloat()

private fun List<MeasuredChild>.sumOfOuterHeight(): Float = sumOf { (it.margin.top + it.size.height + it.margin.bottom).toDouble() }.toFloat()

private fun List<MeasuredChild>.maxOfOuterWidth(): Float = maxOfOrNull { it.margin.left + it.size.width + it.margin.right } ?: 0f

private fun List<MeasuredChild>.maxOfOuterHeight(): Float = maxOfOrNull { it.margin.top + it.size.height + it.margin.bottom } ?: 0f

private val MeasuredChild.isRowFlexible: Boolean
    get() = style.size.width is UiLength.Fill ||
            style.size.width is UiLength.Percent ||
            style.grow > 0f ||
            node is TextNode && style.textWrap && style.size.width is UiLength.Auto

private val MeasuredChild.isColumnFlexible: Boolean
    get() = style.size.height is UiLength.Fill || style.size.height is UiLength.Percent || style.grow > 0f

private fun MeasuredChild.rowWeight(): Float {
    if (style.grow > 0f) return style.grow
    return when (val width = style.size.width) {
        UiLength.Fill -> 1f
        is UiLength.Percent -> width.value
        UiLength.Auto -> size.width.coerceAtLeast(1f)
        is UiLength.Px -> 0f
    }
}

private fun MeasuredChild.columnWeight(): Float {
    if (style.grow > 0f) return style.grow
    return when (val height = style.size.height) {
        UiLength.Fill -> 1f
        is UiLength.Percent -> height.value
        UiLength.Auto -> size.height.coerceAtLeast(1f)
        is UiLength.Px -> 0f
    }
}

private fun UiLength.resolveOrNull(reference: Float, deferFlexible: Boolean = false): Float? = when (this) {
    UiLength.Auto -> null
    UiLength.Fill -> if (deferFlexible) null else reference
    is UiLength.Px -> value
    is UiLength.Percent -> if (deferFlexible) null else reference * value
}

private fun replacedIntrinsicSize(node: UiNode, style: ComputedStyle): LayoutSize {
    return if (node is ImageNode || style.background is UiPaint.Image) {
        LayoutSize(DefaultReplacedElementSize, DefaultReplacedElementSize)
    } else {
        LayoutSize(0f, 0f)
    }
}

private const val DefaultReplacedElementSize = 32f

private fun Float.coerceIn(min: UiLength, max: UiLength, reference: Float): Float {
    val minValue = min.resolve(reference, 0f)
    val maxValue = max.resolve(reference, Float.POSITIVE_INFINITY)
    return coerceIn(minValue, maxValue.coerceAtLeast(minValue))
}

private fun UiAlign?.mainStartOffset(available: Float, used: Float, count: Int, gap: Float): Float {
    val extra = (available - used).coerceAtLeast(0f)
    return when (this) {
        UiAlign.CENTER -> extra / 2f
        UiAlign.END -> extra
        UiAlign.SPACE_AROUND -> if (count > 0) extra / count / 2f else 0f
        UiAlign.SPACE_EVENLY -> if (count > 0) extra / (count + 1) else 0f
        else -> 0f
    }
}

private fun UiAlign?.mainGap(available: Float, used: Float, count: Int, gap: Float): Float {
    val extra = (available - used).coerceAtLeast(0f)
    return when (this) {
        UiAlign.SPACE_BETWEEN -> if (count > 1) gap + extra / (count - 1) else gap
        UiAlign.SPACE_AROUND -> if (count > 0) gap + extra / count else gap
        UiAlign.SPACE_EVENLY -> if (count > 0) gap + extra / (count + 1) else gap
        else -> gap
    }
}

private fun UiAlign.crossOffset(available: Float, size: Float, startMargin: Float, endMargin: Float): Float {
    return when (this) {
        UiAlign.CENTER -> startMargin + (available - size - startMargin - endMargin) / 2f
        UiAlign.END -> available - size - endMargin
        else -> startMargin
    }.coerceAtLeast(startMargin)
}

private fun ComputedStyle.effectiveAlignHorizontal(parent: ComputedStyle?): UiAlign? {
    return alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: parent?.childAlignHorizontal()
}

private fun ComputedStyle.effectiveAlignVertical(parent: ComputedStyle?): UiAlign? {
    return alignVertical.takeUnless { it == UiAlign.AUTO } ?: parent?.childAlignVertical()
}

private fun ComputedStyle.childAlignHorizontal(): UiAlign? {
    return alignItemsHorizontal.takeUnless { it == UiAlign.AUTO }
        ?: if (layout == LayoutType.ROW) justifyContent.takeUnless { it == UiAlign.AUTO }
        else alignItems.takeUnless { it == UiAlign.AUTO }
}

private fun ComputedStyle.childAlignVertical(): UiAlign? {
    return alignItemsVertical.takeUnless { it == UiAlign.AUTO }
        ?: if (layout == LayoutType.ROW) alignItems.takeUnless { it == UiAlign.AUTO }
        else justifyContent.takeUnless { it == UiAlign.AUTO }
}

private fun UiInsets.resolve(parentWidth: Float, parentHeight: Float): ResolvedUiInsets {
    return ResolvedUiInsets(
        left = left.resolve(parentWidth),
        top = top.resolve(parentHeight),
        right = right.resolve(parentWidth),
        bottom = bottom.resolve(parentHeight),
    )
}

private data class ResolvedUiInsets(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val horizontal: Float get() = left + right
    val vertical: Float get() = top + bottom
}

private fun UiRect?.intersect(other: UiRect): UiRect {
    if (this == null) return other
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    if (right <= left || bottom <= top) return UiRect(left, top, 0f, 0f)
    return UiRect(left, top, right - left, bottom - top)
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
