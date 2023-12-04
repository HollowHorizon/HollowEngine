package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.SpawnLocation
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.saveable

class ConditionNode(
    private var condition: () -> Boolean,
    private val ifTasks: List<Node>,
    private val elseTasks: MutableList<Node>
) : Node(), HasInnerNodes {
    val initialCondition by lazy { condition() }
    var index = 0
    private val isEnd get() = index >= if (initialCondition) ifTasks.size else elseTasks.size
    override val currentNode get() = if (initialCondition) ifTasks[index] else elseTasks[index]

    fun setElseTasks(tasks: List<Node>) {
        elseTasks.clear()
        elseTasks.addAll(tasks)
    }

    override fun tick(): Boolean {
        if(!currentNode.tick()) index++

        return isEnd
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

fun IContextBuilder.If(condition: () -> Boolean, ifTasks: NodeContextBuilder.() -> Unit, elseTasks: NodeContextBuilder.() -> Unit) = +ConditionNode(condition, NodeContextBuilder(this.stateMachine).apply(ifTasks).tasks, NodeContextBuilder(this.stateMachine).apply(elseTasks).tasks)

fun IContextBuilder.If(condition: () -> Boolean, ifTasks: NodeContextBuilder.() -> Unit) = +ConditionNode(condition, NodeContextBuilder(this.stateMachine).apply(ifTasks).tasks, ArrayList())

infix fun ConditionNode.Else(tasks: NodeContextBuilder.() -> Unit) = setElseTasks(NodeContextBuilder(manager).apply(tasks).tasks)