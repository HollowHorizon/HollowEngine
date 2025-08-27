package ru.hollowhorizon.hc.common.events.container

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.capabilities.containers.HollowContainer
import ru.hollowhorizon.hc.common.events.Cancelable
import ru.hollowhorizon.hc.common.events.entity.player.PlayerEvent

open class ContainerEvent(player: Player, val container: HollowContainer): PlayerEvent(player), Cancelable {
    override var isCanceled = false
    class OnTake(player: Player, container: HollowContainer, val slot: Int): ContainerEvent(player, container)
    class OnPlace(player: Player, container: HollowContainer, val slot: Int): ContainerEvent(player, container)
    class OnClick(player: Player, container: HollowContainer, val slot: Int): ContainerEvent(player, container)
}