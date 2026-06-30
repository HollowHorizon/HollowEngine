package ru.hollowhorizon.hollowengine.client.ui


import java.util.*
import kotlin.math.abs

private const val ConstraintReflowEpsilon = 0.01f

class UiLayoutPipeline {
    internal var placementStack: ArrayDeque<PlacementTask>? = null
    internal var layoutPass: LayoutPass? = null

    private inner class EngineMeasurable(
        override val node: UiNode,
        private val resolved: ResolvedUiTree,
        private val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ) : UiMeasurable {
        override fun measure(constraints: UiConstraints): UiPlaceable {
            val size = measureNode(
                node,
                resolved,
                constraints.maxWidth,
                constraints.maxHeight,
                scrollbarReserves,
                widthOverride = constraints.fixedWidthOrNull(),
                heightOverride = constraints.fixedHeightOrNull(),
            )
            return UiPlaceable(
                width = constraints.constrainWidth(size.width),
                height = constraints.constrainHeight(size.height),
                node = node,
            )
        }
    }

    fun compute(
        resolved: ResolvedUiTree,
        width: Float,
        height: Float,
        scrollState: UiScrollState = UiScrollState(),
    ): UiLayoutResult {
        val initialLayouts = computeLayouts(resolved, width, height, scrollState, emptyMap())
        val scrollbarReserves = detectScrollbarReserves(resolved, initialLayouts, ::layoutChildren)
        val layouts = if (scrollbarReserves.isEmpty()) {
            initialLayouts
        } else {
            computeLayouts(resolved, width, height, scrollState, scrollbarReserves)
        }
        val rangedLayouts = applyScrollRanges(resolved, layouts, scrollState, ::layoutChildren)
        val traversalOrder = rangedLayouts.keys.toList()
        return UiLayoutResult(
            root = resolved.root,
            nodes = rangedLayouts,
            traversalOrder = traversalOrder,
            popupNodes = traversalOrder.filterIsInstance<PopupNode>(),
        )
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
        val previousStack = placementStack
        val previousPass = layoutPass
        val stack = ArrayDeque<PlacementTask>()
        try {
            layoutPass = LayoutPass(resolved.root)
            val rootRect = rootRect(resolved, width, height, scrollbarReserves)
            placementStack = stack
            enqueuePlacement(
                node = resolved.root,
                resolved = resolved,
                rect = rootRect,
                parentRect = viewport,
                parentStyle = null,
                parentClip = null,
                parentTransform = UiMatrix4.identity(),
                parentInputTransform = UiMatrix4.identity(),
                insideFramebuffer = false,
                scrollState = scrollState,
                scrollbarReserves = scrollbarReserves,
                layouts = layouts,
            )
            while (stack.isNotEmpty()) {
                when (val task = stack.removeLast()) {
                    is NodePlacementTask -> placeNodeNow(task)
                    is PopupPlacementTask -> placePopupChildrenNow(task)
                }
            }
        } finally {
            placementStack = previousStack
            layoutPass = previousPass
        }
        return layouts
    }

    private fun layoutChildren(node: UiNode): List<UiNode> {
        return layoutPass?.layoutChildren?.get(node) ?: node.children.filterNot { it is PopupNode }
    }

    private fun popupChildren(node: UiNode): List<PopupNode> {
        return layoutPass?.popupChildren?.get(node) ?: node.children.filterIsInstance<PopupNode>()
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
        val measured =
            measureNode(node, resolved, availableWidth, availableHeight, scrollbarReserves)
        val rootWidth = if (style.size.width is UiLength.Auto && UiStyleProperty.WIDTH !in style.explicitProperties) {
            availableWidth
        } else {
            measured.width
        }
        val rootHeight =
            if (style.size.height is UiLength.Auto && UiStyleProperty.HEIGHT !in style.explicitProperties) {
                availableHeight
            } else {
                measured.height
            }
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
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val stack = placementStack
        if (stack != null) {
            enqueuePlacement(
                node,
                resolved,
                rect,
                parentRect,
                parentStyle,
                parentClip,
                parentTransform,
                parentInputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
            return
        }
        placeNodeNow(
            NodePlacementTask(
                node,
                resolved,
                rect,
                parentRect,
                parentStyle,
                parentClip,
                parentTransform,
                parentInputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
        )
    }

    private fun enqueuePlacement(
        node: UiNode,
        resolved: ResolvedUiTree,
        rect: UiRect,
        parentRect: UiRect,
        parentStyle: ComputedStyle?,
        parentClip: UiRect?,
        parentTransform: UiMatrix4,
        parentInputTransform: UiMatrix4,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        placementStack?.addLast(
            NodePlacementTask(
                node,
                resolved,
                rect,
                parentRect,
                parentStyle,
                parentClip,
                parentTransform,
                parentInputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
        )
    }

    private fun placeNodeNow(task: NodePlacementTask) {
        val node = task.node
        val resolved = task.resolved
        val rect = task.rect
        val parentRect = task.parentRect
        val parentClip = task.parentClip
        val parentTransform = task.parentTransform
        val parentInputTransform = task.parentInputTransform
        val insideFramebuffer = task.insideFramebuffer
        val scrollState = task.scrollState
        val scrollbarReserves = task.scrollbarReserves
        val layouts = task.layouts
        val style = resolved[node]
        val boxes = nodeBoxes(rect, style, scrollbarReserves[node] ?: UiScrollbarReserve.None)
        val scrollOffset = scrollState.offset(node)
        val textLayout = when (node) {
            is TextNode -> layoutTextNode(node, resolved, style, boxes.content, scrollbarReserves)
            is TextFieldNode -> layoutTextFieldNode(node, resolved, style, boxes.content, scrollbarReserves)
            else -> null
        }
        val clip = if (style.clip || style.input.scrollable) parentClip.intersect(boxes.content) else parentClip
        val localX = rect.x - parentRect.x
        val localY = rect.y - parentRect.y
        val pivot = style.transform.pivot.resolve(rect.width, rect.height)
        val transform = parentTransform * UiMatrix4.translation(localX, localY, style.position.z) *
                style.transform.matrix(pivot)
        val inputTransform = parentInputTransform * UiMatrix4.translation(localX, localY, style.position.z) *
                style.transform.matrix(pivot)
        val opacityNeedsLayer = style.opacity < 1f && node.children.isNotEmpty()
        val needsFramebuffer =
            opacityNeedsLayer ||
                    style.transform.needsFramebuffer || !insideFramebuffer && node.requiresTextLayer(transform) ||
                    style.filter.requiresLayer ||
                    style.backdropFilter.requiresLayer ||
                    style.clipShape != null && style.clip

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
            textLayout = textLayout,
        )

        placeChildren(
            node,
            resolved,
            style,
            boxes.content,
            rect,
            transform,
            inputTransform,
            clip,
            insideFramebuffer || needsFramebuffer,
            scrollState,
            scrollbarReserves,
            layouts,
        )
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
        insideFramebuffer: Boolean,
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
        if (node is TextNode) {
            enqueuePopupChildren(
                node,
                resolved,
                content,
                parentRect,
                transform,
                inputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
            placeTextInlineChildren(
                node,
                resolved,
                style,
                viewport,
                parentRect,
                transform,
                inputTransform,
                clip,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
            return
        }
        if (node is TextFieldNode) {
            placeTextFieldInlineChildren(
                node,
                resolved,
                style,
                viewport,
                parentRect,
                transform,
                inputTransform,
                clip,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
            return
        }
        enqueuePopupChildren(
            node,
            resolved,
            content,
            parentRect,
            transform,
            inputTransform,
            insideFramebuffer,
            scrollState,
            scrollbarReserves,
            layouts,
        )
        node.layout.policy().place(
            this,
            ChildPlacementScope(
                node = node,
                resolved = resolved,
                style = style,
                layout = node.layout,
                content = viewport,
                parentRect = parentRect,
                transform = transform,
                inputTransform = inputTransform,
                clip = clip,
                insideFramebuffer = insideFramebuffer,
                scrollState = scrollState,
                scrollbarReserves = scrollbarReserves,
                layouts = layouts,
            )
        )
    }

    private fun placeTextInlineChildren(
        node: TextNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val widgets = layoutChildren(node).associateBy { it.id }
        if (widgets.isEmpty()) return
        val textLayout = layouts[node]?.textLayout
            ?: layoutTextNode(node, resolved, style, content, scrollbarReserves)
        val placed = mutableSetOf<UiNode>()
        for (line in textLayout.lines) {
            for (fragment in line.fragments) {
                if (fragment !is UiInlineWidgetRun) continue
                val child = widgets[fragment.widget.id] ?: continue
                val margin = resolved[child].margin.resolve(content.width, content.height)
                placed += child
                placeNode(
                    child,
                    resolved,
                    UiRect(
                        content.x + line.x + fragment.x + margin.left,
                        content.y + line.y + fragment.y + margin.top,
                        (fragment.width - margin.horizontal).coerceAtLeast(0f),
                        (fragment.height - margin.vertical).coerceAtLeast(0f),
                    ),
                    parentRect,
                    style,
                    clip,
                    transform,
                    inputTransform,
                    insideFramebuffer,
                    scrollState,
                    scrollbarReserves,
                    layouts,
                )
            }
        }
        for (child in layoutChildren(node)) {
            if (child in placed) continue
            val measured =
                measureNode(child, resolved, content.width, content.height, scrollbarReserves)
            placeNode(
                child,
                resolved,
                UiRect(content.x, content.y, measured.width, measured.height),
                parentRect,
                style,
                clip,
                transform,
                inputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
        }
    }

    private fun placeTextFieldInlineChildren(
        node: TextFieldNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val widgets = layoutChildren(node).associateBy { it.id }
        if (widgets.isEmpty() || style.textField.inlayHints != true) return
        val textLayout = layouts[node]?.textLayout
            ?: layoutTextFieldNode(node, resolved, style, content, scrollbarReserves)
        val textOffset = textFieldTextOffset(node, style, layouts[node] ?: return)
        for (line in textLayout.lines) {
            for (fragment in line.fragments) {
                if (fragment !is UiInlineWidgetRun) continue
                val child = widgets[fragment.widget.id] ?: continue
                val margin = resolved[child].margin.resolve(content.width, content.height)
                placeNode(
                    child,
                    resolved,
                    UiRect(
                        content.x + textOffset + line.x + fragment.x + margin.left,
                        content.y + line.y + fragment.y + margin.top,
                        (fragment.width - margin.horizontal).coerceAtLeast(0f),
                        (fragment.height - margin.vertical).coerceAtLeast(0f),
                    ),
                    parentRect,
                    style,
                    clip,
                    transform,
                    inputTransform,
                    insideFramebuffer,
                    scrollState,
                    scrollbarReserves,
                    layouts,
                )
            }
        }
    }

    private fun layoutTextNode(
        node: TextNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): UiTextLayout {
        val widgetMetrics = measureInlineWidgetMetrics(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
        )
        val textHeight = if (style.input.scrollable) Float.POSITIVE_INFINITY else content.height
        return UiTextLayouter.layout(
            node.content.resolve().toRichText(widgetMetrics),
            content.width,
            textHeight,
            style.textWrap,
            style.textAlign,
            style.fontSize,
            style.fontFamily,
            lineSpacing = style.lineSpacing,
            spaceWidth = style.spaceWidth,
        )
    }

    private fun layoutTextFieldNode(
        node: TextFieldNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): UiTextLayout {
        val layout = temporaryTextFieldLayoutNode(node, content)
        val widgetMetrics = measureInlineWidgetMetrics(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
        )
        return textFieldDisplayLayout(node, style, layout, widgetMetrics)
    }

    private fun enqueuePopupChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        if (popupChildren(node).isEmpty()) return
        val stack = placementStack
        if (stack != null) {
            stack.addLast(
                PopupPlacementTask(
                    node,
                    resolved,
                    content,
                    parentRect,
                    transform,
                    inputTransform,
                    insideFramebuffer,
                    scrollState,
                    scrollbarReserves,
                    layouts,
                )
            )
            return
        }
        placePopupChildrenNow(
            PopupPlacementTask(
                node,
                resolved,
                content,
                parentRect,
                transform,
                inputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
        )
    }

    private fun placePopupChildrenNow(task: PopupPlacementTask) {
        val popups = popupChildren(task.node)
        if (popups.isEmpty()) return
        val parentStyle = task.resolved[task.node]
        for (popup in popups) {
            val measured = measureNode(
                popup,
                task.resolved,
                task.content.width,
                task.content.height,
                task.scrollbarReserves,
            )
            val anchor = popup.anchor.resolvePopupAnchor(task.content, task.resolved, task.layouts)
            val rect = popup.alignment.popupRect(anchor, measured)
            placeNode(
                popup,
                task.resolved,
                rect,
                task.parentRect,
                parentStyle,
                null,
                task.transform,
                task.inputTransform,
                task.insideFramebuffer,
                task.scrollState,
                task.scrollbarReserves,
                task.layouts,
            )
        }
    }

    internal fun placeCustomChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        layout: UiLayout.Custom,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val result = measureCustomLayout(
            node,
            resolved,
            layout,
            content.width,
            content.height,
            scrollbarReserves,
        )
        for (placement in result.placements) {
            val child = placement.placeable.node
            val childStyle = resolved[child]
            val position = childStyle.position.resolve(content.width, content.height)
            placeNode(
                child,
                resolved,
                UiRect(
                    content.x + placement.x + position.x,
                    content.y + placement.y + position.y,
                    placement.placeable.width,
                    placement.placeable.height,
                ),
                parentRect,
                resolved[node],
                clip,
                transform,
                inputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
        }
    }

    internal fun placeRowChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val gap = style.gap.resolve(content.width)
        val measured = measureFlowChildren(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
            allowWidthOverflow = style.input.scrollable,
            allowHeightOverflow = style.input.scrollable,
        )
        val grown = growRowChildren(
            measured,
            content.width,
            gap,
            resolved,
            scrollbarReserves,
            allowOverflow = style.input.scrollable
        )
        val totalWidth = grown.sumOfOuterWidth() + gap * (grown.size - 1).coerceAtLeast(0)
        val mainAlign = grown.singleChildMainAxisAlign { it.alignHorizontal } ?: style.childAlignHorizontal(node.layout)
        var x = content.x + mainAlign.mainStartOffset(content.width, totalWidth, grown.size, gap)
        val actualGap = mainAlign.mainGap(content.width, totalWidth, grown.size, gap)
        for (child in grown) {
            val childStyle = child.style
            val position = childStyle.position.resolve(content.width, content.height)
            val align =
                childStyle.alignVertical.takeUnless { it == UiAlign.AUTO } ?: style.childAlignVertical(node.layout)
                ?: UiAlign.START
            val height = childStyle.size.height
            val childHeight = height.resolveHeight(align, childStyle, child, content)
            val childWidth = child.size.width.coerceAtMost(
                (content.width - child.margin.left - child.margin.right).coerceAtLeast(0f)
            )
            val y = content.y + align.crossOffset(content.height, childHeight, child.margin.top, child.margin.bottom)
            val rect = UiRect(x + child.margin.left + position.x, y + position.y, childWidth, childHeight)
            placeNode(
                child.node,
                resolved,
                rect,
                parentRect,
                style,
                clip,
                transform,
                inputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
            x += child.margin.left + childWidth + child.margin.right + actualGap
        }
    }

    internal fun placeColumnChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val gap = style.gap.resolve(content.height)
        val measured = measureFlowChildren(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
            allowWidthOverflow = style.input.scrollable,
            allowHeightOverflow = style.input.scrollable,
        )
        val grown = growColumnChildren(
            measured,
            content.height,
            gap,
            resolved,
            scrollbarReserves,
            allowOverflow = style.input.scrollable
        )
        val totalHeight = grown.sumOfOuterHeight() + gap * (grown.size - 1).coerceAtLeast(0)
        val mainAlign = grown.singleChildMainAxisAlign { it.alignVertical } ?: style.childAlignVertical(node.layout)
        var y = content.y + mainAlign.mainStartOffset(content.height, totalHeight, grown.size, gap)
        val actualGap = mainAlign.mainGap(content.height, totalHeight, grown.size, gap)
        for (child in grown) {
            val childStyle = child.style
            val position = childStyle.position.resolve(content.width, content.height)
            val align =
                childStyle.alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: style.childAlignHorizontal(node.layout)
                ?: UiAlign.STRETCH
            val width = childStyle.size.width
            val childWidth = width.resolveWidth(align, childStyle, child, content)
                .coerceAtMost((content.width - child.margin.left - child.margin.right).coerceAtLeast(0f))
            val childHeight = child.size.height.coerceAtMost(
                (content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f)
            )
            val x = content.x + align.crossOffset(content.width, childWidth, child.margin.left, child.margin.right)
            val rect = UiRect(x + position.x, y + child.margin.top + position.y, childWidth, childHeight)
            placeNode(
                child.node,
                resolved,
                rect,
                parentRect,
                style,
                clip,
                transform,
                inputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
            y += child.margin.top + childHeight + child.margin.bottom + actualGap
        }
    }

    internal fun placeLazyColumnChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val gap = style.gap.resolve(content.height)
        val measured = measureFlowChildren(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
            allowWidthOverflow = style.input.scrollable,
            allowHeightOverflow = true,
        )
        val totalHeight = measured.sumOfOuterHeight() + gap * (measured.size - 1).coerceAtLeast(0)
        val mainAlign = measured.singleChildMainAxisAlign { it.alignVertical } ?: style.childAlignVertical(node.layout)
        val scrollOffset = scrollState.offset(node)
        val unscrolledTop = content.y + scrollOffset.y
        layouts[node]?.let { layoutNode ->
            layouts[node] = layoutNode.copy(
                virtualContentBounds = UiRect(
                    content.x,
                    unscrolledTop + mainAlign.mainStartOffset(content.height, totalHeight, measured.size, gap),
                    measured.maxOfOuterWidth(),
                    totalHeight,
                )
            )
        }
        var y = content.y + mainAlign.mainStartOffset(content.height, totalHeight, measured.size, gap)
        val actualGap = mainAlign.mainGap(content.height, totalHeight, measured.size, gap)
        val visibleTop = content.y + scrollOffset.y
        val visibleBottom = visibleTop + content.height
        for (child in measured) {
            val childStyle = child.style
            val position = childStyle.position.resolve(content.width, content.height)
            val align =
                childStyle.alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: style.childAlignHorizontal(node.layout)
                ?: UiAlign.STRETCH
            val width = childStyle.size.width
            val childWidth = width.resolveWidth(align, childStyle, child, content)
                .coerceAtMost((content.width - child.margin.left - child.margin.right).coerceAtLeast(0f))
            val childHeight = child.size.height
            val x = content.x + align.crossOffset(content.width, childWidth, child.margin.left, child.margin.right)
            val rect = UiRect(x + position.x, y + child.margin.top + position.y, childWidth, childHeight)
            if (rect.y + rect.height > visibleTop && rect.y < visibleBottom) {
                placeNode(
                    child.node,
                    resolved,
                    rect,
                    parentRect,
                    style,
                    clip,
                    transform,
                    inputTransform,
                    insideFramebuffer,
                    scrollState,
                    scrollbarReserves,
                    layouts,
                )
            }
            y += child.margin.top + childHeight + child.margin.bottom + actualGap
        }
    }

    internal fun placeLazyRowChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        val gap = style.gap.resolve(content.width)
        val measured = measureFlowChildren(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
            allowWidthOverflow = true,
            allowHeightOverflow = style.input.scrollable,
        )
        val totalWidth = measured.sumOfOuterWidth() + gap * (measured.size - 1).coerceAtLeast(0)
        val mainAlign =
            measured.singleChildMainAxisAlign { it.alignHorizontal } ?: style.childAlignHorizontal(node.layout)
        val scrollOffset = scrollState.offset(node)
        val unscrolledLeft = content.x + scrollOffset.x
        layouts[node]?.let { layoutNode ->
            layouts[node] = layoutNode.copy(
                virtualContentBounds = UiRect(
                    unscrolledLeft + mainAlign.mainStartOffset(content.width, totalWidth, measured.size, gap),
                    content.y,
                    totalWidth,
                    measured.maxOfOuterHeight(),
                )
            )
        }
        var x = content.x + mainAlign.mainStartOffset(content.width, totalWidth, measured.size, gap)
        val actualGap = mainAlign.mainGap(content.width, totalWidth, measured.size, gap)
        val visibleLeft = content.x + scrollOffset.x
        val visibleRight = visibleLeft + content.width
        for (child in measured) {
            val childStyle = child.style
            val position = childStyle.position.resolve(content.width, content.height)
            val align =
                childStyle.alignVertical.takeUnless { it == UiAlign.AUTO } ?: style.childAlignVertical(node.layout)
                ?: UiAlign.START
            val height = childStyle.size.height
            val childHeight = height.resolveHeight(align, childStyle, child, content)
            val childWidth = child.size.width
            val y = content.y + align.crossOffset(content.height, childHeight, child.margin.top, child.margin.bottom)
            val rect = UiRect(x + child.margin.left + position.x, y + position.y, childWidth, childHeight)
            if (rect.x + rect.width > visibleLeft && rect.x < visibleRight) {
                placeNode(
                    child.node,
                    resolved,
                    rect,
                    parentRect,
                    style,
                    clip,
                    transform,
                    inputTransform,
                    insideFramebuffer,
                    scrollState,
                    scrollbarReserves,
                    layouts,
                )
            }
            x += child.margin.left + childWidth + child.margin.right + actualGap
        }
    }

    internal fun placeFreeChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        parentRect: UiRect,
        transform: UiMatrix4,
        inputTransform: UiMatrix4,
        clip: UiRect?,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
        for (child in measureFlowChildren(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
            allowWidthOverflow = style.input.scrollable,
            allowHeightOverflow = style.input.scrollable,
        )) {
            val position = child.style.position.resolve(content.width, content.height)
            val alignX = child.style.effectiveAlignHorizontal(style, node.layout) ?: UiAlign.START
            val alignY = child.style.effectiveAlignVertical(style, node.layout) ?: UiAlign.START
            val width = child.style.size.width
            val height = child.style.size.height
            val childWidth = if (width.dependsOnAvailableSpace) {
                width.resolveWidth(alignX, child.style, child, content)
            } else child.size.width
            val childHeight = if (height.dependsOnAvailableSpace) {
                height.resolveHeight(alignY, child.style, child, content)
            } else child.size.height
            val x =
                content.x + alignX.crossOffset(content.width, childWidth, child.margin.left, child.margin.right)
            val y =
                content.y + alignY.crossOffset(content.height, childHeight, child.margin.top, child.margin.bottom)
            val rect = UiRect(x + position.x, y + position.y, childWidth, childHeight)
            placeNode(
                child.node,
                resolved,
                rect,
                parentRect,
                style,
                clip,
                transform,
                inputTransform,
                insideFramebuffer,
                scrollState,
                scrollbarReserves,
                layouts,
            )
        }
    }

    private fun measureFlowChildren(
        node: UiNode,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        deferFlexibleWidth: Boolean = false,
        deferFlexibleHeight: Boolean = false,
        allowWidthOverflow: Boolean = false,
        allowHeightOverflow: Boolean = false,
    ): List<MeasuredChild> {
        return measureFlowChildren(
            layoutChildren(node),
            resolved,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            deferFlexibleWidth = deferFlexibleWidth,
            deferFlexibleHeight = deferFlexibleHeight,
            allowWidthOverflow = allowWidthOverflow,
            allowHeightOverflow = allowHeightOverflow,
        )
    }

    private fun measureFlowChildren(
        children: List<UiNode>,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        deferFlexibleWidth: Boolean = false,
        deferFlexibleHeight: Boolean = false,
        allowWidthOverflow: Boolean = false,
        allowHeightOverflow: Boolean = false,
    ): List<MeasuredChild> {
        if (children.isEmpty()) return emptyList()
        val measured = ArrayList<MeasuredChild>(children.size)
        for (child in children) {
            val style = resolved[child]
            val margin = style.margin.resolve(availableWidth, availableHeight)
            val size = measureNode(
                child,
                resolved,
                availableWidth,
                availableHeight,
                scrollbarReserves,
                deferFlexibleWidth = deferFlexibleWidth,
                deferFlexibleHeight = deferFlexibleHeight,
                allowWidthOverflow = allowWidthOverflow,
                allowHeightOverflow = allowHeightOverflow,
            )
            measured += MeasuredChild(
                node = child,
                style = style,
                size = size,
                margin = margin,
            )
        }
        return measured
    }

    private fun measureCustomLayout(
        node: UiNode,
        resolved: ResolvedUiTree,
        layout: UiLayout.Custom,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): UiMeasureResult {
        val constraints = UiConstraints(
            maxWidth = availableWidth.coerceAtLeast(0f),
            maxHeight = availableHeight.coerceAtLeast(0f),
        )
        val measurables = layoutChildren(node).map { child ->
            EngineMeasurable(child, resolved, scrollbarReserves)
        }
        val scope = UiMeasureScope()
        val result = with(layout.measurePolicy) {
            scope.measure(measurables, constraints)
        }
        return result.copy(
            width = constraints.constrainWidth(result.width),
            height = constraints.constrainHeight(result.height),
        )
    }

    internal fun growRowChildren(
        children: List<MeasuredChild>,
        availableWidth: Float,
        gap: Float,
        resolved: ResolvedUiTree,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        allowOverflow: Boolean = false,
    ): List<MeasuredChild> {
        val gapTotal = gap * (children.size - 1).coerceAtLeast(0)
        var fixedWidth = 0f
        var flexibleMargins = 0f
        var flexibleCount = 0
        var totalWeight = 0f
        for (child in children) {
            if (child.isRowFlexible) {
                flexibleCount++
                flexibleMargins += child.margin.left + child.margin.right
                totalWeight += child.rowWeight()
            } else {
                fixedWidth += child.margin.left + child.size.width + child.margin.right
            }
        }
        val availableForFlexible = (availableWidth - fixedWidth - flexibleMargins - gapTotal).coerceAtLeast(0f)
        val distributed = if (flexibleCount == 0 || totalWeight <= 0f) {
            children
        } else {
            val next = ArrayList<MeasuredChild>(children.size)
            for (child in children) {
                if (!child.isRowFlexible) {
                    next += child
                    continue
                }
                val weight = child.rowWeight()
                val targetWidth = availableForFlexible * (weight / totalWeight)
                val fixedWidthOverride = targetWidth.takeUnless { child.isWrappedAutoText }
                next += child.copy(
                    size = measureNode(
                        child.node,
                        resolved,
                        targetWidth,
                        child.size.height,
                        scrollbarReserves,
                        widthOverride = fixedWidthOverride,
                    )
                )
            }
            next
        }
        var outerWidth = 0f
        for (child in distributed) {
            outerWidth += child.margin.left + child.size.width + child.margin.right
        }
        val overflow = (outerWidth + gapTotal - availableWidth).coerceAtLeast(0f)
        if (allowOverflow) return distributed
        if (overflow <= 0f) return distributed
        var shrinkableWidth = 0f
        for (child in distributed) {
            if (child.style.size.width !is UiLength.Px && child.size.width > 0f) shrinkableWidth += child.size.width
        }
        if (shrinkableWidth <= 0f) return distributed
        val shrunk = ArrayList<MeasuredChild>(distributed.size)
        for (child in distributed) {
            if (child.style.size.width is UiLength.Px || child.size.width <= 0f) {
                shrunk += child
                continue
            }
            val targetWidth = (child.size.width - overflow * (child.size.width / shrinkableWidth)).coerceAtLeast(0f)
            val fixedWidthOverride = targetWidth.takeUnless { child.isWrappedAutoText }
            shrunk += child.copy(
                size = measureNode(
                    child.node,
                    resolved,
                    targetWidth,
                    child.size.height,
                    scrollbarReserves,
                    widthOverride = fixedWidthOverride,
                )
            )
        }
        return shrunk
    }

    internal fun growColumnChildren(
        children: List<MeasuredChild>,
        availableHeight: Float,
        gap: Float,
        resolved: ResolvedUiTree,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        allowOverflow: Boolean = false,
    ): List<MeasuredChild> {
        val gapTotal = gap * (children.size - 1).coerceAtLeast(0)
        var fixedHeight = 0f
        var flexibleMargins = 0f
        var flexibleCount = 0
        var totalWeight = 0f
        for (child in children) {
            if (child.isColumnFlexible) {
                flexibleCount++
                flexibleMargins += child.margin.top + child.margin.bottom
                totalWeight += child.columnWeight()
            } else {
                fixedHeight += child.margin.top + child.size.height + child.margin.bottom
            }
        }
        val availableForFlexible = (availableHeight - fixedHeight - flexibleMargins - gapTotal).coerceAtLeast(0f)
        val distributed = if (flexibleCount == 0 || totalWeight <= 0f) {
            children
        } else {
            val next = ArrayList<MeasuredChild>(children.size)
            for (child in children) {
                if (!child.isColumnFlexible) {
                    next += child
                    continue
                }
                val weight = child.columnWeight()
                val targetHeight = availableForFlexible * (weight / totalWeight)
                next += child.copy(
                    size = measureNode(
                        child.node,
                        resolved,
                        child.size.width,
                        targetHeight,
                        scrollbarReserves,
                        heightOverride = targetHeight,
                    )
                )
            }
            next
        }
        var outerHeight = 0f
        for (child in distributed) {
            outerHeight += child.margin.top + child.size.height + child.margin.bottom
        }
        val overflow = (outerHeight + gapTotal - availableHeight).coerceAtLeast(0f)
        if (allowOverflow) return distributed
        if (overflow <= 0f) return distributed
        var shrinkableHeight = 0f
        for (child in distributed) {
            if (child.style.size.height !is UiLength.Px && child.size.height > 0f) shrinkableHeight += child.size.height
        }
        if (shrinkableHeight <= 0f) return distributed
        val shrunk = ArrayList<MeasuredChild>(distributed.size)
        for (child in distributed) {
            if (child.style.size.height is UiLength.Px || child.size.height <= 0f) {
                shrunk += child
                continue
            }
            val targetHeight =
                (child.size.height - overflow * (child.size.height / shrinkableHeight)).coerceAtLeast(0f)
            shrunk += child.copy(
                size = measureNode(
                    child.node,
                    resolved,
                    child.size.width,
                    targetHeight,
                    scrollbarReserves,
                    heightOverride = targetHeight,
                )
            )
        }
        return shrunk
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
        allowWidthOverflow: Boolean = false,
        allowHeightOverflow: Boolean = false,
    ): LayoutSize {
        val style = resolved[node]
        val referenceWidth = availableWidth.coerceAtLeast(0f)
        val referenceHeight = availableHeight.coerceAtLeast(0f)
        val insets =
            style.outerInsets(referenceWidth, referenceHeight, scrollbarReserves[node] ?: UiScrollbarReserve.None)
        var width = widthOverride ?: style.size.width.resolveOrNull(referenceWidth, deferFlexibleWidth)
        var height = heightOverride ?: style.size.height.resolveOrNull(referenceHeight, deferFlexibleHeight)
        style.aspectRatio?.let { ratio ->
            if (width == null && height != null) width = height * ratio
            if (height == null && width != null) height = width / ratio
        }
        if (width != null && height != null) {
            return constrainMeasuredSize(
                width = width,
                height = height,
                style = style,
                referenceWidth = referenceWidth,
                referenceHeight = referenceHeight,
                allowWidthOverflow = allowWidthOverflow,
                allowHeightOverflow = allowHeightOverflow,
            )
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
            knownContentHeight = height?.let { (it - insets.vertical).coerceAtLeast(0f) },
        )
        width = width ?: (intrinsic.width + insets.horizontal)
        height = height ?: (intrinsic.height + insets.vertical)
        style.aspectRatio?.let { ratio ->
            val resolvedWidth = width
            val resolvedHeight = height
            if (style.size.width is UiLength.Auto && style.size.height !is UiLength.Auto) {
                width = resolvedHeight * ratio
            }
            if (style.size.height is UiLength.Auto && style.size.width !is UiLength.Auto) {
                height = resolvedWidth / ratio
            }
        }
        val finalWidth = requireNotNull(width)
        val finalHeight = requireNotNull(height)
        val widthReference =
            if (allowWidthOverflow && style.size.width is UiLength.Auto) Float.POSITIVE_INFINITY else referenceWidth
        val heightReference =
            if (allowHeightOverflow && style.size.height is UiLength.Auto) Float.POSITIVE_INFINITY else referenceHeight
        var constrainedWidth = finalWidth.coerceIn(style.minSize.width, style.maxSize.width, widthReference)
        var constrainedHeight = finalHeight.coerceIn(style.minSize.height, style.maxSize.height, heightReference)
        val widthConstrained = abs(constrainedWidth - finalWidth) > ConstraintReflowEpsilon
        val shouldReflowConstrainedWidth =
            widthConstrained &&
                    (node is TextNode ||
                            heightOverride == null && style.size.height is UiLength.Auto ||
                            widthOverride == null && style.size.width is UiLength.Auto)

        if (!shouldReflowConstrainedWidth) return LayoutSize(constrainedWidth, constrainedHeight)

        val constrainedContentWidth = (constrainedWidth - insets.horizontal).coerceAtLeast(0f)
        val reflowed = intrinsicSize(
            node,
            resolved,
            style,
            constrainedContentWidth,
            childAvailableHeight,
            scrollbarReserves,
            knownContentWidth = constrainedContentWidth,
            knownContentHeight = null,
        )
        if (widthOverride == null && style.size.width is UiLength.Auto) {
            constrainedWidth = (reflowed.width + insets.horizontal)
                .coerceIn(style.minSize.width, style.maxSize.width, widthReference)
        }
        if (heightOverride == null && style.size.height is UiLength.Auto) {
            constrainedHeight = (reflowed.height + insets.vertical)
                .coerceIn(style.minSize.height, style.maxSize.height, heightReference)
        }

        return LayoutSize(
            width = constrainedWidth,
            height = constrainedHeight,
        )
    }

    private fun constrainMeasuredSize(
        width: Float,
        height: Float,
        style: ComputedStyle,
        referenceWidth: Float,
        referenceHeight: Float,
        allowWidthOverflow: Boolean,
        allowHeightOverflow: Boolean,
    ): LayoutSize {
        val widthReference =
            if (allowWidthOverflow && style.size.width is UiLength.Auto) Float.POSITIVE_INFINITY else referenceWidth
        val heightReference =
            if (allowHeightOverflow && style.size.height is UiLength.Auto) Float.POSITIVE_INFINITY else referenceHeight
        return LayoutSize(
            width = width.coerceIn(style.minSize.width, style.maxSize.width, widthReference),
            height = height.coerceIn(style.minSize.height, style.maxSize.height, heightReference),
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
        knownContentHeight: Float? = null,
    ): LayoutSize {
        return when (node) {
            is TextNode -> measureTextNode(
                node,
                resolved,
                style,
                availableWidth,
                availableHeight,
                scrollbarReserves,
                knownContentWidth,
            )

            is TextFieldNode -> measureTextFieldNode(
                node,
                resolved,
                style,
                availableWidth,
                availableHeight,
                scrollbarReserves,
                knownContentWidth,
            )
            else -> {
                if (layoutChildren(node).isEmpty()) return replacedIntrinsicSize(node, style)

                val customLayout = node.layout as? UiLayout.Custom
                if (customLayout != null) {
                    measureCustomContainer(node, resolved, customLayout, availableWidth, availableHeight, scrollbarReserves)
                } else {
                    measureStandardContainer(node, resolved, style, availableWidth, availableHeight, scrollbarReserves, knownContentWidth, knownContentHeight)
                }
            }
        }
    }

    private fun measureTextNode(
        node: TextNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        knownContentWidth: Float?,
    ): LayoutSize {
        val widgetMetrics = measureInlineWidgetMetrics(
            node, resolved, availableWidth, availableHeight, scrollbarReserves
        )
        return UiTextLayouter.measure(
            richText = node.content.resolve().toRichText(widgetMetrics),
            availableWidth = availableWidth,
            knownWidth = knownContentWidth,
            wrap = style.textWrap,
            fontSize = style.fontSize,
            fontFamily = style.fontFamily,
            lineSpacing = style.lineSpacing,
            spaceWidth = style.spaceWidth,
        )
    }

    private fun measureTextFieldNode(
        node: TextFieldNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        knownContentWidth: Float?,
    ): LayoutSize {
        val widgetMetrics = measureInlineWidgetMetrics(
            node,
            resolved,
            availableWidth,
            availableHeight,
            scrollbarReserves,
        )
        val layout = temporaryTextFieldLayoutNode(
            node,
            UiRect(0f, 0f, availableWidth, availableHeight),
        )
        val textOffset = textFieldTextOffset(node, style, layout)
        val textWidth = (availableWidth - textOffset).coerceAtLeast(1f)
        val knownTextWidth = knownContentWidth?.let { (it - textOffset).coerceAtLeast(1f) }
        val measured = UiTextLayouter.measure(
            richText = node.value.ifEmpty { node.placeholder }.toHighlightedRichText(
                highlighter = null,
                inlayHints = if (style.textField.inlayHints == true) node.currentInlayHints() else emptyList(),
                inlayStyle = textFieldInlayStyle(style),
                inlayWidgetMetrics = widgetMetrics,
            ),
            availableWidth = textWidth,
            knownWidth = knownTextWidth,
            wrap = textFieldWrap(style, node, knownTextWidth != null),
            fontSize = style.fontSize,
            fontFamily = style.fontFamily,
            preserveWhitespace = true,
            lineSpacing = style.lineSpacing,
            spaceWidth = style.spaceWidth,
        )
        return if (knownContentWidth == null) {
            measured.copy(width = textOffset + measured.width + TextFieldCaretWidth + TextFieldCaretVisibilityPadding)
        } else {
            measured
        }
    }

    private fun measureCustomContainer(
        node: UiNode,
        resolved: ResolvedUiTree,
        customLayout: UiLayout.Custom,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): LayoutSize {
        val result = measureCustomLayout(node, resolved, customLayout, availableWidth, availableHeight, scrollbarReserves)
        return LayoutSize(result.width, result.height)
    }

    private fun measureStandardContainer(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        knownContentWidth: Float?,
        knownContentHeight: Float?,
    ): LayoutSize {
        val children = measureFlowChildren(
            node,
            resolved,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            deferFlexibleWidth = style.size.width is UiLength.Auto,
            deferFlexibleHeight = style.size.height is UiLength.Auto,
            allowWidthOverflow = style.input.scrollable,
            allowHeightOverflow = style.input.scrollable,
        )

        val layout = node.layout
        val isHorizontal = layout == UiLayout.Row || layout == UiLayout.LazyRow
        val gap = style.gap.resolve(if (isHorizontal) availableWidth else availableHeight)

        return layout.policy().intrinsic(
            this,
            ChildIntrinsicScope(
                children = children,
                availableWidth = availableWidth,
                availableHeight = availableHeight,
                knownContentWidth = knownContentWidth,
                knownContentHeight = knownContentHeight,
                gap = gap,
                resolved = resolved,
                scrollbarReserves = scrollbarReserves,
            )
        )
    }

    private fun nodeBoxes(rect: UiRect, style: ComputedStyle, reserve: UiScrollbarReserve): NodeBoxes {
        val border = style.border.width.resolve(rect.width, rect.height)
        val padding = style.padding.resolve(rect.width, rect.height)
        val verticalScrollbar = style.scrollbar.resolved(rect.width)
        val horizontalScrollbar = style.scrollbar.resolved(rect.height)
        val scrollArea = UiRect(
            rect.x + border.left + padding.left,
            rect.y + border.top + padding.top,
            (rect.width - border.left - border.right - padding.left - padding.right).coerceAtLeast(0f),
            (rect.height - border.top - border.bottom - padding.top - padding.bottom).coerceAtLeast(0f),
        )
        return NodeBoxes(
            scrollArea = scrollArea,
            content = scrollArea.copy(
                width = (scrollArea.width - if (reserve.vertical) verticalScrollbar.gutter else 0f).coerceAtLeast(0f),
                height = (scrollArea.height - if (reserve.horizontal) horizontalScrollbar.gutter else 0f).coerceAtLeast(
                    0f
                ),
            )
        )
    }

    private fun measureInlineWidgetMetrics(
        node: UiNode,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): Map<String, UiInlineWidgetMetrics> {
        val children = layoutChildren(node)
        if (children.isEmpty()) return emptyMap()
        val metrics = LinkedHashMap<String, UiInlineWidgetMetrics>()
        for (child in children) {
            val id = child.id ?: continue
            val margin = resolved[child].margin.resolve(availableWidth, availableHeight)
            val size =
                measureNode(child, resolved, availableWidth, availableHeight, scrollbarReserves)
            metrics[id] = UiInlineWidgetMetrics(size.width + margin.horizontal, size.height + margin.vertical)
        }
        return metrics
    }

    private fun temporaryTextFieldLayoutNode(node: TextFieldNode, content: UiRect): UiLayoutNode {
        return UiLayoutNode(
            node = node,
            rect = content,
            content = content,
            clip = null,
            worldTransform = UiMatrix4.identity(),
            inputTransform = UiMatrix4.identity(),
            needsFramebuffer = false,
        )
    }

}
