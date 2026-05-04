package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class LoadLayoutEvent(private val provider: (String, Layout) -> Unit, val dock: Dock) : ClientEvent {
    fun provide(name: String, dockable: (Dock) -> Layout) {
        provider(name, dockable(dock))
    }

    companion object : EventHandler<LoadLayoutEvent>()
}