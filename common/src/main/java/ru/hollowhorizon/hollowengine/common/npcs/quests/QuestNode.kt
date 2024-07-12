package ru.hollowhorizon.hollowengine.common.npcs.quests

import kotlinx.serialization.Serializable
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.utils.nbt.ForItemStack
import ru.hollowhorizon.hollowengine.common.npcs.quests.rewards.QuestReward
import ru.hollowhorizon.hollowengine.common.npcs.quests.tasks.QuestTask

@Serializable
class QuestNode {
    var icon: @Serializable(ForItemStack::class) ItemStack = ItemStack.EMPTY
    val tasks = ArrayList<QuestTask>()
    val rewards = ArrayList<QuestReward>()
    var pos = arrayOf(0f, 0f)

    var title = "Квест"
    var subtitle = "Описание отсутствует."
    var description = subtitle
    var completeDescription = ""
    var completeAnimation = ""

    val color = floatArrayOf(0.9f, 0.45f, 0f, 1f)
}