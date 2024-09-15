package ru.hollowhorizon.hollowengine.scripting.nodes

import ru.hollowhorizon.hollowengine.scripting.DelegateProperty


open class SequenceNode : Node {
    val nodes = ArrayList<Node>()
    val properties = HashMap<String, DelegateProperty<*>>()
    private var index = 0

    override fun execute(): Boolean {
        if (nodes.isEmpty() || index >= nodes.size) return true
        if (nodes[index].execute()) {
            index++
            return execute()
        }
        return false
    }


    override fun reset() {
        index = 0
    }
}