package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.releaseDelayed
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable

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

    private fun closeFile(dockable: Dockable) {
        val file = IdeContent.files.values.find { it.dockable == dockable } ?: return
        file.close()
    }

    open fun SubMenuItem<Dockable>.createMenu() {
        // Можно расширять в наследниках
    }

    abstract fun save()

    override fun open() {
        if(surface != null) return
        surface = WindowSurface(ScriptingEnvironmentOverlay.scene, dockable, IdeTheme.colors, IdeTheme.sizes) {

            val isHovered by modifier.hoverable()
            val factor by animateFloatAsState(if (isHovered) 1f else 0f, tween(easing = Easing.quadRev))

            val borderColor = Color("3C3C4AFF").mix(Color("586D84FF"), factor)
            if(!isDocked) {
                modifier.border(RoundRectBorder(borderColor, sizes.smallGap, sizes.borderWidth))
                modifier.background(RoundRectBackground(colors.backgroundVariant, sizes.smallGap))
            } else {
                modifier.backgroundColor(colors.backgroundVariant)
            }
            Column(Grow.Std, Grow.Std) {
                val overlay = remember { ItemPopupMenu<Dockable>("Title-File-Overlay") }
                overlay()
                FileTitleBar(icon, dockable, isCollapsed, onCloseAction = { dockable ->
                    closeFile(dockable)
                }, onRightClick = { dockable, event ->
                    val menu = SubMenuItem("File-Context-Menu") {
                        createMenu()
                        divider()

                        item("Сохранить", "hollowengine:textures/gui/icons/icon_45.png") {
                            save()
                        }
                        item("Закрыть", "hollowengine:textures/gui/icons/close.png") {
                            closeFile(dockable)
                        }
                    }
                    overlay.hide()
                    overlay.show(Vec2f(event.screenPosition), menu, dockable)
                })
                if(!isCollapsed.use()) this@FileData()
            }
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