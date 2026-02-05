package ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.IDEFile

abstract class ModelEditorFile(path: String) : IDEFile(path) {
    val modelController = ModelController()

    override fun UiScope.compose() {
        Row(Grow.Std, Grow.Std) {
            Box(Grow(0.66f), Grow.Std) {
                modelController()
            }
            
            if(hasSidebar) {
                Column(Grow(0.33f), Grow.Std) {
                    modifier.backgroundColor(ColorTheme.UI.BackgroundSecondary)
                    composeSidebar()
                }
            }
        }
    }

    open val hasSidebar: Boolean = true
    open fun UiScope.composeSidebar() {}
}
