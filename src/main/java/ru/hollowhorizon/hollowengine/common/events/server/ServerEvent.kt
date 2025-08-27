package ru.hollowhorizon.hollowengine.common.events.server

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.events.Event

open class ServerEvent(val server: MinecraftServer) : Event {
    class Starting(server: MinecraftServer) : ServerEvent(server)
    class Stoping(server: MinecraftServer) : ServerEvent(server)
}