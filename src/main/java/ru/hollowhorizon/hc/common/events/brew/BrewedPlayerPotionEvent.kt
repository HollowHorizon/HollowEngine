package ru.hollowhorizon.hc.common.events.brew

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.common.events.entity.player.PlayerEvent

class BrewedPlayerPotionEvent(player: Player, val stack: ItemStack) : PlayerEvent(player)