package ru.hollowhorizon.hollowengine.common.npcs.quests.tasks

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.utils.nbt.ForItemStack
import ru.hollowhorizon.hc.client.utils.nbt.NBTFormat
import ru.hollowhorizon.hc.client.utils.nbt.NBT_TAGS
import ru.hollowhorizon.hc.client.utils.nbt.serialize
import ru.hollowhorizon.hollowengine.common.registry.QTask

@Serializable
@QTask
@Polymorphic(QuestTask::class)
class CollectItemsTask : AbstractQuestTask() {
    val items: List<@Serializable(ForItemStack::class) ItemStack> = arrayListOf()
    var consumeItems = false

    override fun check(player: Player): Boolean {
        TODO("Not yet implemented")
    }

    override fun complete(player: Player): Boolean {
        TODO("Not yet implemented")
    }
}

fun main() {
    val t: QuestTask = CollectItemsTask()

    t.description = "hello"

    NBT_TAGS[QuestTask::class] = arrayListOf(CollectItemsTask::class, AbstractQuestTask::class)

    println(NBTFormat.serialize(t))
}