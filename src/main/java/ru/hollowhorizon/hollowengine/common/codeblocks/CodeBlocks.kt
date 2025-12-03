package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor

class BlockContext {
    // Тут потом будет объекты, вроде нпс, переменных и другие вещи
}

abstract class CodeBlock(val color: Color) {
    var next: CodeBlock? = null

    val position = MutableVec2f(50f, 50f)

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