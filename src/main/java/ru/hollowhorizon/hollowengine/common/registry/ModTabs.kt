package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.common.utils.mcTranslate
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.HollowEngine

object ModTabs : HollowRegistry(HollowEngine.MODID) {
    //? if >=1.20.1 {
    val HOLLOW_ENGINE: CreativeModeTab by creativeTab("creative_tab") {
        icon { ModItems.STORYTELLER_DIM_TELEPORTER.defaultInstance }
        title("itemGroup.hollowengine".mcTranslate)
    }
    //?} else {
    /*val HOLLOW_ENGINE = RegistryObject<CreativeModeTab> {
        object : CreativeModeTab(9, "hollowengine") {
            override fun makeIcon(): ItemStack {
                return ItemStack(ModItems.STORYTELLER_DIM_TELEPORTER.get())
            }
        }
    }
    *///?}
}