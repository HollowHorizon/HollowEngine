package ru.hollowhorizon.hollowstory.common.registry

import net.minecraft.item.Item
import ru.hollowhorizon.hc.api.registy.HollowRegister

object ModItems {
    @HollowRegister(auto_model = true)
    @JvmField
    val DIALOGUE_ICON = Item(Item.Properties().stacksTo(1))

    @HollowRegister(auto_model = true)
    @JvmField
    val QUESTION_ICON = Item(Item.Properties().stacksTo(1))

    @HollowRegister(auto_model = true)
    @JvmField
    val WARN_ICON = Item(Item.Properties().stacksTo(1))
}