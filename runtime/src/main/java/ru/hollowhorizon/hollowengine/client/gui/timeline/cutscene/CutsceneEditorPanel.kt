package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Box
import de.fabmax.kool.modules.ui2.Button
import de.fabmax.kool.modules.ui2.Column
import de.fabmax.kool.modules.ui2.Grow
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.dp
import de.fabmax.kool.modules.ui2.margin
import de.fabmax.kool.modules.ui2.onClick
import de.fabmax.kool.modules.ui2.remember
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TimelineArea
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.Toolbar
import ru.hollowhorizon.hollowengine.generated.Assets

class CutsceneEditorPanel(dock: Dock) : DockPanel(MAIN_PANEL_ID, dock) {
    override val icon: ResourceLocation = Assets.Hollowengine.Textures.Gui.Icons.FILM

    private val session = CutsceneEditorSessions.default

    override fun open() {
        super.open()
        CutscenePanelDocking.openAuxiliaryPanels(dockable)
    }

    override fun UiScope.drawHeaderLeft() {
        Toolbar(session.timeline) {
            Button("Capture") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = sizes.smallGap)
                    .onClick { session.captureCurrentCamera() }
            }

            Button("Stop") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = sizes.smallGap)
                    .onClick { session.stopPreview() }
            }
        }
    }

    override fun UiScope.compose() {
        val trackMenu = remember { ItemPopupMenu<AnimTrack<*>>("cutscene-timeline-track-menu") }
        session.timeline.onTrackLaneContextMenu = { event, track ->
            trackMenu.show(event.screenPosition, session.buildTrackMenu(trackMenu), track)
        }

        Column(Grow.Std, Grow.Std) {
            TimelineArea(session.timeline)

            Box(Grow.Std, 22.dp) {
                Text(session.status.use()) {
                    modifier
                        .alignY(AlignmentY.Center)
                        .margin(start = sizes.gap)
                        .textColor(Color.WHITE.withAlpha(0.65f))
                }
            }
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
        val tracks = LayoutLoader.LAYOUTS[TRACKS_PANEL_ID] ?: return
        val properties = LayoutLoader.LAYOUTS[PROPERTIES_PANEL_ID] ?: return

        tracks.open()
        properties.open()

        val mainLeaf = mainDockable.dockedTo.value ?: return
        val onlyMainInLeaf = mainLeaf.dockedItems.count { !it.isHidden } == 1
        if (!onlyMainInLeaf) return

        if (tracks.dockable.dockedTo.value == null) {
            mainLeaf.insertItem(tracks.dockable, DockNode.SlotPosition.Left)
        }

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
const val TRACKS_PANEL_ID = "hollowengine.gui.ide.cutscene.tracks"
const val PROPERTIES_PANEL_ID = "hollowengine.gui.ide.cutscene.properties"
