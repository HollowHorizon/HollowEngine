package ru.hollowhorizon.hollowengine.common.events.brew

import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent

class BrewedPlayerPotionEvent(player: Player, val stack: ItemStack) : PlayerEvent(player)