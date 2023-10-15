package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class WaitNode(private var time: Int) : Node() {
    override fun tick(): Boolean {
        return time-- > 0
    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("time", time)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        time = nbt.getInt("time")
    }
}

fun IContextBuilder.wait(time: Int) = +WaitNode(time)