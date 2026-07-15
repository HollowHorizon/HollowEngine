package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiProfileFrame
import ru.hollowhorizon.hollowengine.client.ui.scroll.*
import ru.hollowhorizon.hollowengine.client.ui.style.UiComputedStyle
import java.util.WeakHashMap

class UiLayoutPipeline {
    internal var layoutPass: LayoutPass? = null
    internal var activeProfile: UiProfileFrame? = null
    internal var measureDepth = 0
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
        profile: UiProfileFrame? = null,
    ): UiLayoutResult {
        val previousProfile = activeProfile
        activeProfile = profile
        try {
            val warmReserves = lastScrollbarReserves
            val warmLayout = computeLayouts(resolved, width, height, scrollState, warmReserves)
            val finalLayout = if (detectScrollbarReserves(warmLayout.nodes, warmLayout::childrenOf) == warmReserves) {
                warmLayout
            } else {
                val initialLayout = if (warmReserves.isEmpty()) {
                    warmLayout
                } else {
                    computeLayouts(resolved, width, height, scrollState, emptyMap())
                }
                val scrollbarReserves = detectScrollbarReserves(initialLayout.nodes, initialLayout::childrenOf)
                val resolvedLayout = if (scrollbarReserves.isEmpty()) {
                    initialLayout
                } else {
                    computeLayouts(resolved, width, height, scrollState, scrollbarReserves)
                }
                lastScrollbarReserves = scrollbarReserves
                resolvedLayout
            }
            applyScrollRanges(finalLayout.nodes, scrollState, finalLayout::childrenOf)
            val scrollbars = placeScrollbarNodes(finalLayout.nodes, scrollbarCache)
            val traversalOrder = finalLayout.nodes.keys.toList()
            val childrenByNode = snapshotVisibleChildren(finalLayout.nodes) { node ->
                finalLayout.children[node] ?: node.children
            }
            return UiLayoutResult(
                root = resolved,
                nodes = finalLayout.nodes,
                traversalOrder = traversalOrder,
                scrollbars = scrollbars,
                childrenByNode = childrenByNode,
            )
        } finally {
            activeProfile = previousProfile
            measureDepth = 0
        }
    }

    private fun computeLayouts(
        resolved: UiNode,
        width: Float,
        height: Float,
        scrollState: UiScrollState,
        scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    ): LayoutComputation {
        val layouts = linkedMapOf<UiNode, UiLayoutNode>()
        val viewport = UiRect(0f, 0f, width, height)
        val previousPass = layoutPass
        val pass = LayoutPass(resolved)
        inlineFlowChildLayouts.clear()
        inlineFlowFlattened.clear()
        try {
            layoutPass = pass
            val profile = activeProfile
            if (profile != null) {
                profile.measurePasses++
                profile.placementPasses++
            }
            val rootRect = rootRect(resolved, width, height, scrollbarReserves)
            val placementStartedAt = if (profile != null) System.nanoTime() else 0L
            val measureBeforePlacement = profile?.measureNanos ?: 0L
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
            if (profile != null) {
                val nestedMeasureNanos = profile.measureNanos - measureBeforePlacement
                profile.placementNanos += (System.nanoTime() - placementStartedAt - nestedMeasureNanos).coerceAtLeast(0L)
            }
        } finally {
            layoutPass = previousPass
        }
        return LayoutComputation(layouts, pass.layoutChildren)
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

private data class LayoutComputation(
    val nodes: MutableMap<UiNode, UiLayoutNode>,
    val children: Map<UiNode, List<UiNode>>,
) {
    fun childrenOf(node: UiNode): List<UiNode> = children[node].orEmpty()
}
