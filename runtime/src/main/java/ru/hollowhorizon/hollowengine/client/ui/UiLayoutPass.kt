package ru.hollowhorizon.hollowengine.client.ui


import java.util.*

internal sealed interface PlacementTask

internal data class NodePlacementTask(
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
) : PlacementTask

internal data class PopupPlacementTask(
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
) : PlacementTask

internal class LayoutPass(root: UiNode) {
    val layoutChildren = IdentityHashMap<UiNode, List<UiNode>>()
    val popupChildren = IdentityHashMap<UiNode, List<PopupNode>>()

    init {
        val stack = ArrayDeque<UiNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
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
