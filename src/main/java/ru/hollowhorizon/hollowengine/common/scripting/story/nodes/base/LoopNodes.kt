package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

open class WhileNode(protected val condition: Node.() -> Boolean, val tasks: List<Node>): Node() {
    protected var index = 0

    init {
        tasks.forEach { it.parent = this }
    }

    override fun tick(): Boolean {
        if(!tasks[index].tick()) index++
        if(index >= tasks.size) index = 0

        return condition()
    }

    override fun serializeNBT() = CompoundTag().apply {
        serializeNodes("nodes", tasks)
        putInt("index", index)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        nbt.deserializeNodes("nodes", tasks)
        index = nbt.getInt("index")
    }
}

class DoWhileNode(condition: Node.() -> Boolean, tasks: List<Node>): WhileNode(condition, tasks) {
    override fun tick(): Boolean {
        if(!condition()) index++
        if(!tasks[index].tick()) index++
        if(index >= tasks.size) index = 0
        return true
    }
}

fun IContextBuilder.While(condition: Node.() -> Boolean, tasks: NodeContextBuilder.() -> Unit) = +WhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks)
fun IContextBuilder.DoWhile(condition: Node.() -> Boolean, tasks: NodeContextBuilder.() -> Unit) = +DoWhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks)

fun Node.anyActiveTask(): Boolean {
    val parent = parent

    if(parent is CombinedNode) {
       return parent.nodes.any { it.key != this && !it.value.data }
    }
    return parent?.anyActiveTask() ?: false
}