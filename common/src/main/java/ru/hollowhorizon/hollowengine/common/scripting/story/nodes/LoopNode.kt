package ru.hollowhorizon.hollowengine.common.scripting.story.nodes

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

class LoopNode(val condition: Node, val body: Node, var doWhile: Boolean = false) : Node {
    var lastCheck = false

    override fun execute(): Boolean {
        return if (doWhile) executeDoWhile()
        else executeWhile()
    }

    /**
     * Если у нас doWhile, то мы сначала вызываем тело цикла и когда оно возвращает false проверяем условие цикла. Если условие истинно - перезапускаем цикл. Если ложно - то возвращаем false, чтобы завершить ноду.
     */
    private fun executeDoWhile(): Boolean {
        if (!body.execute()) return false
        else {
            if (condition.execute()) {
                body.reset()
                return false
            }
            return true
        }
    }

    /**
     * Если же у нас простой While, то мы сначала вызываем условие и сохраняем его. Далее вызываем тело цикла каждый раз, пока оно истинно, иначе же перезапускаем ноду и сбрасываем условие, после чего происходит повторная проверка.
     */
    private fun executeWhile(): Boolean {
        if (!lastCheck) lastCheck = condition.execute()
        if (lastCheck) {
            if (body.execute()) {
                lastCheck = false
                body.reset()
                return true
            }
        }
        return false
    }

    override fun serialize() = super.serialize().apply {
        putBoolean("lastCheck", lastCheck)
        put("condition", condition.serialize())
        put("sequence", body.serialize())
    }

    override fun deserialize(tag: Tag) {
        (tag as CompoundTag).apply {
            lastCheck = getBoolean("lastCheck")
            condition.deserialize(getCompound("condition"))
            body.deserialize(tag.getCompound("sequence"))
        }
    }
}