package ru.hollowhorizon.hollowengine.common.events.server

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

open class ServerEvent(val server: MinecraftServer) : Event {
    class Starting(server: MinecraftServer) : ServerEvent(server) {
        companion object: EventHandler<Starting>()
    }
    class Stoping(server: MinecraftServer) : ServerEvent(server) {
        companion object: EventHandler<Stoping>()
    }
}