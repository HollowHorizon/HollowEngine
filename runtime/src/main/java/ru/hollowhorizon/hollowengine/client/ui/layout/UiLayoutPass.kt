package ru.hollowhorizon.hollowengine.client.ui.layout


import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.style.UiComputedStyle
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.IdentityHashMap

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
