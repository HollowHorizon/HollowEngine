package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

open class WhileNode(protected val condition: () -> Boolean, val tasks: List<Node>, val tag: String = "") : Node(), HasInnerNodes {
    protected var index = 0
    private val initialData = CompoundTag().apply {
        serializeNodes("nodes", tasks)
    }
    internal var shouldContinue = false
    internal var shouldBreak = false
    override val currentNode get() = tasks[index]

    override fun tick(): Boolean {
        if(shouldBreak) return false
        if (shouldContinue) {
            index = 0
            shouldContinue = false
        }

        if (!currentNode.tick()) index++
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

class DoWhileNode(condition: () -> Boolean, tasks: List<Node>, tag: String = "") : WhileNode(condition, tasks, tag) {

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

fun IContextBuilder.While(condition: () -> Boolean, tag: () -> String, tasks: NodeContextBuilder.() -> Unit) =
    +WhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks, tag())

fun IContextBuilder.DoWhile(condition: () -> Boolean, tag: () -> String, tasks: NodeContextBuilder.() -> Unit) =
    +DoWhileNode(condition, NodeContextBuilder(this.stateMachine).apply(tasks).tasks, tag())