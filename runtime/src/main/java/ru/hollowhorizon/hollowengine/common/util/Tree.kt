package ru.hollowhorizon.hollowengine.common.util

import kotlinx.serialization.Serializable

@Serializable
open class Node(
    val name: String,
    val path: String,
    val children: MutableMap<String, Node> = mutableMapOf(),
)

fun Collection<String>.toNode(): Node {
    val root = Node("", "")

    for (path in this) {
        var current = root
        val parts = path.split("/")

        var nodePath = ""
        for (part in parts) {
            nodePath += ".$part"
            current = current.children.computeIfAbsent(part) { Node("component.hollowengine$nodePath", path) }
        }
    }

    return root
}