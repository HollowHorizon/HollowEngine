package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor

class BlockContext {
    // Тут потом будет объекты, вроде нпс, переменных и другие вещи
}

abstract class CodeBlock(val color: Color) {
    var next: CodeBlock? = null

    // Используем State для реактивности координат
    val positionX = mutableStateOf(50f)
    val positionY = mutableStateOf(50f)

    fun setPosition(x: Float, y: Float) {
        positionX.set(x)
        positionY.set(y)
    }

    abstract suspend fun execute(context: BlockContext)
    abstract fun UiScope.composeContent()
}

class PrintBlock(val message: String) : CodeBlock(MdColor.DEEP_PURPLE) {
    override suspend fun execute(context: BlockContext) {
        println("Block says: $message")
        next?.execute(context)
    }

    override fun UiScope.composeContent() {
        Text("Print: $message") {
            modifier.textColor = Color.WHITE
        }
    }
}