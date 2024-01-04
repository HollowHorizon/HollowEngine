package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraftforge.common.util.INBTSerializable
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.*

fun CompoundTag.serializeNodes(name: String, nodes: Collection<Node>) {
    put(name, ListTag().apply {
        addAll(nodes.map(INBTSerializable<CompoundTag>::serializeNBT))
    })
}

fun CompoundTag.deserializeNodes(name: String, nodes: Collection<Node>) {
    val list = getList(name, 10)
    nodes.forEachIndexed { i, node ->
        node.deserializeNBT(list.getCompound(i))
    }
}

open class NodeContextBuilder(override val stateMachine: StoryStateMachine) : IContextBuilder {
    val tasks = ArrayList<Node>()

    override operator fun <T: Node> T.unaryPlus(): T {
        this.manager = stateMachine
        tasks.add(this)
        return this
    }
}

class ChainNode(val nodes: ArrayList<Node>): Node() {
    var index = 0
    val isEnded get() = index >= nodes.size

    override fun tick(): Boolean {
        if(index >= nodes.size) return false
        if(!nodes[index].tick()) index++
        return !isEnded
    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("index", index)
        serializeNodes("nodes", nodes)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        index = nbt.getInt("index")
        nbt.deserializeNodes("nodes", nodes)
    }

}
