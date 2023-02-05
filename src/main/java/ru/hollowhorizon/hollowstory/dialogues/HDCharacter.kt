package ru.hollowhorizon.hollowstory.dialogues

import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.inventory.EquipmentSlotType
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.CompoundNBT
import net.minecraft.nbt.JsonToNBT
import net.minecraft.util.text.ITextComponent
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC

class HDCharacter(val entity: LivingEntity) : HDObject() {
    var name: ITextComponent = entity.displayName

    constructor(
        location: String,
        characterName: String = "%default%",
        nbt: String = "",
    ) : this(EntityType.loadEntityRecursive(
        generateEntityNBT(location, nbt), Minecraft.getInstance().player!!.level
    ) { entity: Entity ->
        entity
    } as LivingEntity) {

        if (characterName != "%default%") {
            this.name = characterName.toSTC()
        }
    }

    init {
        this.entity.customName = name
        this.entity.isCustomNameVisible = true
    }

    fun setItem(slot: EquipmentSlotType, item: String) {
        if (item.isEmpty()) entity.setItemSlot(slot, ItemStack.EMPTY)

        val parsed = item.split("@")

        val forgeItem = ForgeRegistries.ITEMS.getValue(parsed[0].toRL()) ?: Items.BEDROCK

        val count =
            if (parsed.size > 1) parsed[1].toInt()
            else 1

        val nbt =
            if (parsed.size > 2) JsonToNBT.parseTag(parsed[2])
            else CompoundNBT()

        entity.setItemSlot(slot, ItemStack(forgeItem, count, nbt))
    }

}

fun generateEntityNBT(entity: String): CompoundNBT {
    val entityData = entity.split("@")
    return generateEntityNBT(entityData[0], if (entityData.size > 1) entityData[1] else "")
}

fun generateEntityNBT(location: String, nbt: String): CompoundNBT {
    val c = if (nbt.isEmpty()) {
        CompoundNBT()
    } else {
        JsonToNBT.parseTag(nbt)
    }

    c.putString("id", location)
    return c
}