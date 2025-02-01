package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.UiDockable
import ru.hollowhorizon.hollowengine.client.gui.kool.UiColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileTitleBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideSizes
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.ToolBar

abstract class DockPanel(val name: String, dock: Dock) : Composable {
    val dockable = UiDockable(name, dock)
    val surface: UiSurface = WindowSurface(dockable, ideColors, ideSizes) {
        dockable.dockedTo.use()?.let {
            val isPanelBarLeft = it.boundsLeftDp.value.px < 1f
                    || it.boundsRightDp.value.px < it.dock.root.boundsRightDp.value.px * 0.99f

            Row(Grow.Std, Grow.Std) {
                if (isPanelBarLeft) {
                    ToolBar(this@DockPanel)
                    Box(width = sizes.borderWidth, height = Grow.Std) { modifier.backgroundColor(UiColors.titleBg) }
                    panelContent()
                } else {
                    panelContent()
                    Box(width = sizes.borderWidth, height = Grow.Std) { modifier.backgroundColor(UiColors.titleBg) }
                    ToolBar(this@DockPanel)
                }
            }
        } ?: run {
            panelContent()
        }
    }

    abstract val icon: String

    private fun UiScope.panelContent() {
        Column(Grow.Std, Grow.Std) {
            FileTitleBar(dockable, showTabsIfDocked=false)
            this@DockPanel()
        }
    }

    init {
        dock.addDockableSurface(dockable, surface)
    }
}