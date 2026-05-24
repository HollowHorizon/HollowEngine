package ru.hollowhorizon.hollowengine.common.events.server

import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding

@ScriptBinding
open class ServerEvent(val server: MinecraftServer) : Event {
    @ScriptBinding
    class Starting(server: MinecraftServer) : ServerEvent(server) {
        companion object: EventHandler<Starting>()
    }
    @ScriptBinding
    class Stoping(server: MinecraftServer) : ServerEvent(server) {
        companion object: EventHandler<Stoping>()
    }
}
