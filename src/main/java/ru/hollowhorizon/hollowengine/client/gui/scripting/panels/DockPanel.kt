package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.UiDockable
import ru.hollowhorizon.hollowengine.client.gui.kool.UiColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileTitleBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.ToolBar

abstract class DockPanel(final override val name: String, dock: Dock) : Layout, Composable {
    final override val dockable = UiDockable(name, dock)
    var showOnToolbar = true

    val surface: UiSurface = WindowSurface(dockable, dock.dockingSurface.colors, dock.dockingSurface.sizes) {
        modifier.border(null)
        if (!showOnToolbar) {
            panelContent()
            return@WindowSurface
        }

        dockable.dockedTo.use()?.let {
            val isPanelBarLeft = it.boundsLeftDp.value.px < 1f
                    || it.boundsRightDp.value.px < it.dock.root.boundsRightDp.value.px * 0.99f

            Row(Grow.Std, Grow.Std) {
                if (isPanelBarLeft) {
                    ToolBar(this@DockPanel, true)
                    Box(width = sizes.borderWidth, height = Grow.Std) { modifier.backgroundColor(UiColors.titleBg) }
                    panelContent()
                } else {
                    panelContent()
                    Box(width = sizes.borderWidth, height = Grow.Std) { modifier.backgroundColor(UiColors.titleBg) }
                    ToolBar(this@DockPanel, false)
                }
            }
        } ?: run {
            panelContent()
        }
    }

    private fun UiScope.panelContent() {
        Column(Grow.Std, Grow.Std) {
            FileTitleBar(dockable, showTabsIfDocked = !showOnToolbar)
            this@DockPanel()
        }
    }

    init {
        dock.addDockableSurface(dockable, surface)
    }
}