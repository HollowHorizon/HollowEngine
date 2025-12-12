package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputListDelegate
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
    internal val inputDelegates = mutableMapOf<String, InputDelegate<*>>()

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

    inline fun <reified T : Any> input(name: String? = null) = InputDelegate(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>(),
        T::class.java
    )
    inline fun <reified T : Any> inputList(name: String? = null) = InputListDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>()
    )


    abstract suspend fun BlockContext.execute(): Any?

    abstract fun BlockEditor.InputSlotScope.composeContent()

    open fun BlockEditor.InputSlotScope.composeBody() {}

    open fun serialize(tag: CompoundTag) {
        inputDelegates.forEach { (name, value) ->
            tag.put(name, CompoundTag().apply {
                value.serialize(this)
            })
        }
    }

    open fun deserialize(tag: CompoundTag) {
        tag.allKeys.forEach {
            val delegate = inputDelegates[it] ?: return@forEach
            delegate.deserialize(tag.getCompound(it))
        }
    }
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

