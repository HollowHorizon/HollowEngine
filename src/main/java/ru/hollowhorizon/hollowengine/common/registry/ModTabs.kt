package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hc.common.registry.RegistryObject

object ModTabs : HollowRegistry() {
    //? if >=1.20.1 {
    val HOLLOW_ENGINE: RegistryObject<CreativeModeTab> by register("hollowcore:creative_tab".rl) {
        CreativeModeTab
            .builder(CreativeModeTab.Row.TOP, 9)
            .title("itemGroup.hollowengine".mcTranslate)
            .icon { ItemStack(ModItems.STORYTELLER_DIM_TELEPORTER.get()) }.build()
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