package ru.hollowhorizon.hollowengine.common.scripting.dialogues

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser.parseTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.toRL
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hollowengine.common.npcs.ICharacter

class HDCharacter(val type: LivingEntity) : HDObject(), ICharacter {
    var mcName = type.displayName
    override val characterName
        get() = mcName.string
    override val entityType = type

    constructor(
        location: String,
        characterName: String = "%default%",
        nbt: String = "",
    ) : this(
        EntityType.loadEntityRecursive(
        generateEntityNBT(location, nbt), ServerLifecycleHooks.getCurrentServer().overworld()
    ) { e ->e
    } as LivingEntity) {

        if (characterName != "%default%") {
            this.mcName = characterName.toSTC()
        }
    }

    init {
        this.type.customName = mcName
        this.type.isCustomNameVisible = true
    }

    fun setItem(slot: EquipmentSlot, item: String) {
        if (item.isEmpty()) type.setItemSlot(slot, ItemStack.EMPTY)

        val parsed = item.split("@")

        val forgeItem = ForgeRegistries.ITEMS.getValue(parsed[0].toRL()) ?: Items.BEDROCK

        val count =
            if (parsed.size > 1) parsed[1].toInt()
            else 1

        val nbt =
            if (parsed.size > 2) parseTag(parsed[2])
            else CompoundTag()

        type.setItemSlot(slot, ItemStack(forgeItem, count, nbt))
    }

}

fun generateEntityNBT(entity: String): CompoundTag {
    val entityData = entity.split("@")
    return generateEntityNBT(entityData[0], if (entityData.size > 1) entityData[1] else "")
}

fun generateEntityNBT(location: String, nbt: String): CompoundTag {
    val c = if (nbt.isEmpty()) {
        CompoundTag()
    } else {
        parseTag(nbt)
    }

    c.putString("id", location)
    return c
}