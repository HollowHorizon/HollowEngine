package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

suspend fun NpcEntity.collectItems(vararg items: ItemStack) {
    val itemList = items.toMutableList()
    while (itemList.isNotEmpty()) {
        pickupItems(itemList)
        delay(50)
    }
}

private fun NpcEntity.pickupItems(list: MutableList<ItemStack>) {
    level().getEntitiesOfClass(
        ItemEntity::class.java,
        this.boundingBox.inflate(pickupDistance.x.toDouble(), pickupDistance.y.toDouble(), pickupDistance.z.toDouble())
    ).forEach { item ->
        if (item.isRemoved || item.item.isEmpty || item.hasPickUpDelay()) return@forEach

        val entityItem = item.item

        list.find { it.item == entityItem.item }?.let { requestItem ->
            val remaining = requestItem.count
            requestItem.shrink(entityItem.count)
            entityItem.shrink(remaining)
        }

        list.removeIf { it.isEmpty }
    }
}