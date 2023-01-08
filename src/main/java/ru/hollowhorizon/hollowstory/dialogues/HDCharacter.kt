package ru.hollowhorizon.hollowstory.dialogues

import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.LivingEntity
import net.minecraft.nbt.CompoundNBT
import net.minecraft.nbt.JsonToNBT
import net.minecraft.util.text.ITextComponent
import ru.hollowhorizon.hc.client.utils.toSTC

class HDCharacter(val entity: LivingEntity) : HDObject() {
    var name: ITextComponent = entity.displayName

    constructor(location: String, characterName: String = "%default%", nbt: String = "") : this(EntityType.loadEntityRecursive(
        generateEntityNBT(location, nbt), Minecraft.getInstance().player!!.level
    ) { entity: Entity ->
        entity
    } as LivingEntity) {
        if (characterName != "%default%") {
            this.name = characterName.toSTC()
        }
    }

}

fun generateEntityNBT(location: String, nbt: String): CompoundNBT {
    val c = if(nbt.isEmpty()) {
        CompoundNBT()
    } else {
        JsonToNBT.parseTag(nbt)
    }

    c.putString("id", location)
    return c
}