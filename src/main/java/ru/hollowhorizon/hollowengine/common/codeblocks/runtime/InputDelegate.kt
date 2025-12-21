package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class InputDelegate<T : Any>(var name: String?, val type: ExpressionType) :
    ReadOnlyProperty<BlockModel, InputValue<T>> {
    lateinit var thisRef: BlockModel
    private val interpreter: Lazy<BlockModelInterpreter<T>> = lazy {
        val block = thisRef.inputs[name] ?: error("Input $name not attached!")
        when(block) {
            is ExpressionBlock -> ExpressionBlockInterpreter(block)
            is StatementBlock -> CodeBlockInterpreter(block)
            else -> error("Unknown block $block at input $name!")
        }
    }

    operator fun provideDelegate(thisRef: BlockModel, property: KProperty<*>): InputDelegate<T> {
        name = name ?: property.name
        this.thisRef = thisRef
        return this
    }

    override fun getValue(
        thisRef: BlockModel,
        property: KProperty<*>,
    ): InputValue<T> {
        return InterpreterValue(name!!, type, interpreter)
    }
}

class InputListDelegate<T : Any>(var name: String?, val type: ExpressionType) {
    operator fun provideDelegate(thisRef: BlockModel, property: KProperty<*>): InputListDelegate<T> {
        name = name ?: property.name
        return this
    }

    operator fun getValue(thisRef: BlockModel, property: KProperty<*>): InputValue<List<T>> {
        val sortedKeys = thisRef.inputs.keys
            .filter { it.startsWith("${name}_") }
            .sortedBy { it.substringAfterLast("_").toIntOrNull() ?: Int.MAX_VALUE }
            .map {
                val delegate = InputDelegate<T>(it, type)
                delegate.provideDelegate(thisRef, property)
                delegate.getValue(thisRef, property)
            }

        return ListValue(name ?: error("Name for list not defined!"), type, sortedKeys)
    }
}