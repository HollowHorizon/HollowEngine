package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.PopupNode
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.scroll.applyScrollRanges
import ru.hollowhorizon.hollowengine.client.ui.scroll.detectScrollbarReserves
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierSnapshot
import java.util.*

class UiLayoutPipeline {
    internal var layoutPass: LayoutPass? = null

    fun compute(
        resolved: UiNode,
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
            root = resolved,
            nodes = rangedLayouts,
            traversalOrder = traversalOrder,
            popupNodes = traversalOrder.filterIsInstance<PopupNode>(),
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
        parentStyle: UiModifierSnapshot?,
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
