package ru.hollowhorizon.hollowengine.common.items.dynamic

import net.minecraft.world.item.Item
import net.minecraft.world.item.CreativeModeTab
import ru.hollowhorizon.hollowengine.common.objects.items.CreativeTab

open class DynamicItem(properties: Properties) : Item(properties)

class DynamicTabItem(
    properties: Properties,
    private val creativeTab: CreativeModeTab,
) : DynamicItem(properties), CreativeTab {
    override fun tab(): CreativeModeTab = creativeTab
}
