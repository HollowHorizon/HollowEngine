package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.InputStack
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Time
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileTitleBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem

abstract class DockPanel(final override val name: String, val dock: Dock) : Layout, Composable {
    final override val dockable = UiDockable(name, dock)

    val surface by lazy { createSurface() }

    private val inputListener = InputStack.InputHandler("$name Input Handler").apply {
        keyboardListeners += object : InputStack.KeyboardListener {
            override fun handleKeyboard(keyEvents: List<KeyEvent>, ctx: KoolContext) {
                val activeSurface = surface
                val onTop = dock.isSurfaceOnTop(activeSurface, PointerInput.primaryPointer.pos)
                if (!onTop) return
                keyEvents.forEach { onKeyInput(it) }
            }
        }
    }


    private fun UiScope.panelContent() {
        Column(Grow.Std, Grow.Std) {
            val overlay = remember { ItemPopupMenu<Dockable>("Title-File-Overlay") }
            overlay()

            FileTitleBar(
                icon,
                dockable,
                onCloseAction = { close() },
                headerLeft = { drawHeaderLeft(it) },
                headerRight = { drawHeaderRight(it) },
                onRightClick = { dockable, event ->
                    val menu = SubMenuItem("File-Context-Menu") { createMenu() }
                    overlay.hide()
                    overlay.show(Vec2f(event.screenPosition), menu, dockable)
                }
            )
            this@DockPanel()
        }
    }

    override fun open() {
        if (dockable.dockedTo.value != null) {
            surface.lastInputTime = Time.gameTime
            return
        }
        InputStack.pushBottom(inputListener)
        dock.addDockableSurface(dockable, surface)
        surface.lastInputTime = Time.gameTime
    }

    private fun createSurface(): UiSurface {
        dockable.floatingX.set(Dp(5f))
        dockable.floatingY.set(Dp.fromPx(ScriptingEnvironmentOverlay.titleBarHeight) + Dp(5f))
        dockable.floatingWidth.set(Dimensions.PaddingExtraLarge * 15f)
        dockable.floatingHeight.set(Dimensions.PaddingExtraLarge * 10f)

        val surface = WindowSurface(
            dock.dockingSurface.parentScene,
            dockable,
            dock.dockingSurface.colors,
            dock.dockingSurface.sizes
        ) {
            modifier.border(null).backgroundColor(ColorTheme.UI.BackgroundGeneral)
            panelContent()
        }
        return surface
    }

    override fun close() {
        dock.removeDockableSurface(surface)
        InputStack.remove(inputListener)
    }

    protected open fun UiScope.drawHeaderLeft(color: Color) {}
    protected open fun UiScope.drawHeaderRight(color: Color) {}
    protected open fun onKeyInput(event: KeyEvent) {}

    open fun SubMenuItem<Dockable>.createMenu() {}
}
