package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.UiDockable
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentScreen
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.Layout
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme

abstract class FileData(
    val project: IdeContent,
    val fileName: String,
    val filePath: String,
) : Layout, Composable {
    final override val dockable =
        UiDockable(filePath, (Minecraft.getInstance().screen as ScriptingEnvironmentScreen).dock)
    override val name = "file\$$fileName"
    override val icon = IconHelper.forPath(filePath)
    val surface: UiSurface = WindowSurface(dockable, IdeTheme.colors, IdeTheme.sizes) {
        modifier.border(null)
        modifier.backgroundColor(colors.backgroundVariant)

        Column(Grow.Std, Grow.Std) {
            modifier.margin(sizes.smallGap)

            FileTitleBar(dockable, onCloseAction = {
                val file = IdeContent.files.values.find { it.dockable == dockable } ?: return@FileTitleBar
                (Minecraft.getInstance().screen as ScriptingEnvironmentScreen).dock
                    .removeDockableSurface(file.surface)
                IdeContent.files.values.remove(file)
            })
            this@FileData()
        }
    }

    abstract fun save()
    open fun close() {}
}