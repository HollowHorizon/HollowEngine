package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class ConditionNode(
    private var condition: () -> Boolean,
    private val ifTasks: List<Node>,
    private val elseTasks: List<Node>
) : Node() {
    var initialCondition: Boolean? = null
    var index = 0
    val isEnd get() = index >= if (initialCondition == true) ifTasks.size else elseTasks.size

    init {
        ifTasks.forEach { it.parent = this }
        elseTasks.forEach { it.parent = this }
    }

    override fun tick(): Boolean {
        if (initialCondition == null) initialCondition = condition()

        if (initialCondition == true) {
            if (ifTasks[index].tick()) index++
        } else {
            if (elseTasks[index].tick()) index++
        }

        return isEnd
    }

    override fun serializeNBT() = CompoundTag().apply {
        if (initialCondition != null) putBoolean("condition", initialCondition!!)
        serializeNodes("if_tasks", ifTasks)
        serializeNodes("else_tasks", elseTasks)
        putInt("index", index)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        if (nbt.contains("condition")) initialCondition = nbt.getBoolean("condition")
        nbt.deserializeNodes("if_tasks", ifTasks)
        nbt.deserializeNodes("else_tasks", elseTasks)
        index = nbt.getInt("index")
    }
}

fun IContextBuilder.If(condition: () -> Boolean, ifTasks: NodeContextBuilder.() -> Unit, elseTasks: NodeContextBuilder.() -> Unit) = +ConditionNode(condition, NodeContextBuilder(this.stateMachine).apply(ifTasks).tasks, NodeContextBuilder(this.stateMachine).apply(elseTasks).tasks)