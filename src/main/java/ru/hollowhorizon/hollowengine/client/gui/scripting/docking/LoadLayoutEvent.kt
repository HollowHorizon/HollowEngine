package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hc.common.events.Event

class LoadLayoutEvent(private val provider: (String, Layout) -> Unit, val dock: Dock) : Event {
    fun provide(name: String, dockable: (Dock) -> Layout) {
        provider(name, dockable(dock))
    }
}