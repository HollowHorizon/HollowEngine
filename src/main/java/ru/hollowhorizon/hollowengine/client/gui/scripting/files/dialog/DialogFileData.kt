package ru.hollowhorizon.hollowengine.client.gui.scripting.files.dialog

import de.fabmax.kool.modules.ui2.UiScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData

class DialogFileData(fileName: String, filePath: String): FileData(fileName, filePath) {
    val components = (0..10).map { if (it % 2 == 0) TextComponent() else WaitComponent() } + NewComponent

    override fun save() {

    }

    override fun UiScope.compose() {
        DialogEditor(components)
    }
}