package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.common.utils.mcTranslate
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.HollowEngine

object ModTabs : HollowRegistry(HollowEngine.MODID) {
    val HOLLOW_ENGINE: CreativeModeTab by creativeTab("creative_tab") {
        icon { ModItems.STORYTELLER_DIM_TELEPORTER.defaultInstance }
        title("itemGroup.hollowengine".mcTranslate)
    }
}