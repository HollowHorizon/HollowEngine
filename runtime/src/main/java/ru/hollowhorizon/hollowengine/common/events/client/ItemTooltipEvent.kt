package ru.hollowhorizon.hollowengine.common.events.client

import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import ru.hollowhorizon.hollowengine.common.events.ClientEvent

class ItemTooltipEvent(
    val flags: TooltipFlag,
    val itemStack: ItemStack,
    val toolTip: MutableList<Component>,
    val context: Item.TooltipContext,
) : ClientEvent