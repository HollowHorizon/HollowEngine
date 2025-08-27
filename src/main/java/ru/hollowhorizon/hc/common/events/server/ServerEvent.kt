package ru.hollowhorizon.hc.common.events.server

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.common.events.Event

open class ServerEvent(val server: MinecraftServer) : Event {
    class Starting(server: MinecraftServer) : ServerEvent(server)
    class Stoping(server: MinecraftServer) : ServerEvent(server)
}