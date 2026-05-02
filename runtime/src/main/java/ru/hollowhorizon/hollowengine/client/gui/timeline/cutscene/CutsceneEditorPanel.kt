package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TimelineArea
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.Toolbar
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.ToolbarIconButton
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TrackHeaderList
import ru.hollowhorizon.hollowengine.generated.Assets

class CutsceneEditorPanel(dock: Dock) : DockPanel(MAIN_PANEL_ID, dock) {
    override val icon: ResourceLocation = Assets.Hollowengine.Textures.Gui.Icons.FILM

    private val session = CutsceneEditorSessions.default
    private val fileDialog = CutsceneFileDialog(session)

    override fun UiScope.drawHeaderLeft(color: Color) {
        Toolbar(session.timeline, color) {
            Box(Dimensions.PaddingLarge, Grow.Std) {
                modifier
                    .width(Dimensions.PaddingSmall)
                    .margin(horizontal = Dimensions.PaddingHuge)
                    .backgroundColor(ColorTheme.UI.BackgroundElements)
                    .alignY(AlignmentY.Center)
            }

            ToolbarIconButton(
                icon = session.timeline.iconSave,
                padding = 4.dp,
                size = 18.dp,
            ) {
                fileDialog.showExport(session.playback.toData().name)
            }
            ToolbarIconButton(
                icon = session.timeline.iconLoad,
                padding = 4.dp,
                size = 18.dp,
            ) {
                fileDialog.showImport()
            }
        }
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
        fileDialog.draw()
        session.update()
    }

    override fun onKeyInput(event: KeyEvent) {
        session.onKeyInput(event)
    }
}

const val MAIN_PANEL_ID = "hollowengine.gui.ide.cutscene"
const val PROPERTIES_PANEL_ID = "hollowengine.gui.ide.cutscene.properties"
