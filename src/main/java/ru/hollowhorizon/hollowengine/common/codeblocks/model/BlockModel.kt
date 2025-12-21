package ru.hollowhorizon.hollowengine.common.codeblocks.model

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.isExpression
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputListDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.*

@Serializable
abstract class BlockModel {
    var uuid: @Serializable(ForStringUUID::class) UUID = UUID.randomUUID()

    @Transient
    var color: Color = Color.Companion.RED

    @Transient
    val inputs = mutableMapOf<String, BlockModel>()

    @Transient
    val inputTypes = mutableMapOf<String, ExpressionType>()

    @Transient
    val positionX = mutableStateOf(50f)

    @Transient
    val positionY = mutableStateOf(50f)

    fun attachInput(slotName: String, block: BlockModel) {
        inputs[slotName] = block
        if (block.isExpression()) {
            block.parentBlock = this
            block.parentInputName = slotName
        }
    }

    inline fun <reified T : Any> input(name: String? = null) = InputDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>()
    )

    inline fun <reified T : Any> inputList(name: String? = null) = InputListDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>()
    )


    abstract suspend fun execute(): Any?

    abstract fun InputSlotScope.composeContent()
}