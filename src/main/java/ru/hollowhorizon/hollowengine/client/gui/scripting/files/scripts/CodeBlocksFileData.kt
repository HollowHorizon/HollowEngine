package ru.hollowhorizon.hollowengine.client.gui.scripting.files.scripts

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.StandardModules
import ru.hollowhorizon.hollowengine.common.codeblocks.runScript

class CodeBlocksFileData(filePath: String, bytes: ByteArray) : FileData(filePath.substringAfterLast('/'), filePath) {
    val repository = BlockRepository.create("Скрипт") {
        include(StandardModules.AllBasics)
    }
    val editor = BlockEditor(repository)

    override fun save() {

    }

    override fun UiScope.compose() {
        Box(Grow.Std, Grow.Std) {
            with(editor) {
                EditorLayout {}
            }

            Button("Запустить") {
                modifier.onClick {
                    runScript(editor.rootBlocks)
                }
            }
        }
    }
}