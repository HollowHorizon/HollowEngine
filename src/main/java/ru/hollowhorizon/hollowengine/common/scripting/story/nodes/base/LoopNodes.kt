package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

open class WhileNode(protected val condition: Node.() -> Boolean, val tasks: List<Node>) : Node() {
    protected var index = 0

    constructor(tasks: List<Node>, condition: () -> Boolean) : this({ condition() }, tasks)

    init {
        tasks.forEach { it.parent = this }
    }

    override fun tick(): Boolean {
        if (!tasks[index].tick()) index++
        if (index >= tasks.size) index = 0

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

class DoWhileNode(condition: Node.() -> Boolean, tasks: List<Node>) : WhileNode(condition, tasks) {
    constructor(tasks: List<Node>, condition: () -> Boolean) : this({ condition() }, tasks)

    override fun tick(): Boolean {
        if (!condition()) index++
        if (!tasks[index].tick()) index++
        if (index >= tasks.size) index = 0
        return true
    }
}

operator fun (() -> Boolean).not(): Node.() -> Boolean {
    return { !this@not() }
}

fun IContextBuilder.While(condition: Node.() -> Boolean, tasks: NodeContextBuilder.() -> Unit) =
    +WhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks)

fun IContextBuilder.DoWhile(condition: Node.() -> Boolean, tasks: NodeContextBuilder.() -> Unit) =
    +DoWhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks)

fun IContextBuilder.While(condition: () -> Boolean, tasks: NodeContextBuilder.() -> Unit) =
    +WhileNode(NodeContextBuilder(this.stateMachine).apply(tasks).tasks, condition)

fun IContextBuilder.DoWhile(condition: () -> Boolean, tasks: NodeContextBuilder.() -> Unit) =
    +DoWhileNode(NodeContextBuilder(this.stateMachine).apply(tasks).tasks, condition)

fun Node.anyActiveTask(): Boolean {
    val parent = parent

    if (parent is CombinedNode) {
        return parent.nodes.any { it.key != this && !it.value.data }
    }
    return parent?.anyActiveTask() ?: false
}