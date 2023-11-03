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

class NodeContextBuilder(override val stateMachine: StoryStateMachine) : IContextBuilder {
    val tasks = ArrayList<Node>()

    override operator fun <T: Node> T.unaryPlus(): T {
        this.manager = stateMachine
        tasks.add(this)
        return this
    }
}