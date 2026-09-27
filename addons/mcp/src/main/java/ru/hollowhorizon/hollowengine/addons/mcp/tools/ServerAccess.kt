package ru.hollowhorizon.hollowengine.addons.mcp.tools

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.common.utils.currentServerOrNull

/**
 * The logical server of this game process and what the agent may do on it. A dedicated server trusts
 * the agent like its own console; in a singleplayer or LAN world the agent has the host's rights.
 */
internal object ServerAccess {
    const val NO_SERVER = "No world is open in this game process, so there is no server to act on. " +
        "A client connected to a remote server cannot reach that server's commands or code from here."

    const val NO_CHEATS = "The host of this world has no operator rights (cheats are off), so server code " +
        "cannot run here. Enable cheats (Open to LAN → Allow Commands) or use the client side."

    fun current(): MinecraftServer? = currentServerOrNull()?.takeIf(MinecraftServer::isRunning)

    /** The player hosting a singleplayer or LAN world. Call on the server thread. */
    fun host(server: MinecraftServer): ServerPlayer? =
        server.playerList.players.firstOrNull { server.isSingleplayerOwner(it.gameProfile) }

    /** Whether server snippets may run on [server]. Call on the server thread. */
    fun mayRunCode(server: MinecraftServer): Boolean =
        server.isDedicatedServer || host(server)?.hasPermissions(2) == true
}
