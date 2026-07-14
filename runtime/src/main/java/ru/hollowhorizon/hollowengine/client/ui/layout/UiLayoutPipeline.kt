package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.*
import ru.hollowhorizon.hollowengine.client.ui.style.UiComputedStyle
import java.util.WeakHashMap

class UiLayoutPipeline {
    internal var layoutPass: LayoutPass? = null
    private val scrollbarCache = ScrollbarCache()
    private val measureCache = WeakHashMap<UiNode, NodeMeasureCache>()
    private var lastScrollbarReserves: Map<UiNode, UiScrollbarReserve> = emptyMap()

    internal val inlineFlowChildLayouts = HashMap<UiNode, List<InlinePlacement>>()
    internal val inlineFlowFlattened = HashSet<UiNode>()

    internal fun measureCacheFor(node: UiNode): NodeMeasureCache =
        measureCache.getOrPut(node) { NodeMeasureCache() }

    fun compute(
        resolved: UiNode,
        width: Float,
        height: Float,
        scrollState: UiScrollState = UiScrollState(),
    ): UiLayoutResult {
        val warmReserves = lastScrollbarReserves
        val warmLayouts = computeLayouts(resolved, width, height, scrollState, warmReserves)
        val layouts: MutableMap<UiNode, UiLayoutNode>
        if (detectScrollbarReserves(warmLayouts, ::layoutChildren) == warmReserves) {
            layouts = warmLayouts
        } else {
            val initialLayouts = if (warmReserves.isEmpty()) {
                warmLayouts
            } else {
                computeLayouts(resolved, width, height, scrollState, emptyMap())
            }
            val scrollbarReserves = detectScrollbarReserves(initialLayouts, ::layoutChildren)
            layouts = if (scrollbarReserves.isEmpty()) {
                initialLayouts
            } else {
                computeLayouts(resolved, width, height, scrollState, scrollbarReserves)
            }
            lastScrollbarReserves = scrollbarReserves
        }
        applyScrollRanges(layouts, scrollState, ::layoutChildren)
        val scrollbars = placeScrollbarNodes(layouts, scrollbarCache)
        val traversalOrder = layouts.keys.toList()
        return UiLayoutResult(
            root = resolved,
            nodes = layouts,
            traversalOrder = traversalOrder,
            scrollbars = scrollbars,
        )
    }

    private fun computeLayouts(
        resolved: UiNode,
        width: Float,
        height: Float,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): MutableMap<UiNode, UiLayoutNode> {
        val layouts = linkedMapOf<UiNode, UiLayoutNode>()
        val viewport = UiRect(0f, 0f, width, height)
        val previousPass = layoutPass
        inlineFlowChildLayouts.clear()
        inlineFlowFlattened.clear()
        try {
            layoutPass = LayoutPass(resolved)
            val rootRect = rootRect(resolved, width, height, scrollbarReserves)
            placeNode(
                node = resolved,
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
        } finally {
            layoutPass = previousPass
        }
        return layouts
    }

    internal fun layoutChildren(node: UiNode): List<UiNode> {
        return layoutPass?.layoutChildren?.get(node) ?: node.children
    }

    internal fun placeNode(
        node: UiNode,
        resolved: UiNode,
        rect: UiRect,
        parentRect: UiRect,
        parentStyle: UiComputedStyle?,
        parentClip: UiRect?,
        parentTransform: UiMatrix4,
        parentInputTransform: UiMatrix4,
        insideFramebuffer: Boolean,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
        layouts: MutableMap<UiNode, UiLayoutNode>,
    ) {
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

}
