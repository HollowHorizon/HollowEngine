package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.PopupNode
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.*
import ru.hollowhorizon.hollowengine.client.ui.style.UiComputedStyle

class UiLayoutPipeline {
    internal var layoutPass: LayoutPass? = null
    private val scrollbarCache = ScrollbarCache()

    internal val inlineFlowChildLayouts = HashMap<UiNode, List<InlinePlacement>>()
    internal val inlineFlowFlattened = HashSet<UiNode>()

    fun compute(
        resolved: UiNode,
        width: Float,
        height: Float,
        scrollState: UiScrollState = UiScrollState(),
    ): UiLayoutResult {
        val initialLayouts = computeLayouts(resolved, width, height, scrollState, emptyMap())
        val scrollbarReserves = detectScrollbarReserves(initialLayouts, ::layoutChildren)
        val layouts = if (scrollbarReserves.isEmpty()) {
            initialLayouts
        } else {
            computeLayouts(resolved, width, height, scrollState, scrollbarReserves)
        }
        val rangedLayouts = applyScrollRanges(layouts, scrollState, ::layoutChildren)
        val (withScrollbars, scrollbars) = placeScrollbarNodes(rangedLayouts, scrollbarCache)
        val traversalOrder = withScrollbars.keys.toList()
        return UiLayoutResult(
            root = resolved,
            nodes = withScrollbars,
            traversalOrder = traversalOrder,
            popupNodes = traversalOrder.filterIsInstance<PopupNode>(),
            scrollbars = scrollbars,
        )
    }

    private fun computeLayouts(
        resolved: UiNode,
        width: Float,
        height: Float,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): Map<UiNode, UiLayoutNode> {
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
        return layoutPass?.layoutChildren?.get(node) ?: node.children.filterNot { it is PopupNode }
    }

    internal fun popupChildren(node: UiNode): List<PopupNode> {
        return layoutPass?.popupChildren?.get(node) ?: node.children.filterIsInstance<PopupNode>()
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
