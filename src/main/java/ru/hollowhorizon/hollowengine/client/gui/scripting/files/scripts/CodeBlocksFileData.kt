package ru.hollowhorizon.hollowengine.client.gui.scripting.files.scripts

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.common.codeblocks.*

class CodeBlocksFileData(filePath: String, bytes: ByteArray) : FileData(filePath.substringAfterLast('/'), filePath) {
    val editor = BlockEditor()

    val blocks: List<Pair<String, () -> CodeBlock>> = buildList {
        add("Вывод" to { PrintBlock() })
        add("Строка" to { StringValueBlock("") })
        add("Пока" to { WhileBlock() })
        add("Пока" to { WhileBlock() })
        add("Ждать" to { DelayBlock() })
        add("Отправить сообщение" to { SendEventBlock("") })
        add("Операция" to { MathBlock() })
        add("Сравнение" to { LogicBlock() })
        add("Число" to { NumberBlock() })
        add("Логический тип" to { BoolBlock() })
        add("Присвоить" to { SetVarBlock("") })
        add("Получить" to { GetVarBlock("") })
    }

    override fun save() {

    }

    override fun UiScope.compose() {
        Box(Grow.Std, Grow.Std) {
            val node = uiNode
            val popup = AutoPopup {
                blocks.forEach { (name, builder) ->
                    Button(name) {
                        modifier.onClick {
                            editor.rootBlocks.add(builder().apply { setPosition(node.toLocal(it.screenPosition)) })
                        }
                    }
                }
            }
            with(editor) {
                EditorLayout {
                    modifier.onClick {
                        if (it.isRightClick) popup.show(Vec2f(it.screenPosition))
                    }

                }

            }
            popup()

            Button("Запустить") {
                modifier.onClick {
                    runScript(editor.rootBlocks)
                }
            }
        }
    }
}