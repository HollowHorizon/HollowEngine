package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hc.common.registry.RegistryObject

object ModTabs : HollowRegistry() {
    val HOLLOW_ENGINE: RegistryObject<CreativeModeTab> by register("hollowcore:creative_tab".rl) {
        CreativeModeTab
            .builder(CreativeModeTab.Row.TOP, 9)
            .title("itemGroup.hollowengine".mcTranslate)
            .icon { ItemStack(ModItems.STORYTELLER_DIM_TELEPORTER.get()) }.build()
    }
}