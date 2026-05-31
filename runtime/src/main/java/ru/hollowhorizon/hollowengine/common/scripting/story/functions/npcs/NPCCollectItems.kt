package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import kotlinx.coroutines.delay
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariHostReferences
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual

@ScriptBinding
suspend fun NpcEntity.requestItems(vararg items: ItemStack) {
    val server = (level() as ServerLevel).server
    val npcId = uuid
    val requested = items
        .map(ItemStack::copy)
        .filterNot(ItemStack::isEmpty)
        .toMutableList()

    while (requested.isNotEmpty()) {
        val npc = KatariHostReferences.awaitEntity(server, npcId, NpcEntity::class.java)
        npc.pickupRequestedItems(requested)
        delay(50)
    }
}

private fun NpcEntity.pickupRequestedItems(list: MutableList<ItemStack>) {
    level().getEntitiesOfClass(
        ItemEntity::class.java,
        this.boundingBox.inflate(pickupDistance.x.toDouble(), pickupDistance.y.toDouble(), pickupDistance.z.toDouble())
    ).forEach { item ->
        if (item.isRemoved || item.item.isEmpty || item.hasPickUpDelay()) return@forEach

        val entityItem = item.item

        list.find { requestItem -> requestItem.areStacksEqual(entityItem) }?.let { requestItem ->
            val requestedCount = requestItem.count
            requestItem.shrink(entityItem.count)
            entityItem.shrink(requestedCount)
        }

        list.removeIf(ItemStack::isEmpty)
    }
}
