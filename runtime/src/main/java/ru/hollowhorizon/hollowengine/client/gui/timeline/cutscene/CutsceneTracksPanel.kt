package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.Grow
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.dp
import de.fabmax.kool.modules.ui2.remember
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TrackHeaderList
import ru.hollowhorizon.hollowengine.generated.Assets

class CutsceneTracksPanel(dock: Dock) : DockPanel(TRACKS_PANEL_ID, dock) {
    override val icon: ResourceLocation = Assets.Hollowengine.Textures.Gui.Icons.LAYERS

    private val session = CutsceneEditorSessions.default

    init {
        dockable.floatingWidth.set(280.dp)
    }

    override fun UiScope.compose() {
        val trackMenu = remember { ItemPopupMenu<AnimTrack<*>>("cutscene-header-track-menu") }
        session.timeline.onTrackHeaderContextMenu = { event, track ->
            trackMenu.show(event.screenPosition, session.buildTrackMenu(trackMenu), track)
        }

        TrackHeaderList(session.timeline, Grow.Std)
        trackMenu()
    }

    override fun onKeyInput(event: KeyEvent) {
        session.onKeyInput(event)
    }
}
