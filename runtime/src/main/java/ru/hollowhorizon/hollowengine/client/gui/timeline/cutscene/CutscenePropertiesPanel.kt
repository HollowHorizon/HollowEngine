package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.Grow
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.dp
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.PropertiesPanel
import ru.hollowhorizon.hollowengine.generated.Assets

class CutscenePropertiesPanel(dock: Dock) : DockPanel(PROPERTIES_PANEL_ID, dock) {
    override val icon: ResourceLocation = Assets.Hollowengine.Textures.Gui.Icons.OPTIONS

    private val session = CutsceneEditorSessions.default

    init {
        dockable.floatingWidth.set(360.dp)
        session._propertiesSurface = { surface }
    }

    override fun UiScope.compose() {
        PropertiesPanel(session.timeline, Grow.Std)
    }

    override fun onKeyInput(event: KeyEvent) {
        session.onKeyInput(event)
    }
}
