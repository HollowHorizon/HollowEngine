package ru.hollowhorizon.hollowengine.client.ui

import java.util.ArrayDeque
import java.util.IdentityHashMap
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

private const val DirectTextTransformEpsilon = 0.0001f
private const val ScrollOverflowEpsilon = 0.01f
private const val ConstraintReflowEpsilon = 0.01f

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
)

private data class NodeBoxes(
    val scrollArea: UiRect,
    val content: UiRect,
)

private data class MeasureCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val bindingsHash: Int,
    val availableWidth: Float,
    val availableHeight: Float,
    val widthOverride: Float?,
    val heightOverride: Float?,
    val deferFlexibleWidth: Boolean,
    val deferFlexibleHeight: Boolean,
    val allowWidthOverflow: Boolean,
    val allowHeightOverflow: Boolean,
    val reserve: UiScrollbarReserve,
)

private data class FlowChildrenCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val bindingsHash: Int,
    val availableWidth: Float,
    val availableHeight: Float,
    val deferFlexibleWidth: Boolean,
    val deferFlexibleHeight: Boolean,
    val allowWidthOverflow: Boolean,
    val allowHeightOverflow: Boolean,
)

private data class InlineWidgetMetricsCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val bindingsHash: Int,
    val availableWidth: Float,
    val availableHeight: Float,
)

private data class IntrinsicSizeCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val bindingsHash: Int,
    val availableWidth: Float,
    val availableHeight: Float,
    val knownContentWidth: Float?,
    val knownContentHeight: Float?,
)

private data class MeasureRequest(
    val node: UiNode,
    val resolved: ResolvedUiTree,
    val availableWidth: Float,
    val availableHeight: Float,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val widthOverride: Float? = null,
    val heightOverride: Float? = null,
    val deferFlexibleWidth: Boolean = false,
    val deferFlexibleHeight: Boolean = false,
    val allowWidthOverflow: Boolean = false,
    val allowHeightOverflow: Boolean = false,
    val bindings: UiBindingContext = UiBindingContext(),
) {
    fun cacheKey(): MeasureCacheKey {
        val reserve = scrollbarReserves[node] ?: UiScrollbarReserve.None
        return MeasureCacheKey(
            nodeId = System.identityHashCode(node),
            subtreeRevision = node.layoutState.subtreeRevision,
            bindingsHash = bindings.root.hashCode(),
            availableWidth = availableWidth.layoutCacheValue(),
            availableHeight = availableHeight.layoutCacheValue(),
            widthOverride = widthOverride?.layoutCacheValue(),
            heightOverride = heightOverride?.layoutCacheValue(),
            deferFlexibleWidth = deferFlexibleWidth,
            deferFlexibleHeight = deferFlexibleHeight,
            allowWidthOverflow = allowWidthOverflow,
            allowHeightOverflow = allowHeightOverflow,
            reserve = reserve,
        )
    }
}

private class MeasureContext(
    private val compute: (MeasureRequest) -> LayoutSize,
) {
    private val activeKeys = HashSet<MeasureCacheKey>()
    private val measured = HashMap<MeasureCacheKey, LayoutSize>()
    private val measuredChildren = HashMap<FlowChildrenCacheKey, List<MeasuredChild>>()
    private val inlineWidgetMetrics = HashMap<InlineWidgetMetricsCacheKey, Map<String, UiInlineWidgetMetrics>>()
    private val intrinsicSizes = HashMap<IntrinsicSizeCacheKey, LayoutSize>()

    fun measure(request: MeasureRequest): LayoutSize {
        val key = request.cacheKey()
        measured[key]?.let { return it }
        if (!activeKeys.add(key)) return compute(request)
        return try {
            compute(request).also { measured[key] = it }
        } finally {
            activeKeys.remove(key)
        }
    }

    fun measureChildren(key: FlowChildrenCacheKey, compute: () -> List<MeasuredChild>): List<MeasuredChild> {
        measuredChildren[key]?.let { return it }
        return compute().also { measuredChildren[key] = it }
    }

    fun inlineMetrics(
        key: InlineWidgetMetricsCacheKey,
        compute: () -> Map<String, UiInlineWidgetMetrics>,
    ): Map<String, UiInlineWidgetMetrics> {
        inlineWidgetMetrics[key]?.let { return it }
        return compute().also { inlineWidgetMetrics[key] = it }
    }

    fun intrinsicSize(key: IntrinsicSizeCacheKey, compute: () -> LayoutSize): LayoutSize {
        intrinsicSizes[key]?.let { return it }
        return compute().also { intrinsicSizes[key] = it }
    }
}

private sealed interface PlacementTask

private data class NodePlacementTask(
    val node: UiNode,
    val resolved: ResolvedUiTree,
    val rect: UiRect,
    val parentRect: UiRect,
    val parentStyle: ComputedStyle?,
    val parentClip: UiRect?,
    val parentTransform: UiMatrix4,
    val parentInputTransform: UiMatrix4,
    val insideFramebuffer: Boolean,
    val scrollState: UiScrollState,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val layouts: MutableMap<UiNode, UiLayoutNode>,
    val bindings: UiBindingContext,
) : PlacementTask

private data class PopupPlacementTask(
    val node: UiNode,
    val resolved: ResolvedUiTree,
    val content: UiRect,
    val parentRect: UiRect,
    val transform: UiMatrix4,
    val inputTransform: UiMatrix4,
    val insideFramebuffer: Boolean,
    val scrollState: UiScrollState,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val layouts: MutableMap<UiNode, UiLayoutNode>,
    val bindings: UiBindingContext,
) : PlacementTask

private class LayoutPass(root: UiNode) {
    val nodes = ArrayList<UiNode>()
    val layoutChildren = IdentityHashMap<UiNode, List<UiNode>>()
    val popupChildren = IdentityHashMap<UiNode, List<PopupNode>>()

    init {
        val stack = ArrayDeque<UiNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            nodes += node
            val layout = ArrayList<UiNode>()
            val popups = ArrayList<PopupNode>()
            for (child in node.children) {
                if (child is PopupNode) {
                    popups += child
                } else {
                    layout += child
                }
            }
            layoutChildren[node] = layout
            popupChildren[node] = popups
            for (index in node.children.indices.reversed()) {
                stack.add(node.children[index])
            }
        }
    }
}

class UiLayoutPipeline {
    private var placementStack: ArrayDeque<PlacementTask>? = null
    private var layoutPass: LayoutPass? = null
    private var measureContext: MeasureContext? = null

    private inner class EngineMeasurable(
        override val node: UiNode,
        private val resolved: ResolvedUiTree,
        private val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        private val bindings: UiBindingContext,
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
                bindings = bindings,
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
        bindings: UiBindingContext = UiBindingContext(),
    ): UiLayoutResult {
        val initialLayouts = computeLayouts(resolved, width, height, scrollState, emptyMap(), bindings)
        val scrollbarReserves = detectScrollbarReserves(resolved, initialLayouts, bindings, ::layoutChildren)
        val layouts = if (scrollbarReserves.isEmpty()) {
            initialLayouts
        } else {
            computeLayouts(resolved, width, height, scrollState, scrollbarReserves, bindings)
        }
        val rangedLayouts = applyScrollRanges(resolved, layouts, scrollState, bindings, ::layoutChildren)
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
        bindings: UiBindingContext,
    ): Map<UiNode, UiLayoutNode> {
        val layouts = linkedMapOf<UiNode, UiLayoutNode>()
        val viewport = UiRect(0f, 0f, width, height)
        val previousStack = placementStack
        val previousPass = layoutPass
        val previousMeasureContext = measureContext
        val stack = ArrayDeque<PlacementTask>()
        try {
            layoutPass = LayoutPass(resolved.root)
            measureContext = MeasureContext(::measureNodeCached)
            val rootRect = rootRect(resolved, width, height, scrollbarReserves, bindings)
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
                bindings = bindings,
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
            measureContext = previousMeasureContext
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
        bindings: UiBindingContext,
    ): UiRect {
        val node = resolved.root
        val style = resolved[node]
        val margin = style.margin.resolve(width, height)
        val availableWidth = (width - margin.left - margin.right).coerceAtLeast(0f)
        val availableHeight = (height - margin.top - margin.bottom).coerceAtLeast(0f)
        val measured =
            measureNode(node, resolved, availableWidth, availableHeight, scrollbarReserves, bindings = bindings)
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
        bindings: UiBindingContext,
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
                bindings,
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
                bindings,
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
        bindings: UiBindingContext,
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
                bindings,
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
        val bindings = task.bindings
        val style = resolved[node]
        val boxes = nodeBoxes(rect, style, scrollbarReserves[node] ?: UiScrollbarReserve.None)
        val scrollOffset = scrollState.offset(node)
        val textLayout = if (node is TextNode) {
            layoutTextNode(node, resolved, style, boxes.content, scrollbarReserves, bindings)
        } else {
            null
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
            bindings
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
        bindings: UiBindingContext,
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
                bindings,
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
                bindings,
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
            bindings,
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
                bindings = bindings,
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
        bindings: UiBindingContext,
    ) {
        val widgets = layoutChildren(node).associateBy { it.id }
        if (widgets.isEmpty()) return
        val textLayout = layouts[node]?.textLayout
            ?: layoutTextNode(node, resolved, style, content, scrollbarReserves, bindings)
        val placed = mutableSetOf<UiNode>()
        for (line in textLayout.lines) {
            for (fragment in line.fragments) {
                if (fragment !is UiInlineWidgetRun) continue
                val child = widgets[fragment.widget.id] ?: continue
                placed += child
                placeNode(
                    child,
                    resolved,
                    UiRect(
                        content.x + line.x + fragment.x,
                        content.y + line.y + fragment.y,
                        fragment.width,
                        fragment.height,
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
                    bindings,
                )
            }
        }
        for (child in layoutChildren(node)) {
            if (child in placed) continue
            val measured =
                measureNode(child, resolved, content.width, content.height, scrollbarReserves, bindings = bindings)
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
                bindings,
            )
        }
    }

    private fun layoutTextNode(
        node: TextNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        content: UiRect,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        bindings: UiBindingContext,
    ): UiTextLayout {
        val widgetMetrics = measureInlineWidgetMetrics(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
            bindings,
        )
        val textHeight = if (style.input.scrollable) Float.POSITIVE_INFINITY else content.height
        return UiTextLayouter.layout(
            node.content.resolve(bindings).toRichText(widgetMetrics),
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
        bindings: UiBindingContext,
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
                    bindings,
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
                bindings,
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
                bindings = task.bindings
            )
            val anchor = popup.anchor.resolvePopupAnchor(task.content, task.resolved, task.layouts, task.bindings)
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
                task.bindings,
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
        bindings: UiBindingContext,
    ) {
        val result = measureCustomLayout(
            node,
            resolved,
            layout,
            content.width,
            content.height,
            scrollbarReserves,
            bindings,
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
                bindings,
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
        bindings: UiBindingContext,
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
            bindings = bindings,
        )
        val grown = growRowChildren(
            measured,
            content.width,
            gap,
            resolved,
            scrollbarReserves,
            bindings,
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
                bindings
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
        bindings: UiBindingContext,
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
            bindings = bindings,
        )
        val grown = growColumnChildren(
            measured,
            content.height,
            gap,
            resolved,
            scrollbarReserves,
            bindings,
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
                bindings
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
        bindings: UiBindingContext,
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
            bindings = bindings,
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
                    bindings
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
        bindings: UiBindingContext,
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
            bindings = bindings,
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
                    bindings
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
        bindings: UiBindingContext,
    ) {
        for (child in measureFlowChildren(
            node,
            resolved,
            content.width,
            content.height,
            scrollbarReserves,
            allowWidthOverflow = style.input.scrollable,
            allowHeightOverflow = style.input.scrollable,
            bindings = bindings,
        )) {
            val position = child.style.position.resolve(content.width, content.height)
            val alignX = child.style.effectiveAlignHorizontal(style, node.layout) ?: UiAlign.START
            val alignY = child.style.effectiveAlignVertical(style, node.layout) ?: UiAlign.START
            val x =
                content.x + alignX.crossOffset(content.width, child.size.width, child.margin.left, child.margin.right)
            val y =
                content.y + alignY.crossOffset(content.height, child.size.height, child.margin.top, child.margin.bottom)
            val rect = UiRect(x + position.x, y + position.y, child.size.width, child.size.height)
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
                bindings
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
        bindings: UiBindingContext = UiBindingContext(),
    ): List<MeasuredChild> {
        val key = FlowChildrenCacheKey(
            nodeId = System.identityHashCode(node),
            subtreeRevision = node.layoutState.subtreeRevision,
            bindingsHash = bindings.root.hashCode(),
            availableWidth = availableWidth.layoutCacheValue(),
            availableHeight = availableHeight.layoutCacheValue(),
            deferFlexibleWidth = deferFlexibleWidth,
            deferFlexibleHeight = deferFlexibleHeight,
            allowWidthOverflow = allowWidthOverflow,
            allowHeightOverflow = allowHeightOverflow,
        )
        return measureContext?.measureChildren(key) {
            measureFlowChildrenUncached(
                layoutChildren(node),
                resolved,
                availableWidth,
                availableHeight,
                scrollbarReserves,
                deferFlexibleWidth = deferFlexibleWidth,
                deferFlexibleHeight = deferFlexibleHeight,
                allowWidthOverflow = allowWidthOverflow,
                allowHeightOverflow = allowHeightOverflow,
                bindings = bindings,
            )
        } ?: measureFlowChildrenUncached(
            layoutChildren(node),
            resolved,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            deferFlexibleWidth = deferFlexibleWidth,
            deferFlexibleHeight = deferFlexibleHeight,
            allowWidthOverflow = allowWidthOverflow,
            allowHeightOverflow = allowHeightOverflow,
            bindings = bindings,
        )
    }

    private fun measureFlowChildrenUncached(
        children: List<UiNode>,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        deferFlexibleWidth: Boolean = false,
        deferFlexibleHeight: Boolean = false,
        allowWidthOverflow: Boolean = false,
        allowHeightOverflow: Boolean = false,
        bindings: UiBindingContext = UiBindingContext(),
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
                bindings = bindings,
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
        bindings: UiBindingContext,
    ): UiMeasureResult {
        val constraints = UiConstraints(
            maxWidth = availableWidth.coerceAtLeast(0f),
            maxHeight = availableHeight.coerceAtLeast(0f),
        )
        val measurables = layoutChildren(node).map { child ->
            EngineMeasurable(child, resolved, scrollbarReserves, bindings)
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
        bindings: UiBindingContext,
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
                        bindings = bindings
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
                    bindings = bindings
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
        bindings: UiBindingContext,
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
                        bindings = bindings
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
                    bindings = bindings
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
        bindings: UiBindingContext = UiBindingContext(),
    ): LayoutSize {
        val request = MeasureRequest(
            node = node,
            resolved = resolved,
            availableWidth = availableWidth,
            availableHeight = availableHeight,
            scrollbarReserves = scrollbarReserves,
            widthOverride = widthOverride,
            heightOverride = heightOverride,
            deferFlexibleWidth = deferFlexibleWidth,
            deferFlexibleHeight = deferFlexibleHeight,
            allowWidthOverflow = allowWidthOverflow,
            allowHeightOverflow = allowHeightOverflow,
            bindings = bindings,
        )
        return measureContext?.measure(request) ?: measureNodeCached(request)
    }

    private fun measureNodeCached(request: MeasureRequest): LayoutSize {
        val node = request.node
        val cacheKey = request.cacheKey()
        return node.layoutState.cachedMeasure(cacheKey) {
            measureNodeUncached(
                node = node,
                resolved = request.resolved,
                availableWidth = request.availableWidth,
                availableHeight = request.availableHeight,
                scrollbarReserves = request.scrollbarReserves,
                widthOverride = request.widthOverride,
                heightOverride = request.heightOverride,
                deferFlexibleWidth = request.deferFlexibleWidth,
                deferFlexibleHeight = request.deferFlexibleHeight,
                allowWidthOverflow = request.allowWidthOverflow,
                allowHeightOverflow = request.allowHeightOverflow,
                bindings = request.bindings,
            )
        }
    }

    private fun measureNodeUncached(
        node: UiNode,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        widthOverride: Float?,
        heightOverride: Float?,
        deferFlexibleWidth: Boolean,
        deferFlexibleHeight: Boolean,
        allowWidthOverflow: Boolean,
        allowHeightOverflow: Boolean,
        bindings: UiBindingContext,
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
            bindings = bindings,
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

        if (shouldReflowConstrainedWidth) return LayoutSize(constrainedWidth, constrainedHeight)

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
            bindings = bindings,
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
        bindings: UiBindingContext = UiBindingContext(),
    ): LayoutSize {
        val key = IntrinsicSizeCacheKey(
            nodeId = System.identityHashCode(node),
            subtreeRevision = node.layoutState.subtreeRevision,
            bindingsHash = bindings.root.hashCode(),
            availableWidth = availableWidth.layoutCacheValue(),
            availableHeight = availableHeight.layoutCacheValue(),
            knownContentWidth = knownContentWidth?.layoutCacheValue(),
            knownContentHeight = knownContentHeight?.layoutCacheValue(),
        )
        return measureContext?.intrinsicSize(key) {
            intrinsicSizeUncached(
                node,
                resolved,
                style,
                availableWidth,
                availableHeight,
                scrollbarReserves,
                knownContentWidth,
                knownContentHeight,
                bindings,
            )
        } ?: intrinsicSizeUncached(
            node,
            resolved,
            style,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            knownContentWidth,
            knownContentHeight,
            bindings,
        )
    }

    private fun intrinsicSizeUncached(
        node: UiNode,
        resolved: ResolvedUiTree,
        style: ComputedStyle,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        knownContentWidth: Float? = null,
        knownContentHeight: Float? = null,
        bindings: UiBindingContext = UiBindingContext(),
    ): LayoutSize {
        return when (node) {
            is TextNode -> measureTextNode(node, resolved, style, availableWidth, availableHeight, scrollbarReserves, knownContentWidth, bindings)
            is TextFieldNode -> measureTextFieldNode(node, style, availableWidth, knownContentWidth)
            else -> {
                if (layoutChildren(node).isEmpty()) return replacedIntrinsicSize(node, style)

                val customLayout = node.layout as? UiLayout.Custom
                if (customLayout != null) {
                    measureCustomContainer(node, resolved, customLayout, availableWidth, availableHeight, scrollbarReserves, bindings)
                } else {
                    measureStandardContainer(node, resolved, style, availableWidth, availableHeight, scrollbarReserves, knownContentWidth, knownContentHeight, bindings)
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
        bindings: UiBindingContext
    ): LayoutSize {
        val widgetMetrics = measureInlineWidgetMetrics(
            node, resolved, availableWidth, availableHeight, scrollbarReserves, bindings
        )
        return UiTextLayouter.measure(
            richText = node.content.resolve(bindings).toRichText(widgetMetrics),
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
        style: ComputedStyle,
        availableWidth: Float,
        knownContentWidth: Float?
    ): LayoutSize {
        val measured = UiTextLayouter.measure(
            text = node.value.ifEmpty { node.placeholder },
            availableWidth = availableWidth,
            knownWidth = knownContentWidth,
            wrap = textFieldWrap(style, node, knownContentWidth != null),
            fontSize = style.fontSize,
            fontFamily = style.fontFamily,
            preserveWhitespace = true,
            lineSpacing = style.lineSpacing,
            spaceWidth = style.spaceWidth,
        )
        return if (knownContentWidth == null) {
            measured.copy(width = measured.width + TextFieldCaretWidth + TextFieldCaretVisibilityPadding)
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
        bindings: UiBindingContext
    ): LayoutSize {
        val result = measureCustomLayout(
            node, resolved, customLayout, availableWidth, availableHeight, scrollbarReserves, bindings
        )
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
        bindings: UiBindingContext
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
            bindings = bindings,
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
                bindings = bindings,
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
        node: TextNode,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        bindings: UiBindingContext,
    ): Map<String, UiInlineWidgetMetrics> {
        val key = InlineWidgetMetricsCacheKey(
            nodeId = System.identityHashCode(node),
            subtreeRevision = node.layoutState.subtreeRevision,
            bindingsHash = bindings.root.hashCode(),
            availableWidth = availableWidth.layoutCacheValue(),
            availableHeight = availableHeight.layoutCacheValue(),
        )
        return measureContext?.inlineMetrics(key) {
            measureInlineWidgetMetricsUncached(
                layoutChildren(node),
                resolved,
                availableWidth,
                availableHeight,
                scrollbarReserves,
                bindings,
            )
        } ?: measureInlineWidgetMetricsUncached(
            layoutChildren(node),
            resolved,
            availableWidth,
            availableHeight,
            scrollbarReserves,
            bindings,
        )
    }

    private fun measureInlineWidgetMetricsUncached(
        children: Collection<UiNode>,
        resolved: ResolvedUiTree,
        availableWidth: Float,
        availableHeight: Float,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        bindings: UiBindingContext,
    ): Map<String, UiInlineWidgetMetrics> {
        if (children.isEmpty()) return emptyMap()
        val metrics = LinkedHashMap<String, UiInlineWidgetMetrics>()
        for (child in children) {
            val id = child.id ?: continue
            val size =
                measureNode(child, resolved, availableWidth, availableHeight, scrollbarReserves, bindings = bindings)
            metrics[id] = UiInlineWidgetMetrics(size.width, size.height)
        }
        return metrics
    }

}

private fun UiLength.resolveWidth(
    align: UiAlign,
    childStyle: ComputedStyle,
    child: MeasuredChild,
    content: UiRect,
): Float {
    return when (this) {
        UiLength.Auto -> if (align == UiAlign.STRETCH && child.canStretchAutoWidth) {
            (content.width - child.margin.left - child.margin.right).coerceAtLeast(0f)
                .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)
        } else {
            child.size.width
        }

        UiLength.Fill -> (content.width - child.margin.left - child.margin.right).coerceAtLeast(0f)
            .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)

        is UiLength.Percent -> ((content.width - child.margin.left - child.margin.right).coerceAtLeast(0f) * value)
            .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)

        is UiLength.Px -> child.size.width
            .coerceIn(childStyle.minSize.width, childStyle.maxSize.width, content.width)

        is UiLength.Addition -> first.resolveWidth(align, childStyle, child, content) + second.resolveWidth(
            align,
            childStyle,
            child,
            content
        )

        is UiLength.Substraction -> first.resolveWidth(align, childStyle, child, content) - second.resolveWidth(
            align,
            childStyle,
            child,
            content
        )
    }
}

private fun UiLength.resolveHeight(
    align: UiAlign,
    childStyle: ComputedStyle,
    child: MeasuredChild,
    content: UiRect,
): Float {
    return when (this) {
        UiLength.Auto -> if (align == UiAlign.STRETCH || childStyle.input.scrollable && child.node is TextNode) {
            (content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f)
                .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)
        } else {
            child.size.height
        }

        UiLength.Fill -> (content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f)
            .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)

        is UiLength.Percent -> ((content.height - child.margin.top - child.margin.bottom).coerceAtLeast(0f) * value)
            .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)

        is UiLength.Px -> child.size.height
            .coerceIn(childStyle.minSize.height, childStyle.maxSize.height, content.height)

        is UiLength.Addition -> first.resolveHeight(align, childStyle, child, content) + second.resolveHeight(
            align,
            childStyle,
            child,
            content
        )

        is UiLength.Substraction -> first.resolveHeight(align, childStyle, child, content) - second.resolveHeight(
            align,
            childStyle,
            child,
            content
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
    val verticalScrollbar = scrollbar.resolved(width)
    val horizontalScrollbar = scrollbar.resolved(height)
    return ResolvedUiInsets(
        left = border.left + padding.left,
        top = border.top + padding.top,
        right = border.right + padding.right + if (reserve.vertical) verticalScrollbar.gutter else 0f,
        bottom = border.bottom + padding.bottom + if (reserve.horizontal) horizontalScrollbar.gutter else 0f,
    )
}

private fun applyScrollRanges(
    resolved: ResolvedUiTree,
    layouts: Map<UiNode, UiLayoutNode>,
    scrollState: UiScrollState,
    bindings: UiBindingContext,
    layoutChildren: (UiNode) -> List<UiNode> = ::layoutChildren,
): Map<UiNode, UiLayoutNode> {
    val result = layouts.toMutableMap()
    for ((node, layout) in layouts) {
        val style = resolved[node]
        if (!style.input.scrollable) continue
        val childBounds = scrollableContentBounds(node, style, layout, layouts, bindings, layoutChildren)
        val range = UiScrollOffset(
            x = maxOf(0f, childBounds.x + childBounds.width - (layout.content.x + layout.content.width)),
            y = maxOf(0f, childBounds.y + childBounds.height - (layout.content.y + layout.content.height)),
        )
        val clamped = scrollState.clamp(node, range)
        val clip = layout.clip?.let { it.intersect(layout.content) } ?: layout.content
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

private fun detectScrollbarReserves(
    resolved: ResolvedUiTree,
    layouts: Map<UiNode, UiLayoutNode>,
    bindings: UiBindingContext,
    layoutChildren: (UiNode) -> List<UiNode> = ::layoutChildren,
): Map<UiNode, UiScrollbarReserve> {
    val reserves = linkedMapOf<UiNode, UiScrollbarReserve>()
    for ((node, layout) in layouts) {
        val style = resolved[node]
        if (!style.input.scrollable) continue
        val childBounds = scrollableContentBounds(node, style, layout, layouts, bindings, layoutChildren)
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
    bindings: UiBindingContext,
    layoutChildren: (UiNode) -> List<UiNode>,
): UiRect {
    layout.virtualContentBounds?.let { return it }
    if (node is TextNode || node is TextFieldNode) {
        val textLayout = if (node is TextNode) {
            layout.textLayout ?: textLayoutForScrollBounds(node, style, layout, layouts, bindings, layoutChildren)
        } else {
            val field = node as TextFieldNode
            val editWidth = textFieldTextWidth(field, style, layout)
            UiTextLayouter.layout(
                field.value.ifEmpty { field.placeholder },
                editWidth,
                Float.POSITIVE_INFINITY,
                textFieldWrap(style, field, constrainedWidth = true),
                style.textAlign,
                style.fontSize,
                style.fontFamily,
                preserveWhitespace = true,
                lineSpacing = style.lineSpacing,
                spaceWidth = style.spaceWidth,
            )
        }
        val textOffset = if (node is TextFieldNode) textFieldTextOffset(node, style, layout) else 0f
        val textViewportWidth =
            if (node is TextFieldNode) textFieldTextWidth(node, style, layout) else layout.content.width
        val horizontalPadding =
            if (node is TextFieldNode) textFieldHorizontalScrollPadding(textViewportWidth) else TextFieldCaretVisibilityPadding
        return UiRect(
            layout.content.x,
            layout.content.y,
            maxOf(
                layout.content.width,
                textOffset + textLayout.maxNaturalLineWidth() + TextFieldCaretWidth + horizontalPadding
            ),
            maxOf(layout.content.height, textLayout.height + TextFieldCaretVisibilityPadding),
        )
    }
    return layoutChildren(node).mapNotNull { layouts[it]?.rect?.withScroll(layout.scrollOffset) }.union()
        ?: layout.content
}

private fun textLayoutForScrollBounds(
    node: TextNode,
    style: ComputedStyle,
    layout: UiLayoutNode,
    layouts: Map<UiNode, UiLayoutNode>,
    bindings: UiBindingContext,
    layoutChildren: (UiNode) -> List<UiNode>,
): UiTextLayout {
    val widgetMetrics = layoutChildren(node).mapNotNull { child ->
        val id = child.id ?: return@mapNotNull null
        val rect = layouts[child]?.rect ?: return@mapNotNull null
        id to UiInlineWidgetMetrics(rect.width, rect.height)
    }.toMap()
    return UiTextLayouter.layout(
        node.content.resolve(bindings).toRichText(widgetMetrics),
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

internal fun List<MeasuredChild>.sumOfOuterWidth(): Float =
    sumOf { (it.margin.left + it.size.width + it.margin.right).toDouble() }.toFloat()

internal fun List<MeasuredChild>.sumOfOuterHeight(): Float =
    sumOf { (it.margin.top + it.size.height + it.margin.bottom).toDouble() }.toFloat()

internal fun List<MeasuredChild>.maxOfOuterWidth(): Float =
    maxOfOrNull { it.margin.left + it.size.width + it.margin.right } ?: 0f

internal fun List<MeasuredChild>.maxOfOuterHeight(): Float =
    maxOfOrNull { it.margin.top + it.size.height + it.margin.bottom } ?: 0f

internal fun List<MeasuredChild>.maxOfPositionedOuterWidth(referenceWidth: Float, referenceHeight: Float): Float {
    return maxOfOrNull { child ->
        val position = child.style.position.resolve(referenceWidth, referenceHeight)
        child.margin.left + position.x + child.size.width + child.margin.right
    } ?: 0f
}

internal fun List<MeasuredChild>.maxOfPositionedOuterHeight(referenceWidth: Float, referenceHeight: Float): Float {
    return maxOfOrNull { child ->
        val position = child.style.position.resolve(referenceWidth, referenceHeight)
        child.margin.top + position.y + child.size.height + child.margin.bottom
    } ?: 0f
}

private inline fun List<MeasuredChild>.singleChildMainAxisAlign(selector: (ComputedStyle) -> UiAlign): UiAlign? {
    if (size != 1) return null
    return selector(first().style).takeUnless { it == UiAlign.AUTO }
}

private val MeasuredChild.isRowFlexible: Boolean
    get() = style.size.width is UiLength.Fill ||
            style.size.width is UiLength.Percent ||
            style.grow > 0f ||
            node is TextNode && style.textWrap && style.size.width is UiLength.Auto

private val MeasuredChild.isWrappedAutoText: Boolean
    get() = node is TextNode &&
            style.textWrap &&
            style.size.width is UiLength.Auto &&
            style.grow <= 0f

private val MeasuredChild.isColumnFlexible: Boolean
    get() = style.size.height is UiLength.Fill ||
            style.size.height is UiLength.Percent ||
            style.grow > 0f ||
            node is TextNode && style.input.scrollable && style.size.height is UiLength.Auto

private val MeasuredChild.canStretchAutoWidth: Boolean
    get() = node !is TextFieldNode

private fun MeasuredChild.rowWeight(): Float {
    if (style.grow > 0f) return style.grow
    val width = style.size.width
    return width.rowWeight(size)
}

private fun UiLength.rowWeight(size: LayoutSize): Float {
    return when (this) {
        UiLength.Fill -> 1f
        is UiLength.Percent -> value
        UiLength.Auto -> size.width.coerceAtLeast(1f)
        is UiLength.Px -> 0f
        is UiLength.Addition -> first.rowWeight(size) + second.rowWeight(size)
        is UiLength.Substraction -> first.rowWeight(size) + second.rowWeight(size)
    }
}

private fun MeasuredChild.columnWeight(): Float {
    if (style.grow > 0f) return style.grow
    val height = style.size.height
    return height.columnWeight(size)
}

private fun UiLength.columnWeight(size: LayoutSize): Float {
    return when (this) {
        UiLength.Fill -> 1f
        is UiLength.Percent -> value
        UiLength.Auto -> size.height.coerceAtLeast(1f)
        is UiLength.Px -> 0f
        is UiLength.Addition -> first.columnWeight(size) + second.columnWeight(size)
        is UiLength.Substraction -> first.columnWeight(size) + second.columnWeight(size)
    }
}

private fun UiLength.resolveOrNull(reference: Float, deferFlexible: Boolean = false): Float? = when (this) {
    UiLength.Auto -> null
    UiLength.Fill -> if (deferFlexible) null else reference
    is UiLength.Px -> value
    is UiLength.Percent -> if (deferFlexible) null else reference * value
    is UiLength.Addition -> first.resolveOrNull(reference, deferFlexible)
        ?.let { it + (second.resolveOrNull(reference, deferFlexible) ?: return null) }

    is UiLength.Substraction -> first.resolveOrNull(reference, deferFlexible)
        ?.let { it - (second.resolveOrNull(reference, deferFlexible) ?: return null) }
}

private fun UiConstraints.fixedWidthOrNull(): Float? {
    return if (minWidth == maxWidth) minWidth else null
}

private fun UiConstraints.fixedHeightOrNull(): Float? {
    return if (minHeight == maxHeight) minHeight else null
}

private fun replacedIntrinsicSize(node: UiNode, style: ComputedStyle): LayoutSize {
    return when {
        node is SliderNode -> LayoutSize(DefaultSliderWidth, DefaultWidgetHeight)
        node is CheckboxNode -> LayoutSize(DefaultWidgetHeight, DefaultWidgetHeight)
        node is ImageNode || style.background is UiPaint.Image -> LayoutSize(
            DefaultReplacedElementSize,
            DefaultReplacedElementSize
        )

        else -> LayoutSize(0f, 0f)
    }
}

private const val DefaultReplacedElementSize = 32f
private const val DefaultSliderWidth = 120f
private const val DefaultWidgetHeight = 16f

private fun Float.coerceIn(min: UiLength, max: UiLength, reference: Float): Float {
    val minValue = min.resolve(reference, 0f)
    val maxValue = max.resolve(reference, Float.POSITIVE_INFINITY)
    return coerceIn(minValue, maxValue.coerceAtLeast(minValue))
}

private fun Float.layoutCacheValue(): Float {
    if (!isFinite()) return this
    return (this * 100f).toInt().toFloat() / 100f
}

private fun layoutChildren(node: UiNode): List<UiNode> {
    return node.children.filterNot { it is PopupNode }
}

private fun UiPopupAnchor.resolvePopupAnchor(
    parentContent: UiRect,
    resolved: ResolvedUiTree,
    layouts: Map<UiNode, UiLayoutNode>,
    bindings: UiBindingContext,
): UiRect {
    return when (this) {
        UiPopupAnchor.Parent -> parentContent
        is UiPopupAnchor.Cursor -> UiRect(
            x.takeIf { it.isFinite() } ?: bindings.pointerX(parentContent.x),
            y.takeIf { it.isFinite() } ?: bindings.pointerY(parentContent.y),
            0f,
            0f,
        )

        is UiPopupAnchor.Node -> {
            val anchorNode = resolved.styles.keys.firstOrNull { it.id == id }
            anchorNode?.let { layouts[it]?.rect } ?: parentContent
        }
    }
}

private fun UiPopupAlignment.popupRect(anchor: UiRect, size: LayoutSize): UiRect {
    val anchorX = anchor.x + anchorHorizontal.alignmentOffset(anchor.width)
    val anchorY = anchor.y + anchorVertical.alignmentOffset(anchor.height)
    val popupX = popupHorizontal.alignmentOffset(size.width)
    val popupY = popupVertical.alignmentOffset(size.height)
    return UiRect(anchorX - popupX + offsetX, anchorY - popupY + offsetY, size.width, size.height)
}

private fun UiAlign.alignmentOffset(size: Float): Float {
    return when (this) {
        UiAlign.CENTER -> size / 2f
        UiAlign.END -> size
        else -> 0f
    }
}

internal fun ComputedStyle.layoutFingerprint(): Int {
    return listOf(
        size,
        minSize,
        maxSize,
        aspectRatio,
        padding,
        margin,
        gap,
        alignHorizontal,
        alignVertical,
        alignItemsHorizontal,
        alignItemsVertical,
        alignItems,
        alignSelf,
        justifySelf,
        justifyContent,
        grow,
        position,
        border.width,
        input.scrollable,
        scrollbar,
        textWrap,
        textAlign,
        lineSpacing,
        spaceWidth,
        fontSize,
        fontFamily,
        textField,
    ).hashCode()
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

private fun ComputedStyle.effectiveAlignHorizontal(parent: ComputedStyle?, parentLayout: UiLayout?): UiAlign? {
    return alignHorizontal.takeUnless { it == UiAlign.AUTO } ?: parent?.childAlignHorizontal(parentLayout)
}

private fun ComputedStyle.effectiveAlignVertical(parent: ComputedStyle?, parentLayout: UiLayout?): UiAlign? {
    return alignVertical.takeUnless { it == UiAlign.AUTO } ?: parent?.childAlignVertical(parentLayout)
}

private fun ComputedStyle.childAlignHorizontal(layout: UiLayout?): UiAlign? {
    return alignItemsHorizontal.takeUnless { it == UiAlign.AUTO }
        ?: if (layout == UiLayout.Row || layout == UiLayout.LazyRow) justifyContent.takeUnless { it == UiAlign.AUTO }
        else alignItems.takeUnless { it == UiAlign.AUTO }
}

private fun ComputedStyle.childAlignVertical(layout: UiLayout?): UiAlign? {
    return alignItemsVertical.takeUnless { it == UiAlign.AUTO }
        ?: if (layout == UiLayout.Row || layout == UiLayout.LazyRow) alignItems.takeUnless { it == UiAlign.AUTO }
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

internal data class ResolvedUiInsets(
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
