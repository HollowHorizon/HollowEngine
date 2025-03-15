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

    val surface: UiSurface = WindowSurface(dockable, IdeTheme.colors, IdeTheme.sizes) {
        modifier.border(null)
        dockable.dockedTo.use()?.let {
            val isPanelBarLeft = it.boundsLeftDp.value.px < 1f
                    || it.boundsRightDp.value.px < it.dock.root.boundsRightDp.value.px * 0.99f

            Row(Grow.Std, Grow.Std) {
                if (isPanelBarLeft) {
                    ToolBar(this@DockPanel, true)
                    Box(width = sizes.borderWidth, height = Grow.Std) { modifier.backgroundColor(UiColors.titleBg) }
                    if(isOpened) panelContent()
                } else {
                    if(isOpened) panelContent()
                    Box(width = sizes.borderWidth, height = Grow.Std) { modifier.backgroundColor(UiColors.titleBg) }
                    ToolBar(this@DockPanel, false)
                }
            }
        } ?: run {
            panelContent()
        }
    }

    final override var isOpened = false
        set(value) {
            field = value
            surface.triggerUpdate()
        }

    private fun UiScope.panelContent() {
        Column(Grow.Std, Grow.Std) {
            FileTitleBar(dockable, showTabsIfDocked = false)
            this@DockPanel()
        }
    }

    init {
        dock.addDockableSurface(dockable, surface)
    }
}