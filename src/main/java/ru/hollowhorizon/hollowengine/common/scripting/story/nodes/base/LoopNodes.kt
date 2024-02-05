package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import java.util.*

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
        if(index >= tasks.size) return condition()

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

private fun StoryStateMachine.innerBreak(
    tag: String = "",
    node: Node = stateMachine.nodes[stateMachine.currentIndex],
    stack: Stack<WhileNode> = Stack()
) {
    if (node is WhileNode) stack.push(node)
    if (node is HasInnerNodes) {
        if (node.currentNode is HasInnerNodes) innerBreak(tag, node.currentNode)
        else while (!stack.empty()) {
            val whileNode = stack.pop()
            if (whileNode.tag == tag) {
                whileNode.shouldBreak = true
                break
            }
        }
    } else throw IllegalArgumentException("${node.javaClass} is not a HasInnerNodes. May be you called Break() not in loop?")
}

private fun StoryStateMachine.innerContinue(
    tag: String = "",
    node: Node = stateMachine.nodes[stateMachine.currentIndex],
    stack: Stack<WhileNode> = Stack()
) {
    if (node is WhileNode) stack.push(node)
    if (node is HasInnerNodes) {
        if (node.currentNode is HasInnerNodes) innerBreak(tag, node.currentNode)
        else while (!stack.empty()) {
            val whileNode = stack.pop()
            if (whileNode.tag == tag) {
                whileNode.shouldContinue = true
                break
            }
        }
    } else throw IllegalArgumentException("${node.javaClass} is not a HasInnerNodes. May be you called Continue() not in loop?")
}

fun IContextBuilder.Break(tag: () -> String) = next { manager.innerBreak(tag()) }
fun IContextBuilder.Continue(tag: () -> String) = next { manager.innerContinue(tag()) }