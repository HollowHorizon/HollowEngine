package ru.hollowhorizon.hollowengine.common.events.registry

import net.minecraft.server.packs.resources.PreparableReloadListener
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

open class RegisterReloadListenersEvent : Event {
    val listeners = HashSet<PreparableReloadListener>()
    fun register(listener: PreparableReloadListener) {
        listeners += listener
    }

    class Client : RegisterReloadListenersEvent() {
        companion object : EventHandler<Client>()
    }

    class Server : RegisterReloadListenersEvent() {
        companion object : EventHandler<Server>()
    }
}