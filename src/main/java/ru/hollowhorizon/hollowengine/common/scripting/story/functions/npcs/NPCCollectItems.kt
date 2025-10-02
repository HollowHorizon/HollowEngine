package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStack
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

@Serializable
class Item(val itemStack: @Serializable(ForItemStack::class) ItemStack)

private fun NpcEntity.pickupItems(list: MutableList<Item>) {
    level().getEntitiesOfClass(
        ItemEntity::class.java,
        this.boundingBox.inflate(pickupDistance.x.toDouble(), pickupDistance.y.toDouble(), pickupDistance.z.toDouble())
    ).forEach { item ->
        if (item.isRemoved || item.item.isEmpty || item.hasPickUpDelay()) return@forEach

        val entityItem = item.item

        list.find { it.itemStack.item == entityItem.item }?.let { requestItem ->
            val remaining = requestItem.itemStack.count
            requestItem.itemStack.shrink(entityItem.count)
            entityItem.shrink(remaining)
        }

        list.removeIf { it.itemStack.isEmpty }
    }
}