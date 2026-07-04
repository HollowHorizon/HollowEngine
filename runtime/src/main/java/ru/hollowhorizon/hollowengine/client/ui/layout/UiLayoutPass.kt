package ru.hollowhorizon.hollowengine.client.ui.layout


import ru.hollowhorizon.hollowengine.client.ui.PopupNode
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.*
import java.util.*

internal data class NodePlacementTask(
    val node: UiNode,
    val resolved: UiNode,
    val rect: UiRect,
    val parentRect: UiRect,
    val parentStyle: UiComputedStyle?,
    val parentClip: UiRect?,
    val parentTransform: UiMatrix4,
    val parentInputTransform: UiMatrix4,
    val insideFramebuffer: Boolean,
    val scrollState: UiScrollState,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val layouts: MutableMap<UiNode, UiLayoutNode>,
)

internal data class PopupPlacementTask(
    val node: UiNode,
    val resolved: UiNode,
    val content: UiRect,
    val parentRect: UiRect,
    val transform: UiMatrix4,
    val inputTransform: UiMatrix4,
    val insideFramebuffer: Boolean,
    val scrollState: UiScrollState,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val layouts: MutableMap<UiNode, UiLayoutNode>,
)

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
                if (child is PopupNode) popups += child else layout += child
            }
            layoutChildren[node] = layout
            popupChildren[node] = popups
            for (index in node.children.indices.reversed()) {
                stack.add(node.children[index])
            }
        }
    }
}
