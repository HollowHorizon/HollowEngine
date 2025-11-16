package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.kool.UiColors
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileTitleBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.ToolBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverListener

abstract class DockPanel(final override val name: String, val dock: Dock) : Layout, Composable {
    final override val dockable = UiDockable(name, dock)
    var showOnToolbar = true

    private var surface: UiSurface? = null

    val isDocked: Boolean get() = dockable.dockedTo.value != null

    private fun UiScope.panelContent() {
        val (isHovered, anim) = hoverListener { !surface.isFocused.use() }

        var factor = Easing.quadRev(anim.progressAndUse())
        if (!isHovered.use() && !surface.isFocused.use()) factor = 1f - factor

        val borderColor = Color("3C3C4AFF").mix(Color("586D84FF"), factor)
        if (!isDocked) modifier.border(RoundRectBorder(borderColor, sizes.smallGap, sizes.borderWidth))
        modifier.background(RoundRectBackground(colors.backgroundVariant, sizes.smallGap))

        Column(Grow.Std, Grow.Std) {
            FileTitleBar(
                dockable,
                showTabsIfDocked = !showOnToolbar,
                drawAlignLeft = { drawHeaderLeft() },
                drawAlignRight = { drawHeaderRight() },
                onCloseAction = {
                    close()
                })
            this@DockPanel()
        }
    }

    override fun open() {
        if (surface != null) return

        dockable.floatingX.set(Dp(5f))
        dockable.floatingY.set(Dp.fromPx(ScriptingEnvironmentOverlay.titleBarHeight) + Dp(5f))

        surface = WindowSurface(dockable, dock.dockingSurface.colors, dock.dockingSurface.sizes) {
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
        }.also { dock.addDockableSurface(dockable, it) }
    }

    override fun close() {
        surface?.let {
            dock.removeDockableSurface(it)
            it.release()
        }
        surface = null
    }

    protected open fun UiScope.drawHeaderLeft() {}
    protected open fun UiScope.drawHeaderRight() {}
}