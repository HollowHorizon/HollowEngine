package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node

class WaitNode(var startTime: () -> Int) : Node() {
    var isStarted = false
    var time = 0

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            time = startTime()
        }
        return time-- > 0
    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("time", time)
        putBoolean("isStarted", isStarted)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        time = nbt.getInt("time")
        isStarted = nbt.getBoolean("isStarted")
    }
}

fun IContextBuilder.wait(time: () -> Int) = +WaitNode(time)

fun IContextBuilder.await(condition: () -> Boolean) = +object : Node() {
    override fun tick(): Boolean {
        return !condition()
    }

    override fun serializeNBT() = CompoundTag()

    override fun deserializeNBT(p0: CompoundTag) {}

}
