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
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.InputDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.InputListDelegate
import ru.hollowhorizon.hollowengine.common.codeblocks.execution.OutputDelegate
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
    var parentOutputName: String? = null

    abstract val color: Color

    @Transient
    val inputs = mutableMapOf<String, BlockModel>()

    @Transient
    private val inputDefaults = mutableMapOf<String, () -> BlockModel>()

    @Transient
    private val outputDefaults = mutableMapOf<String, () -> BlockModel>()

    @Transient
    val inputTypes = mutableMapOf<String, ExpressionType>()

    @Transient
    val outputs = mutableMapOf<String, BlockModel>()

    @Transient
    val outputTypes = mutableMapOf<String, ExpressionType>()

    @Transient
    val positionX = mutableStateOf(50f)

    @Transient
    val positionY = mutableStateOf(50f)

    @Transient
    val isCollapsed = mutableStateOf(false)

    @Transient
    var displayName: String? = null

    fun attachInput(slotName: String, block: BlockModel) {
        inputs[slotName] = block
        block.parentInputName = slotName
        block.parentOutputName = null
        block.parentBlock = this
    }

    fun attachOutput(slotName: String, block: BlockModel) {
        outputs[slotName] = block
        block.parentOutputName = slotName
        block.parentInputName = null
        block.parentBlock = this
    }

    inline fun <reified T : Any> input(name: String? = null) = InputDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>()
    )

    inline fun <reified T : Any> inputDefault(
        name: String? = null,
        noinline default: () -> BlockModel,
    ) = InputDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>(),
        default
    )

    fun setInputDefault(slotName: String, default: () -> BlockModel) {
        inputDefaults[slotName] = default
    }

    fun setOutputDefault(slotName: String, default: () -> BlockModel) {
        outputDefaults[slotName] = default
    }

    fun applyDefaults(recursive: Boolean = true) {
        inputDefaults.forEach { (slotName, factory) ->
            if (inputs[slotName] == null) {
                val block = factory()
                attachInput(slotName, block)
            }
        }
        outputDefaults.forEach { (slotName, factory) ->
            if (outputs[slotName] == null) {
                val block = factory()
                attachOutput(slotName, block)
            }
        }

        if (recursive) {
            inputs.values.forEach { it.applyDefaults(true) }
            outputs.values.forEach { it.applyDefaults(true) }
            (this as? StatementBlock)?.next?.applyDefaults(true)
        }
    }

    inline fun <reified T : Any> inputList(name: String? = null) = InputListDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>()
    )

    inline fun <reified T : Any> output(name: String? = null) = OutputDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>(),
        defaultFactory = null
    )

    inline fun <reified T : Any> outputDefault(
        name: String? = null,
        noinline default: () -> BlockModel,
    ) = OutputDelegate<T>(
        name,
        if (T::class == Any::class) AnyType else typeOf<T>(),
        defaultFactory = default
    )

    abstract suspend fun execute(): Any?

    abstract fun InputSlotScope.composeContent()

    open fun InputSlotScope.composeContentCollapsed() {
        DefaultText(this@BlockModel.displayName ?: this@BlockModel.toString())
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
