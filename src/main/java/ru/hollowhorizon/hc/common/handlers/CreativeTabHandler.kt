package ru.hollowhorizon.hc.common.handlers

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

object CreativeTabHandler {
    val ITEMS = hashMapOf<CreativeModeTab, MutableList<ItemStack>>()
}

fun <T : Item> T.tab(first: CreativeModeTab): T {
    CreativeTabHandler.ITEMS.computeIfAbsent(first) { mutableListOf() }.add(ItemStack(this))
    return this
}
