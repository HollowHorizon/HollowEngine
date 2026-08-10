@file:UseSerializers(ForResourceLocation::class, ForCompoundNBT::class)

package ru.hollowhorizon.hollowengine.common.ui.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.ui.script.UiScriptClient
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

/** Opens a scripted screen and binds it to the server session [sessionId]. */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class OpenUiScreenPacket(
    val sessionId: Int,
    val screen: ResourceLocation,
    val state: CompoundTag = CompoundTag(),
) : HollowPacket {
    override fun handle(player: Player) = UiScriptClient.openScreen(sessionId, screen, state)
}

/** Shows a scripted HUD overlay, bound to the server session [sessionId]. */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ShowUiOverlayPacket(
    val sessionId: Int,
    val overlay: ResourceLocation,
    val state: CompoundTag = CompoundTag(),
) : HollowPacket {
    override fun handle(player: Player) = UiScriptClient.showOverlay(sessionId, overlay, state)
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class OpenUiSurfacePacket(
    val sessionId: Int,
    val surface: ResourceLocation,
    val state: CompoundTag = CompoundTag(),
) : HollowPacket {
    override fun handle(player: Player) = UiScriptClient.openSurface(sessionId, surface, state)
}

/**
 * Merges [patch] into a session's bound document and drops [removed] paths. Only changed fields
 * travel, so a HUD bound to a frequently changing value does not resend the whole document.
 */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class PatchUiDataPacket(
    val sessionId: Int,
    val patch: CompoundTag = CompoundTag(),
    val removed: List<String> = emptyList(),
) : HollowPacket {
    override fun handle(player: Player) = UiScriptClient.applyPatch(sessionId, patch, removed)
}

/**
 * Closes a session's UI. Travels both ways: the server dismisses a screen with it, and the client
 * reports a screen the player closed so the server can drop the session.
 */
@HollowPacketHandler(HollowPacketHandler.Direction.ANY)
@Serializable
class CloseUiPacket(val sessionId: Int) : HollowPacket {
    override fun handle(player: Player) {
        if (player.level().isClientSide) UiScriptClient.close(sessionId)
        else UiSessionManager.onClientClosed(player, sessionId)
    }
}

/** Carries a payload a scripted UI produced back to the session that opened it. */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class UiEventPacket(
    val sessionId: Int,
    val payload: CompoundTag = CompoundTag(),
) : HollowPacket {
    override fun handle(player: Player) = UiSessionManager.onClientEvent(player, sessionId, payload)
}

/** Replaces the set of HUD layers the server wants hidden, vanilla ones included. */
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class SetHiddenHudLayersPacket(val layers: List<ResourceLocation> = emptyList()) : HollowPacket {
    override fun handle(player: Player) = UiScriptClient.setHiddenLayers(layers)
}
