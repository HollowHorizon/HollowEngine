package ru.hollowhorizon.hollowengine.common.events

import net.minecraft.world.entity.player.Player
import net.minecraftforge.event.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.util.Keybind

class ServerKeyPressedEvent(player: Player, val keybind: Keybind) : PlayerEvent(player)