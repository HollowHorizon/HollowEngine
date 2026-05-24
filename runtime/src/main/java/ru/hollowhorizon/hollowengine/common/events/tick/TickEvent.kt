package ru.hollowhorizon.hollowengine.common.events.tick

import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding

open class TickEvent : Event {
    @ScriptBinding
    class Server(val server: MinecraftServer) : TickEvent() {
        companion object : EventHandler<Server>()
    }

    class Client(val minecraft: Minecraft) : TickEvent(), ClientEvent {
        companion object : EventHandler<Client>()
    }

    @ScriptBinding
    class Entity(val entity: net.minecraft.world.entity.LivingEntity) : TickEvent() {
        companion object : EventHandler<Entity>()
    }
}
