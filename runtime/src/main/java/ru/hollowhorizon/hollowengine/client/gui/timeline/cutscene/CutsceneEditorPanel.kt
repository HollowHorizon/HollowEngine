package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.Grow
import de.fabmax.kool.modules.ui2.Row
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.remember
import de.fabmax.kool.util.Color
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TimelineArea
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.Toolbar
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TrackHeaderList
import ru.hollowhorizon.hollowengine.generated.Assets

class CutsceneEditorPanel(dock: Dock) : DockPanel(MAIN_PANEL_ID, dock) {
    override val icon: ResourceLocation = Assets.Hollowengine.Textures.Gui.Icons.FILM

    private val session = CutsceneEditorSessions.default

    override fun open() {
        super.open()
        CutscenePanelDocking.openAuxiliaryPanels(dockable)
    }

    override fun UiScope.drawHeaderLeft(color: Color) {
        Toolbar(session.timeline, color) {}
    }

    override fun UiScope.compose() {
        val trackMenu = remember { ItemPopupMenu<AnimTrack<*>>("cutscene-timeline-track-menu") }
        session.timeline.onTrackLaneContextMenu = { event, track ->
            trackMenu.show(event.screenPosition, session.buildTrackMenu(trackMenu), track)
        }

        Row(Grow.Std) {
            TrackHeaderList(session.timeline)
            TimelineArea(session.timeline)
        }

        trackMenu()
        session.update()
    }

    override fun onKeyInput(event: KeyEvent) {
        session.onKeyInput(event)
    }
}

private object CutscenePanelDocking {
    fun openAuxiliaryPanels(mainDockable: Dockable) {
        val properties = LayoutLoader.LAYOUTS[PROPERTIES_PANEL_ID] ?: return

        properties.open()

        val mainLeaf = mainDockable.dockedTo.value ?: return
        val onlyMainInLeaf = mainLeaf.dockedItems.count { !it.isHidden } == 1
        if (!onlyMainInLeaf) return

        val updatedMainLeaf = mainDockable.dockedTo.value ?: return
        if (properties.dockable.dockedTo.value == null) {
            updatedMainLeaf.insertItem(
                properties.dockable,
                DockNode.SlotPosition.Right,
            )
        }
    }
}

const val MAIN_PANEL_ID = "hollowengine.gui.ide.cutscene"
const val PROPERTIES_PANEL_ID = "hollowengine.gui.ide.cutscene.properties"
