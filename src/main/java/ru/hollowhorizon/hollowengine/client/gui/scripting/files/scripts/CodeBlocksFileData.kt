package ru.hollowhorizon.hollowengine.client.gui.scripting.files.scripts

import de.fabmax.kool.modules.ui2.Box
import de.fabmax.kool.modules.ui2.Grow
import de.fabmax.kool.modules.ui2.UiScope
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.StandardModules
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath

class CodeBlocksFileData(filePath: String, bytes: ByteArray) : FileData(filePath.substringAfterLast('/'), filePath) {
    val repository = BlockRepository.create("Скрипт") {
        include(StandardModules.AllBasics)
    }
    val editor = BlockEditor(repository) // Возможно стоит положить ссылку на save() прямо туда

    override fun save() {
        val file = filePath.fromReadablePath()
    }

    override fun UiScope.compose() {
        Box(Grow.Std, Grow.Std) {
            with(editor) {
                EditorLayout {}
            }
        }
    }
}