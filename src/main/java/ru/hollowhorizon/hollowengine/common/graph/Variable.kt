package ru.hollowhorizon.hollowengine.common.graph

import io.ktor.websocket.Serializer
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

interface Variable<T: Any>: ReadWriteProperty<Any?, T> {

    fun serialize(): Tag?
    suspend fun deserialize(tag: Tag?): T

    fun name(): String
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Variable<T>
}

class GraphVariable<T : Any>(
    private var name: String? = null,
    private val generator: suspend () -> T,
    private val type: Class<T>,
): Variable<T> {
    private var value: T? = null

    override suspend fun deserialize(tag: Tag?): T {
        val value = tag?.let { NBTFormat.deserializeNoInline(it, type) } ?: generator()
        this.value = value
        return value
    }

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Variable<T> {
        if (name == null) name = property.name
        return this
    }

    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return value ?: error("Variable $name not initialized!")
    }

    override operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    override fun serialize(): Tag? = value?.let { NBTFormat.serializeNoInline(it, type) }


    override fun name() = name ?: error("Variable name not found!")
}

class DelegatedVariable<T : Any, V: Any>(val original: Variable<T>, val mappedDeserializer: suspend (T) -> V, val mappedSerializer: (V) -> T): Variable<V> {
    private var value: V? = null

    override suspend fun deserialize(tag: Tag?): V {
        val base = original.deserialize(tag)
        return mappedDeserializer(base).also { value = it }
    }

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Variable<V> {
        original.provideDelegate(thisRef, property)
        return this
    }

    // Если значение не было создано, то его не нужно сериализовать
    override fun serialize(): Tag? = value?.let { original.serialize() }

    override fun name(): String = original.name()

    override fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return value ?: error("Variable ${original.name()} not initialized!")
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        this.value = value
        original.setValue(thisRef, property, mappedSerializer(value))
    }

}

fun <T: Any, V: Any> Variable<T>.map(mappedDeserializer: suspend (T) -> V, mappedSerializer: (V) -> T): Variable<V> =
    DelegatedVariable(this, mappedDeserializer, mappedSerializer)