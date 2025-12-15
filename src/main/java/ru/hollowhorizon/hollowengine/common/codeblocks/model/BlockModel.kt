package ru.hollowhorizon.hollowengine.common.codeblocks.model

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputListDelegate
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.*

@Serializable
abstract class BlockModel {
    var uuid: @Serializable(ForStringUUID::class) UUID = UUID.randomUUID()

    @Transient
    var color: Color = Color.Companion.RED

    @Transient
    internal val inputDelegates = mutableMapOf<String, InputDelegate<*>>()

    @Transient
    val inputs = mutableMapOf<String, BlockModel>()

    @Transient
    val inputTypes = mutableMapOf<String, ExpressionType>()

    @Transient
    val positionX = mutableStateOf(50f)

    @Transient
    val positionY = mutableStateOf(50f)

    fun setPosition(x: Float, y: Float) {
        positionX.value = x
        positionY.value = y
    }

    fun attachInput(slotName: String, block: BlockModel) {
        inputs[slotName] = block
        if (block.isExpression()) {
            block.parentBlock = this
            block.parentInputName = slotName
        }
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