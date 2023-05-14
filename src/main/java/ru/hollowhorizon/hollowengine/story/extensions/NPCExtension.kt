package ru.hollowhorizon.hollowengine.story.extensions

import net.minecraft.entity.item.ItemEntity
import net.minecraft.inventory.EquipmentSlotType
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.CompoundNBT
import net.minecraft.nbt.JsonToNBT
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hollowengine.common.npcs.IHollowNPC

fun IHollowNPC.dropItem(stack: ItemStack) {
    val p = this.npcEntity.position()
    val entityStack = ItemEntity(this.npcEntity.level, p.x, p.y+this.npcEntity.eyeHeight, p.z, stack)
    entityStack.setDeltaMovement(this.npcEntity.lookAngle.x / 3, this.npcEntity.lookAngle.y / 3, this.npcEntity.lookAngle.z / 3)
    this.npcEntity.level.addFreshEntity(entityStack)
}

fun IHollowNPC.dropItem(item: String) {
    val parsed = item.split("@")

    val forgeItem = ForgeRegistries.ITEMS.getValue(parsed[0].toRL()) ?: Items.BEDROCK

    val count =
        if (parsed.size > 1) parsed[1].toInt()
        else 1

    val nbt =
        if (parsed.size > 2) JsonToNBT.parseTag(parsed[2])
        else CompoundNBT()

    dropItem(ItemStack(forgeItem, count, nbt))
}

fun IHollowNPC.equip(slot: EquipmentSlotType, item: String) {
    if (item.isEmpty()) this.npcEntity.setItemSlot(slot, ItemStack.EMPTY)

    val parsed = item.split("@")

    val forgeItem = ForgeRegistries.ITEMS.getValue(parsed[0].toRL()) ?: Items.BEDROCK

    val count =
        if (parsed.size > 1) parsed[1].toInt()
        else 1

    val nbt =
        if (parsed.size > 2) JsonToNBT.parseTag(parsed[2])
        else CompoundNBT()

    this.npcEntity.setItemSlot(slot, ItemStack(forgeItem, count, nbt))
}
