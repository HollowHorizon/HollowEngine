package ru.hollowhorizon.hollowengine.common.events.tick

import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.Event

open class TickEvent : Event {
    class Server(val server: MinecraftServer) : TickEvent()
    class Client(val minecraft: Minecraft) : TickEvent(), ClientEvent
    class Entity(val entity: net.minecraft.world.entity.LivingEntity) : TickEvent()
}