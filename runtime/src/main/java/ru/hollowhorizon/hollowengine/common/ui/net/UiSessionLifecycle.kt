package ru.hollowhorizon.hollowengine.common.ui.net

import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.ui.hud.ServerHudLayers

/**
 * Keeps server-side UI state tied to the player's connection: a session must not outlive the player
 * that owns it, and a fresh client starts with no HUD suppressed unless the server says otherwise.
 */
@SubscribeEvent
fun onPlayerLeaveDropUiSessions(event: PlayerEvent.Leave) {
    UiSessionManager.closeAll(event.player)
    ServerHudLayers.clear(event.player.uuid)
}

/** A respawned client rebuilds its HUD from scratch, so the hidden layers have to be re-sent. */
@SubscribeEvent
fun onPlayerRespawnRestoreHudLayers(event: PlayerEvent.Respawn) {
    (event.player as? ServerPlayer)?.resendHiddenHudLayers()
}

/** Same for a dimension change, which also rebuilds the client HUD. */
@SubscribeEvent
fun onPlayerChangeDimensionRestoreHudLayers(event: PlayerEvent.ChangeDimension) {
    (event.player as? ServerPlayer)?.resendHiddenHudLayers()
}
