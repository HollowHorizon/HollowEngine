package ru.hollowhorizon.hollowengine.common.scripting.story.extensions

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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
        if (parsed.size > 2) TagParser.parseTag(parsed[2])
        else CompoundTag()

    dropItem(ItemStack(forgeItem, count, nbt))
}

fun IHollowNPC.equip(slot: EquipmentSlot, item: String) {
    if (item.isEmpty()) this.npcEntity.setItemSlot(slot, ItemStack.EMPTY)

    val parsed = item.split("@")

    val forgeItem = ForgeRegistries.ITEMS.getValue(parsed[0].toRL()) ?: Items.BEDROCK

    val count =
        if (parsed.size > 1) parsed[1].toInt()
        else 1

    val nbt =
        if (parsed.size > 2) TagParser.parseTag(parsed[2])
        else CompoundTag()

    this.npcEntity.setItemSlot(slot, ItemStack(forgeItem, count, nbt))
}
