package ru.hollowhorizon.hollowengine.client.ui.layout


import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiComputedStyle
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.IdentityHashMap

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

internal class LayoutPass(root: UiNode) {
    val layoutChildren = IdentityHashMap<UiNode, List<UiNode>>()

    init {
        val stack = ArrayDeque<UiNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            val children = ArrayList(node.children)
            layoutChildren[node] = children
            for (index in children.indices.reversed()) {
                stack.add(children[index])
            }
        }
    }
}
