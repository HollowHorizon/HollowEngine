//? if fabric {
package ru.hollowhorizon.hollowengine.fabric.internal

import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.network.*
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks

object NetworkHelper {
    fun register() {
        registerPacket = { type: Class<*> ->
            registerPacket<HollowPacket>(JavaHacks.forceCast(type))
        }
        sendPacketToClient = { player: ServerPlayer, hollowPacketV3: HollowPacket ->
            player.server.coroutineScope.launch {
                while(player.connection == null && !player.isRemoved) {
                    yield() // Если пакет отправляется до инициализации, то ждём каждый тик
                }
                if (player.connection != null && !player.isRemoved) {
                    player.connection.send(hollowPacketV3.asVanillaPacket(true))
                } else {
                    HollowEngine.LOGGER.warn("Player ${player.name} removed, but packet still trying to send")
                }
            }
        }
        sendPacketToServer = { hollowPacketV3: HollowPacket? ->
            val connection = Minecraft.getInstance().getConnection()
            if (connection != null) connection.send(hollowPacketV3!!.asVanillaPacket(false))
            Unit
        }
        registerPackets.invoke()
    }
} //?}
