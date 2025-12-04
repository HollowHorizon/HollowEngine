package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor

interface StartBlock
interface EndBlock
interface ContainerBlock

abstract class CodeBlock(val color: Color, val isExpression: Boolean = false) {
    var next: CodeBlock? = null
    var parent: CodeBlock? = null

    val inputs = mutableMapOf<String, CodeBlock>()

    var parentBlock: CodeBlock? = null
    var parentInputName: String? = null

    val positionX = mutableStateOf(50f)
    val positionY = mutableStateOf(50f)

    fun setPosition(x: Float, y: Float) {
        positionX.value = x
        positionY.value = y
    }
    fun setPosition(pos: Vec2f) = setPosition(pos.x, pos.y)

    fun attachInput(slotName: String, block: CodeBlock) {
        inputs[slotName] = block
        block.parentBlock = this
        block.parentInputName = slotName
        block.parent = null
    }

    abstract suspend fun execute(context: BlockContext): Any?

    abstract fun BlockEditor.InputSlotScope.composeContent()

    open fun UiScope.composeHeaderLayout(scopeBuilder: (UiScope) -> BlockEditor.InputSlotScope, blockHeaderModifier: UiModifier.() -> Unit) {
        Row {
            modifier.apply(blockHeaderModifier)
            // Стандартные паддинги
            modifier.padding(horizontal = 10.dp, vertical = 6.dp).alignY(AlignmentY.Center)
            scopeBuilder(this).composeContent()
        }
    }

    open fun BlockEditor.InputSlotScope.composeBody() {}
}

class PrintBlock(var defaultMessage: String = "") : CodeBlock(MdColor.DEEP_PURPLE, isExpression = false) {
    override suspend fun execute(context: BlockContext): Any? {
        val msg = inputs["msg"]?.execute(context) ?: defaultMessage
        HollowEngine.LOGGER.info(msg)
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Print") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("msg")
    }
}

class StringValueBlock(var value: String) : CodeBlock(MdColor.AMBER, isExpression = true) {
    override suspend fun execute(context: BlockContext) = value
    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(value) {
            modifier.onChange { value = it }
                .hint("Значение")
                .colors(lineColor = Color.WHITE, textColor = Color.WHITE)
        }
    }
}

class RepeatBlock : CodeBlock(MdColor.ORANGE, isExpression = false), ContainerBlock {
    override suspend fun execute(context: BlockContext): Any? {
        val times = inputs["times"]?.execute(context).toString().toIntOrNull() ?: 1
        repeat(times) {
            inputs["body"]?.execute(context)
        }
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Repeat") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("times")
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("body")
    }
}

class IfBlock : CodeBlock(MdColor.TEAL, isExpression = false), ContainerBlock {
    override suspend fun execute(context: BlockContext): Any? {
        val condition = inputs["cond"]?.execute(context) as? Boolean ?: false
        if (condition) {
            inputs["then"]?.execute(context)
        } else {
            inputs["else"]?.execute(context)
        }
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("If") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center) }
        InputSlot("cond")
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("then")

        SectionSeparator("Else")

        BodySlot("else")
    }
}