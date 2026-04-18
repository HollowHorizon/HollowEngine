package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ArmorItem
import net.minecraft.world.item.ItemStack
import java.util.*

fun ItemStack.getArmorTexture(entity: Entity, slot: EquipmentSlot): ResourceLocation {
    val item = item as ArmorItem
    var texture = item.material.registeredName

    var domain = "minecraft"
    val idx = texture.indexOf(':')
    if (idx != -1) {
        domain = texture.substring(0, idx)
        texture = texture.substring(idx + 1)
    }
    val path = String.format(
        Locale.ROOT, "%s:textures/models/armor/%s_layer_%d%s.png", domain, texture,
        (if (slot == EquipmentSlot.LEGS) 2 else 1), ""
    )

    return path.rl
}