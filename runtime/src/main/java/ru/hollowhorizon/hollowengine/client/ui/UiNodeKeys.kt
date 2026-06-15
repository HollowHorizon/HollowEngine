package ru.hollowhorizon.hollowengine.client.ui

import java.util.*

object UiNodeKeys {
    private val keys = WeakHashMap<UiNode, String>()

    fun assign(root: UiNode) {
        val stack = ArrayDeque<NodeKeyTask>()
        stack.add(NodeKeyTask(root, "root"))
        while (stack.isNotEmpty()) {
            val task = stack.removeLast()
            val node = task.node
            node.layoutState.synchronizeChildren()
            val ownKey = node.id ?: "${task.path}/${node.type}${node.tags.sorted().joinToString(prefix = "[", postfix = "]")}"
            keys[node] = ownKey
            for (index in node.children.indices.reversed()) {
                stack.add(NodeKeyTask(node.children[index], "$ownKey/$index"))
            }
        }
    }

    fun key(node: UiNode): String = keys[node] ?: node.id ?: node.type
}

private data class NodeKeyTask(
    val node: UiNode,
    val path: String,
)
