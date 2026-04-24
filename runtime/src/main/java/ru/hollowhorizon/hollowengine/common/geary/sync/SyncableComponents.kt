package ru.hollowhorizon.hollowengine.common.geary.sync

import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityTrackingEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.geary.anchor.MaterializationRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.snapshot.applySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf

@SubscribeEvent
fun startTracking(event: EntityTrackingEvent.Start) {
    MaterializationRuntimeState.service(event.entity.level()).syncEntityAnchorsToPlayer(event.player as ServerPlayer, event.entity)
}

@SubscribeEvent
fun stopTracking(event: EntityTrackingEvent.Stop) {
    MaterializationRuntimeState.service(event.entity.level())
        .removeEntityAnchorsFromPlayer(event.player as ServerPlayer, event.entity.uuid)
}

@SubscribeEvent
fun onClone(event: PlayerEvent.Clone) {
    val old = event.oldPlayer
    val new = event.player

    MaterializationRuntimeState.service(old.level()).rebindEntityAnchors(old.uuid)

    val snapshot = snapshotOf(old.entity)
    val filtered = if (event.wasDeath) snapshot.dropLooseOnDeathComponents() else snapshot

    new.server?.coroutineScope?.launch {
        yield()
        applySnapshot(new.entity, filtered)
    }
}
