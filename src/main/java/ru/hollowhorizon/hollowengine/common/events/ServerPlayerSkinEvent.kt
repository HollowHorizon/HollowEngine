package ru.hollowhorizon.hollowengine.common.events

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.entity.player.PlayerEvent

class ServerPlayerSkinEvent(player: Player, val skin: ResourceLocation) : PlayerEvent(player)
