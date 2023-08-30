package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.world.item.Item
import ru.hollowhorizon.hc.api.registy.HollowRegister
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.common.items.StoryTellerDimItem

object ModItems : HollowRegistry() {
    val DIALOGUE_ICON by register("dialogue_icon") { Item(Item.Properties().stacksTo(1)) }

    val QUESTION_ICON by register("question_icon") { Item(Item.Properties().stacksTo(1)) }

    val WARN_ICON by register("warn_icon") { Item(Item.Properties().stacksTo(1)) }

    val STORYTELLER_DIM_TELEPORTER by register("storyteller_dim_teleporter", ::StoryTellerDimItem)
}