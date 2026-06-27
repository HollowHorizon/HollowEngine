package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.utils.areStacksEqual

private fun NpcEntity.pickupRequestedItems(list: MutableList<ItemStack>): Boolean {
    var changed = false
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
            changed = true
        }

        list.removeIf(ItemStack::isEmpty)
    }

    return changed || list.isEmpty()
}
