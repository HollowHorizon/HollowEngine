package ru.hollowhorizon.hollowengine.common.ui.net

import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns the server's open UI sessions. Sessions are per-player and identified by a plain counter that
 * both sides quote, which keeps late packets from a closed screen from landing on a newer one.
 */
object UiSessionManager {
    private val sessions = ConcurrentHashMap<Int, UiSession>()
    private val nextId = AtomicInteger(1)

    fun open(
        player: ServerPlayer,
        surface: ResourceLocation,
        kind: UiSurfaceKind,
        initialState: CompoundTag,
        body: UiSession.() -> Unit,
    ): UiSession {
        val session = UiSession(nextId.getAndIncrement(), player, surface, kind)
        session.seed(initialState)
        session.body()
        sessions[session.id] = session

        val state = session.initialState()
        when (kind) {
            UiSurfaceKind.SCREEN -> OpenUiScreenPacket(session.id, surface, state).send(player)
            UiSurfaceKind.OVERLAY -> ShowUiOverlayPacket(session.id, surface, state).send(player)
        }
        return session
    }

    fun close(session: UiSession) {
        if (sessions.remove(session.id) == null) return
        if (!session.player.hasDisconnected()) CloseUiPacket(session.id).send(session.player)
        session.dispatchClose()
    }

    fun sessionsOf(player: Player): List<UiSession> = sessions.values.filter { it.player.uuid == player.uuid }

    fun closeAll(player: Player) = sessionsOf(player).forEach(::close)

    internal fun onClientEvent(player: Player, sessionId: Int, payload: CompoundTag) {
        ownedSession(player, sessionId)?.dispatchEvent(payload)
    }

    internal fun onClientClosed(player: Player, sessionId: Int) {
        val session = ownedSession(player, sessionId) ?: return
        sessions.remove(session.id)
        session.dispatchClose()
    }

    /** Looks a session up, refusing ids belonging to another player so a client cannot drive them. */
    private fun ownedSession(player: Player, sessionId: Int): UiSession? =
        sessions[sessionId]?.takeIf { it.player.uuid == player.uuid }
}
