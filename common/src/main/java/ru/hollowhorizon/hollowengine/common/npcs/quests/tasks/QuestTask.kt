package ru.hollowhorizon.hollowengine.common.npcs.quests.tasks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.api.utils.Polymorphic

@Serializable
@Polymorphic(QuestTask::class)
abstract class AbstractQuestTask : QuestTask {
    override var name: String = javaClass.simpleName
    override var description = ""
    override var completeText = ""
    override var completeAnimation = ""
}

interface QuestTask {
    var name: String
    var description: String
    var completeText: String
    var completeAnimation: String

    val icon: ItemStack get() = ItemStack.EMPTY

    fun check(player: Player): Boolean

    fun complete(player: Player): Boolean

    fun drawEditor() {

    }
}