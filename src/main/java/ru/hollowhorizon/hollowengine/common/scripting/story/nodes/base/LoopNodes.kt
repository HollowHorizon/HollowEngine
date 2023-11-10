package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

open class WhileNode(protected val condition: () -> Boolean, val tasks: List<Node>) : Node() {
    protected var index = 0
    val initialData = CompoundTag().apply {
        serializeNodes("nodes", tasks)
    }

    init {
        tasks.forEach { it.parent = this }
    }

    override fun tick(): Boolean {
        if (!tasks[index].tick()) index++
        if (index >= tasks.size) {
            index = 0
            initialData.deserializeNodes("nodes", tasks)
        }

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

class DoWhileNode(condition: () -> Boolean, tasks: List<Node>) : WhileNode(condition, tasks) {

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

fun IContextBuilder.While(condition: () -> Boolean, tasks: NodeContextBuilder.() -> Unit) =
    +WhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks)

fun IContextBuilder.DoWhile(condition: () -> Boolean, tasks: NodeContextBuilder.() -> Unit) =
    +DoWhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks)