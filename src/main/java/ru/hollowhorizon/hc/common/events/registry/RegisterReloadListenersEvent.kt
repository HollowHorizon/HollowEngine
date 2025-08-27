package ru.hollowhorizon.hc.common.events.registry

import net.minecraft.server.packs.resources.PreparableReloadListener
import ru.hollowhorizon.hc.common.events.Event

open class RegisterReloadListenersEvent : Event {
    val listeners = HashSet<PreparableReloadListener>()
    fun register(listener: PreparableReloadListener) {
        listeners += listener
    }

    class Client : RegisterReloadListenersEvent()
    class Server : RegisterReloadListenersEvent()
}