package ru.hollowhorizon.hollowengine.common.codeblocks.model

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlocksScope
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.InputListDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.*

@Serializable
abstract class BlockModel {
    var uuid: @Serializable(ForStringUUID::class) UUID = UUID.randomUUID()


    @Transient
    var parentBlock: BlockModel? = null

    @Transient
    var parentInputName: String? = null

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

    @Transient
    val isCollapsed = mutableStateOf(false)

    fun attachInput(slotName: String, block: BlockModel) {
        inputs[slotName] = block
        block.parentInputName = slotName
        block.parentBlock = this
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

    open fun InputSlotScope.composeContentCollapsed() {
        DefaultText(this@BlockModel.toString())
    }

    @Transient
    private var _explicitScope: BlocksScope? = null

    val scope: BlocksScope?
        get() = _explicitScope ?: (this as? StatementBlock)?.parent?.scope ?: parentBlock?.scope

    fun setExplicitScope(scope: BlocksScope?) {
        _explicitScope = scope
    }

    override fun toString(): String {
        return this::class.simpleName!!.mapIndexed { i, it -> if (it.isUpperCase() && i > 0) " " + it.lowercase() else it }.joinToString("")
    }
}