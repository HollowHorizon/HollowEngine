package ru.hollowhorizon.hollowengine.common.tabs

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.registry.ModItems


val HOLLOWENGINE_TAB = object : CreativeModeTab("hollowengine") {
    override fun makeIcon(): ItemStack {
        return ItemStack(ModItems.STORYTELLER_DIM_TELEPORTER.get())
    }
}