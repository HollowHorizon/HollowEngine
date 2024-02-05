package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util

import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack

class TeamHelper(val team: Team) {
    operator fun ItemStack.unaryPlus() {
        team.onlineMembers.forEach {
            it.inventory.add(this)
            it.inventory.setChanged()
        }
    }

    fun setHealth(value: Float) {
        team.onlineMembers.forEach {
            it.health = value
        }
    }

    fun setMaxHealth(value: Float) {
        team.onlineMembers.forEach {
            it.attributes.getInstance(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)?.baseValue =
                value.toDouble()
        }
        setHealth(value)
    }

    fun addHealth(value: Float) {
        team.onlineMembers.forEach {
            it.health += value
        }
    }

    fun equipHelmet(item: ItemStack) = team.onlineMembers.forEach {
        it.drop(it.getItemBySlot(EquipmentSlot.HEAD), false)
        it.setItemSlot(EquipmentSlot.HEAD, item)
    }

    fun equipChestplate(item: ItemStack) = team.onlineMembers.forEach {
        it.drop(it.getItemBySlot(EquipmentSlot.CHEST), false)
        it.setItemSlot(EquipmentSlot.CHEST, item)
    }

    fun equipLeggings(item: ItemStack) = team.onlineMembers.forEach {
        it.drop(it.getItemBySlot(EquipmentSlot.LEGS), false)
        it.setItemSlot(EquipmentSlot.LEGS, item)
    }

    fun equipBoots(item: ItemStack) = team.onlineMembers.forEach {
        it.drop(it.getItemBySlot(EquipmentSlot.FEET), false)
        it.setItemSlot(EquipmentSlot.FEET, item)
    }
}