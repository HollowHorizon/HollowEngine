package ru.hollowhorizon.hollowengine.common.ui.net

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.ui.hud.ServerHudLayers

/**
 * Server-side entry points for driving scripted UI. A story script opens a screen by the id its
 * `.ui.kts` declared and then talks to the returned [UiSession]:
 *
 * ```kotlin
 * val session = player.openUi("mypack:quest_log") {
 *     put(Title, "The Missing Cargo")
 *     onEvent { payload -> if (payload.getString("action") == "accept") acceptQuest() }
 * }
 * ```
 */
fun ServerPlayer.openUi(
    screen: ResourceLocation,
    initialState: CompoundTag = CompoundTag(),
    body: UiSession.() -> Unit = {},
): UiSession = UiSessionManager.open(this, screen, UiSurfaceKind.SCREEN, initialState, body)

fun ServerPlayer.openUi(
    screen: String,
    initialState: CompoundTag = CompoundTag(),
    body: UiSession.() -> Unit = {},
): UiSession = openUi(ResourceLocation.parse(screen), initialState, body)

/** Shows a scripted HUD overlay for this player and returns the session that drives it. */
fun ServerPlayer.showOverlay(
    overlay: ResourceLocation,
    initialState: CompoundTag = CompoundTag(),
    body: UiSession.() -> Unit = {},
): UiSession = UiSessionManager.open(this, overlay, UiSurfaceKind.OVERLAY, initialState, body)

fun ServerPlayer.showOverlay(
    overlay: String,
    initialState: CompoundTag = CompoundTag(),
    body: UiSession.() -> Unit = {},
): UiSession = showOverlay(ResourceLocation.parse(overlay), initialState, body)

/** Closes every UI this player has open through the engine. */
fun ServerPlayer.closeUi() = UiSessionManager.closeAll(this)

/**
 * Replaces the HUD layers hidden for this player. Vanilla layers are addressed by the ids in
 * [ru.hollowhorizon.hollowengine.common.ui.hud.VanillaHudLayers], e.g.
 * `player.hideHudLayers(VanillaHudLayers.CROSSHAIR, VanillaHudLayers.HOTBAR)`.
 */
fun ServerPlayer.hideHudLayers(vararg layers: ResourceLocation) {
    ServerHudLayers[uuid] = layers.toSet()
    SetHiddenHudLayersPacket(layers.toList()).send(this)
}

/** Clears every HUD layer this player has hidden. */
fun ServerPlayer.showAllHudLayers() {
    ServerHudLayers.clear(uuid)
    SetHiddenHudLayersPacket(emptyList()).send(this)
}

/** The layers currently hidden for this player, as the server believes them to be. */
fun ServerPlayer.hiddenHudLayers(): Set<ResourceLocation> = ServerHudLayers[uuid]

/** Re-sends the player's hidden layers, e.g. after a respawn resets client state. */
fun ServerPlayer.resendHiddenHudLayers() {
    SetHiddenHudLayersPacket(ServerHudLayers[uuid].toList()).send(this)
}
