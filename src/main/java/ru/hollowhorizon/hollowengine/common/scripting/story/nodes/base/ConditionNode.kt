package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import java.util.*
import kotlin.collections.ArrayList

class ConditionNode(
    private var condition: () -> Boolean,
    private val ifTasks: List<Node>,
    private val elseTasks: MutableList<Node>
) : Node(), HasInnerNodes {
    var index = 0
    private val isEnd get() = index >= if (condition()) ifTasks.size else elseTasks.size
    override val currentNode get() = if (condition()) ifTasks[index] else elseTasks[index]

    fun setElseTasks(tasks: List<Node>) {
        elseTasks.clear()
        elseTasks.addAll(tasks)
    }

    override fun tick(): Boolean {
        if(isEnd) return false

        if (!currentNode.tick()) index++

        return true
    }

    override fun serializeNBT() = CompoundTag().apply {
        serializeNodes("if_tasks", ifTasks)
        serializeNodes("else_tasks", elseTasks)
        putInt("index", index)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        nbt.deserializeNodes("if_tasks", ifTasks)
        nbt.deserializeNodes("else_tasks", elseTasks)
        index = nbt.getInt("index")
    }
}

fun IContextBuilder.If(
    condition: () -> Boolean,
    ifTasks: NodeContextBuilder.() -> Unit,
    elseTasks: NodeContextBuilder.() -> Unit
) = +ConditionNode(
    condition,
    NodeContextBuilder(this.stateMachine).apply(ifTasks).tasks,
    NodeContextBuilder(this.stateMachine).apply(elseTasks).tasks
)

fun IContextBuilder.If(condition: () -> Boolean, ifTasks: NodeContextBuilder.() -> Unit) =
    +ConditionNode(condition, NodeContextBuilder(this.stateMachine).apply(ifTasks).tasks, ArrayList())

infix fun ConditionNode.Else(tasks: NodeContextBuilder.() -> Unit) =
    setElseTasks(NodeContextBuilder(manager).apply(tasks).tasks)

