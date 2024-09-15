package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hc.HollowCore
import compiler.story.PropertyDelegate

open class SequenceNode : Node {
    val nodes = ArrayList<Node>()
    val properties = HashMap<String, compiler.story.PropertyDelegate<*>>()
    private var index = 0

    override fun execute(): Boolean {
        if (nodes.isEmpty() || index >= nodes.size) return true
        if (nodes[index].execute()) {
            index++
            return execute()
        }
        return false
    }

    override fun serialize() = super.serialize().apply {
        putInt("index", index)
        put("nodes", ListTag().apply { addAll(nodes.map { it.serialize() }) })
    }

    override fun deserialize(tag: Tag) {
        index = (tag as CompoundTag).getInt("index")
        tag.getList("nodes", 10).forEachIndexed { i, node ->
            nodes[i].deserialize(node)
        }
    }

    override fun reset() {
        index = 0
    }
}