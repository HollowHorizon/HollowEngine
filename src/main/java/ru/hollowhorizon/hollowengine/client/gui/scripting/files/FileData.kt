package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentScreen
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverListener

abstract class FileData(
    val fileName: String,
    val filePath: String,
) : Layout, Composable {
    final override val dockable =
        UiDockable(filePath, (Minecraft.getInstance().screen as ScriptingEnvironmentScreen).dock)
    override val name = "file\$$fileName"
    override val icon = IconHelper.forPath(filePath)
    val isDocked get() = dockable.dockedTo.value != null

    val surface: UiSurface = WindowSurface(dockable, IdeTheme.colors, IdeTheme.sizes) {
        val (isHovered, anim) = hoverListener { !surface.isFocused.use() }

        var factor = Easing.quadRev(anim.progressAndUse())
        if (!isHovered.use() && !surface.isFocused.use()) factor = 1f - factor

        val borderColor = Color("3C3C4AFF").mix(Color("586D84FF"), factor)
        if(!isDocked) {
            modifier.border(RoundRectBorder(borderColor, sizes.smallGap, sizes.borderWidth))
            modifier.background(RoundRectBackground(colors.backgroundVariant, sizes.smallGap))
        } else {
            modifier.backgroundColor(colors.backgroundVariant)
        }
        Column(Grow.Std, Grow.Std) {
            if(isDocked) modifier.margin(sizes.smallGap)


            val overlay = remember { ItemPopupMenu<Dockable>("Title-File-Overlay") }
            overlay()
            FileTitleBar(dockable, onCloseAction = { dockable ->
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
            this@FileData()
        }
    }

    private fun closeFile(dockable: Dockable) {
        val file = IdeContent.files.values.find { it.dockable == dockable } ?: return
        (Minecraft.getInstance().screen as ScriptingEnvironmentScreen).dock
            .removeDockableSurface(file.surface)
        IdeContent.files.values.remove(file)
        close()
    }

    open fun SubMenuItem<Dockable>.createMenu() {
        // Можно расширять в наследниках
    }

    abstract fun save()
    open fun close() {}
}