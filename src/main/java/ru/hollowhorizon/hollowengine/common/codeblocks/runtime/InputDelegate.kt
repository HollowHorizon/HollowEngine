package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class InputDelegate<T : Any>(var name: String?, val type: ExpressionType, val returnType: Class<T>) :
    ReadOnlyProperty<CodeBlock, InputValue<T>> {
    lateinit var thisRef: CodeBlock
    private val interpreter: Lazy<CodeBlockInterpreter<T>> = lazy {
        val block = thisRef.inputs[name] ?: error("Input $name not attached!")
        CachedCodeBlockInterpreter(block, returnType)
    }

    operator fun provideDelegate(thisRef: CodeBlock, property: KProperty<*>): InputDelegate<T> {
        name = name ?: property.name
        thisRef.inputDelegates[name!!] = this
        this.thisRef = thisRef
        return this
    }

    override fun getValue(
        thisRef: CodeBlock,
        property: KProperty<*>,
    ): InputValue<T> {
        return InterpreterValue(name!!, type, interpreter)
    }

    fun serialize(tag: CompoundTag) {
        interpreter.value.serialize(tag)
    }

    fun deserialize(tag: CompoundTag) {
        interpreter.value.deserialize(tag)
    }
}

class InputListDelegate<T : Any>(var name: String?, val type: ExpressionType) {
    operator fun provideDelegate(thisRef: CodeBlock, property: KProperty<*>): InputListDelegate<T> {
        name = name ?: property.name
        return this
    }

    operator fun getValue(thisRef: CodeBlock, property: KProperty<*>): InputValue<List<T>> {
        val sortedKeys = thisRef.inputs.keys
            .filter { it.startsWith("${name}_") }
            .sortedBy { it.substringAfterLast("_").toIntOrNull() ?: Int.MAX_VALUE }
            .map {
                val delegate = thisRef.inputDelegates[it] ?: error("Input $it not attached!")
                delegate.getValue(thisRef, property) as InputValue<T>
            }

        return ListValue(name ?: error("Name for list not defined!"), type, sortedKeys)
    }
}