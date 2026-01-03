package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.releaseDelayed
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons

abstract class FileData(
    val fileName: String,
    val filePath: String,
) : Layout, Composable {
    final override val dockable = UiDockable(filePath, ScriptingEnvironmentOverlay.dock)
    override val name = "file\$$fileName"
    override val icon = IconHelper.forPath(filePath)

    val isCollapsed = mutableStateOf(false)
    val isDocked get() = dockable.dockedTo.value != null

    protected var surface: UiSurface? = null

    protected fun closeFile(dockable: Dockable) {
        val file = IdeContent.files.values.find { it.dockable == dockable } ?: return
        file.close()
    }

    protected open fun UiScope.setupContent() {
        Column(Grow.Std, Grow.Std) {
            val overlay = remember { ItemPopupMenu<Dockable>("Title-File-Overlay") }
            overlay()
            FileTitleBar(icon, dockable, isCollapsed, onCloseAction = { dockable ->
                closeFile(dockable)
            }, onRightClick = { dockable, event ->
                val menu = SubMenuItem("File-Context-Menu") { createMenu() }
                overlay.hide()
                overlay.show(Vec2f(event.screenPosition), menu, dockable)
            })
            if (!isCollapsed.use()) this@FileData()
        }
    }

    open fun SubMenuItem<Dockable>.createMenu() {
        item("Сохранить", icons.ICON_45) {
            save()
        }
        item("Закрыть", icons.CLOSE) {
            closeFile(dockable)
        }
    }

    abstract fun save()

    override fun open() {
        if (surface != null) return

        dockable.floatingX.set(Dp(5f))
        dockable.floatingY.set(Dp.fromPx(ScriptingEnvironmentOverlay.titleBarHeight) + Dp(5f))
        dockable.floatingWidth.set(Dimensions.PaddingExtraLarge * 15f)
        dockable.floatingHeight.set(Dimensions.PaddingExtraLarge * 10f)
        surface = WindowSurface(ScriptingEnvironmentOverlay.scene, dockable, IdeTheme.colors, IdeTheme.sizes) {

            modifier.backgroundColor(ColorTheme.UI.BackgroundGeneral).border(null)
            setupContent()
        }.also { ScriptingEnvironmentOverlay.dock.addDockableSurface(dockable, it) }
    }

    override fun close() {
        surface?.let {
            IdeContent.files.values.remove(this)
            ScriptingEnvironmentOverlay.dock.removeDockableSurface(it)
            it.releaseDelayed(1)
        }
        surface = null
    }
}