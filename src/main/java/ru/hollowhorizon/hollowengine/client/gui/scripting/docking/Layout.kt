package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import de.fabmax.kool.modules.ui2.docking.Dockable

interface Layout {
    val dockable: Dockable
    val name: String
    val icon: String
}