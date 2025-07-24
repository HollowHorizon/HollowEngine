package ru.hollowhorizon.hollowengine.client.gui.kool

import de.fabmax.kool.modules.ui2.PointerEvent
import de.fabmax.kool.modules.ui2.docking.Dockable

interface DockLock {
    fun canInsert(dragItem: Dockable, dragPointer: PointerEvent): Boolean
}