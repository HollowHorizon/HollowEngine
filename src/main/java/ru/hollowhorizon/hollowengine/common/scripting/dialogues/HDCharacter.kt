package ru.hollowhorizon.hollowengine.common.scripting.dialogues

import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.inventory.EquipmentSlotType
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.CompoundNBT
import net.minecraft.nbt.JsonToNBT
import net.minecraft.util.text.ITextComponent
import net.minecraftforge.fml.server.ServerLifecycleHooks
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowengine.common.npcs.ICharacter

class HDCharacter(val type: LivingEntity) : HDObject(), ICharacter {
    var mcName: ITextComponent = type.displayName
    override val characterName
        get() = mcName.string
    override val entityType: CompoundNBT
        get() = type.serializeNBT()

    constructor(
        location: String,
        characterName: String = "%default%",
        nbt: String = "",
    ) : this(EntityType.loadEntityRecursive(
        generateEntityNBT(location, nbt), ServerLifecycleHooks.getCurrentServer().overworld()
    ) { entity: Entity ->
        entity
    } as LivingEntity) {

        if (characterName != "%default%") {
            this.mcName = characterName.toSTC()
        }
    }

    init {
        this.type.customName = mcName
        this.type.isCustomNameVisible = true
    }

    fun setItem(slot: EquipmentSlotType, item: String) {
        if (item.isEmpty()) type.setItemSlot(slot, ItemStack.EMPTY)

        val parsed = item.split("@")

        val forgeItem = ForgeRegistries.ITEMS.getValue(parsed[0].toRL()) ?: Items.BEDROCK

        val count =
            if (parsed.size > 1) parsed[1].toInt()
            else 1

        val nbt =
            if (parsed.size > 2) JsonToNBT.parseTag(parsed[2])
            else CompoundNBT()

        type.setItemSlot(slot, ItemStack(forgeItem, count, nbt))
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