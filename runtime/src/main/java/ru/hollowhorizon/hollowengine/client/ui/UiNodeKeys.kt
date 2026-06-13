package ru.hollowhorizon.hollowengine.client.ui

import java.util.*

object UiNodeKeys {
    private val keys = WeakHashMap<UiNode, String>()

    fun assign(root: UiNode) {
        assign(root, "root")
    }

    fun key(node: UiNode): String = keys[node] ?: node.id ?: node.type

    private fun assign(node: UiNode, path: String) {
        node.layoutState.synchronizeChildren()
        val ownKey = node.id ?: "$path/${node.type}${node.tags.sorted().joinToString(prefix = "[", postfix = "]")}"
        keys[node] = ownKey
        node.children.forEachIndexed { index, child ->
            assign(child, "$ownKey/$index")
        }
    }
}
