package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.*

interface StartBlock
interface EndBlock
interface ContainerBlock
interface ExpressionBlock {
    val expressionType: ExpressionType
}

@Serializable
abstract class CodeBlock {
    var uuid: @Serializable(ForStringUUID::class) UUID = UUID.randomUUID()

    @Transient
    var color: Color = Color.RED

    @Transient
    var next: CodeBlock? = null

    @Transient
    var parent: CodeBlock? = null

    @Transient
    val inputs = mutableMapOf<String, CodeBlock>()

    @Transient
    val inputTypes = mutableMapOf<String, ExpressionType>()

    @Transient
    var parentBlock: CodeBlock? = null // Используется для expression

    @Transient
    var parentInputName: String? = null

    @Transient
    val positionX = mutableStateOf(50f)

    @Transient
    val positionY = mutableStateOf(50f)

    fun setPosition(x: Float, y: Float) {
        positionX.value = x
        positionY.value = y
    }

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

fun CodeBlock.deepCopy(provider: BlockProvider): CodeBlock {

    val format = CodeBlockFormat(provider)

    val jsonString = format.json.encodeToString(PolymorphicSerializer(CodeBlock::class), this)
    val clone = format.json.decodeFromString(PolymorphicSerializer(CodeBlock::class), jsonString)

    clone.uuid = UUID.randomUUID()
    clone.color = color
    clone.parent = null
    clone.parentBlock = null
    clone.parentInputName = null

    this.inputs.forEach { (slotName, inputBlock) ->
        val inputClone = inputBlock.deepCopy(provider)
        clone.attachInput(slotName, inputClone)
    }

    this.next?.let { nextBlock ->
        val nextClone = nextBlock.deepCopy(provider)
        clone.next = nextClone
        nextClone.parent = clone
    }

    return clone
}

val CodeBlock.isExpression: Boolean
    get() = this is ExpressionBlock

