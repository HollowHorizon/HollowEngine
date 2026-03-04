package ru.hollowhorizon.hollowengine.common.codeblocks.execution

import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

interface OutputConsumer {
    val acceptedType: ExpressionType
    suspend fun accept(value: Any?)
}

interface OutputValue<T> {
    val name: String
    val type: ExpressionType
    val default: T?
    suspend fun emit(value: T?)
}

class OutputDelegate<T : Any>(
    var name: String?,
    val type: ExpressionType,
    val default: T?,
) : ReadOnlyProperty<BlockModel, OutputValue<T>> {

    lateinit var thisRef: BlockModel

    operator fun provideDelegate(thisRef: BlockModel, property: KProperty<*>): OutputDelegate<T> {
        name = name ?: property.name
        this.thisRef = thisRef
        return this
    }

    override fun getValue(thisRef: BlockModel, property: KProperty<*>): OutputValue<T> {
        return object : OutputValue<T> {
            override val name: String get() = this@OutputDelegate.name!!
            override val type: ExpressionType get() = this@OutputDelegate.type
            override val default: T? get() = this@OutputDelegate.default

            override suspend fun emit(value: T?) {
                val attached = thisRef.outputs[name] as? OutputConsumer ?: return
                attached.accept(value ?: default)
            }
        }
    }
}

