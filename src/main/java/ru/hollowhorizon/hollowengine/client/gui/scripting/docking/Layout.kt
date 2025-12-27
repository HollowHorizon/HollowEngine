package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import de.fabmax.kool.modules.ui2.docking.Dockable
import net.minecraft.resources.ResourceLocation

interface Layout {
    val dockable: Dockable
    val name: String
    val icon: ResourceLocation

    fun open()

    fun close()
}