package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.item.CreativeModeTab
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate

object ModTabs : HollowRegistry(HollowEngine.MODID) {
    val HOLLOW_ENGINE: CreativeModeTab by creativeTab("creative_tab") {
        icon { ModItems.NPC_TOOL.defaultInstance }
        title("itemGroup.hollowengine".mcTranslate)
    }
}