package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import java.util.*

interface StartBlock
interface EndBlock
interface ContainerBlock
interface ExpressionBlock {
    val expressionType: ExpressionType
}

abstract class CodeBlock(val color: Color) {
    val uuid: UUID = UUID.randomUUID()

    var next: CodeBlock? = null
    var parent: CodeBlock? = null

    val inputs = mutableMapOf<String, CodeBlock>()
    val inputTypes = mutableMapOf<String, ExpressionType>()

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

    context(editor: BlockEditor)
    open fun UiScope.composeHeaderLayout(
        isHovered: Boolean,
        isGhost: Boolean,
        blockHeaderModifier: UiModifier.() -> Unit,
    ) {
        Row(Grow.Std) {
            modifier.apply(blockHeaderModifier)
            modifier.padding(horizontal = 10.dp, vertical = 6.dp).alignY(AlignmentY.Center)

            editor.InputSlotScope(this, this@CodeBlock, isHovered, isGhost).composeContent()
        }
    }

    open fun BlockEditor.InputSlotScope.composeBody() {}
}

val CodeBlock.isExpression: Boolean
    get() = this is ExpressionBlock

