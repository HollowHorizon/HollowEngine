package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor

class BlockContext {
    // Контекст выполнения
}

abstract class CodeBlock(val color: Color, val isExpression: Boolean = false) {
    // Вертикальная связь (Statements)
    var next: CodeBlock? = null
    var parent: CodeBlock? = null

    // Горизонтальная связь / Вложенность (Inputs/Expressions)
    // Ключ - имя слота, Значение - блок
    val inputs = mutableMapOf<String, CodeBlock>()

    // Если этот блок вставлен в input другого блока, запоминаем, в какой именно
    var parentBlock: CodeBlock? = null
    var parentInputName: String? = null

    // Координаты (имеют смысл только если блок корневой, т.е. лежит на канвасе)
    val positionX = mutableStateOf(50f)
    val positionY = mutableStateOf(50f)

    fun setPosition(x: Float, y: Float) {
        positionX.value = x
        positionY.value = y
    }

    // Помощник для присоединения инпута
    fun attachInput(slotName: String, block: CodeBlock) {
        inputs[slotName] = block
        block.parentBlock = this
        block.parentInputName = slotName
        block.parent = null // У инпута нет вертикального родителя
    }

    abstract suspend fun execute(context: BlockContext): Any? // Теперь может возвращать значение
    abstract fun BlockEditor.InputSlotScope.composeContent()
}

// Пример блока-инструкции (имеет зубчики)
class PrintBlock(var defaultMessage: String = "") : CodeBlock(MdColor.DEEP_PURPLE, isExpression = false) {
    override suspend fun execute(context: BlockContext): Any? {
        // Если в слот "msg" что-то вставлено, вычисляем это. Иначе берем дефолт.
        val messageToPrint = inputs["msg"]?.execute(context) ?: defaultMessage
        println("Block says: $messageToPrint")

        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        // Текст "Print"
        Text("Print") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center)
        }

        InputSlot("msg")
    }
}

// Пример блока-выражения (число/математика), без зубчиков
class StringValueBlock(var value: String) : CodeBlock(MdColor.AMBER, isExpression = true) {
    override suspend fun execute(context: BlockContext): Any {
        return value
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(value) {
            modifier
                .onChange { value = it }
                .colors(
                    lineColor = Color.WHITE,
                    cursorColor = Color.WHITE,
                    textColor = Color.WHITE
                )
        }
    }
}