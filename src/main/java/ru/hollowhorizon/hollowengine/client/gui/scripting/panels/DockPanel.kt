package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.releaseDelayed
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileTitleBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.ToolBar

abstract class DockPanel(final override val name: String, val dock: Dock) : Layout, Composable {
    final override val dockable = UiDockable(name, dock)
    var showOnToolbar = true

    private var surface: UiSurface? = null

    val isCollapsed = mutableStateOf(false)
    val isDocked: Boolean get() = dockable.dockedTo.value != null

    private fun UiScope.panelContent() {
        Column(Grow.Std, Grow.Std) {
            FileTitleBar(
                icon,
                dockable,
                isCollapsed,
                showTabsIfDocked = !showOnToolbar,
                onCloseAction = {
                    close()
                })
            if(!isCollapsed.use()) this@DockPanel()
        }
    }

    override fun open() {
        if (surface != null) return

        dockable.floatingX.set(Dp(5f))
        dockable.floatingY.set(Dp.fromPx(ScriptingEnvironmentOverlay.titleBarHeight) + Dp(5f))

        surface = WindowSurface(dock.dockingSurface.parentScene, dockable, dock.dockingSurface.colors, dock.dockingSurface.sizes) {
            modifier.border(null).backgroundColor(ColorTheme.UI.BackgroundGeneral)
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
                        panelContent()
                    } else {
                        panelContent()
                        ToolBar(this@DockPanel, false)
                    }
                }
            } ?: run {
                panelContent()
            }
        }.also { dock.addDockableSurface(dockable, it) }
    }

    override fun close() {
        surface?.let {
            dock.removeDockableSurface(it)
            it.releaseDelayed(1)
        }
        surface = null
    }

    protected open fun UiScope.drawHeaderLeft() {}
    protected open fun UiScope.drawHeaderRight() {}
}