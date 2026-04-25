package ru.hollowhorizon.hollowengine.common.geary.sync

import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.binding.NodeRuntimeState

@SubscribeEvent
fun startTracking(event: EntityTrackingEvent.Start) {
    NodeRuntimeState.service(event.entity.level()).syncEntityNodesToPlayer(event.player as ServerPlayer, event.entity)
}

@SubscribeEvent
fun stopTracking(event: EntityTrackingEvent.Stop) {
    NodeRuntimeState.service(event.entity.level())
        .removeEntityNodesFromPlayer(event.player as ServerPlayer, event.entity.uuid)
}

@SubscribeEvent
fun onClone(event: PlayerEvent.Clone) {
    val old = event.oldPlayer
    val new = event.player

    GearyRuntimeState.cloneOwnedState(old, new, dropLooseOnDeath = event.wasDeath)

    new.server?.coroutineScope?.launch {
        yield()
        (new as? ServerPlayer)?.let { serverPlayer ->
            NodeRuntimeState.service(serverPlayer.level()).syncEntityNodesToPlayer(serverPlayer, serverPlayer)
        }
    }
}

@SubscribeEvent
fun onJoin(event: PlayerEvent.Join) {
    val player = event.player as? ServerPlayer ?: return
    NodeRuntimeState.service(player.level()).syncEntityNodesToPlayer(player, player)
}

@SubscribeEvent
fun onRespawn(event: PlayerEvent.Respawn) {
    val player = event.player as? ServerPlayer ?: return
    NodeRuntimeState.service(player.level()).syncEntityNodesToPlayer(player, player)
}

@SubscribeEvent
fun onChangeDimension(event: PlayerEvent.ChangeDimension) {
    val player = event.player as? ServerPlayer ?: return
    player.server?.coroutineScope?.launch {
        yield()
        NodeRuntimeState.service(player.level()).syncEntityNodesToPlayer(player, player)
    }
}
