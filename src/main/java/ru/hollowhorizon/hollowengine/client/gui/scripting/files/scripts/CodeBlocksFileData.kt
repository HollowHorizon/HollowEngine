package ru.hollowhorizon.hollowengine.client.gui.scripting.files.scripts

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.common.codeblocks.IfBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.StringValueBlock

class CodeBlocksFileData(filePath: String, bytes: ByteArray) : FileData(filePath.substringAfterLast('/'), filePath) {
    val editor = BlockEditor()

    override fun save() {

    }

    override fun UiScope.compose() {
        Box(Grow.Std, Grow.Std) {
            val node = uiNode
            val popup = AutoPopup {
                Button("Добавить If") {
                    modifier.onClick {
                        editor.rootBlocks.add(IfBlock().apply { setPosition(node.toLocal(it.screenPosition)) })
                    }
                }
                Button("Добавить Print") {
                    modifier.onClick {
                        editor.rootBlocks.add(PrintBlock().apply { setPosition(node.toLocal(it.screenPosition)) })
                    }
                }
                Button("Добавить Text") {
                    modifier.onClick {
                        editor.rootBlocks.add(StringValueBlock("").apply { setPosition(node.toLocal(it.screenPosition)) })
                    }
                }
            }
            with(editor) {
                EditorLayout {
                    modifier.onClick {
                        if(it.isRightClick) popup.show(Vec2f(it.screenPosition))
                    }

                }

            }
            popup()
        }
    }
}