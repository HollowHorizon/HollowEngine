package ru.hollowhorizon.hollowengine.common.utils.serialization

import com.google.common.reflect.TypeToken
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

interface Format<T> {
    val serializersModule: SerializersModule

    fun <V> serialize(serializer: SerializationStrategy<V>, value: V): T
    fun <V> deserialize(deserializer: DeserializationStrategy<V>, data: T): V
}

inline fun <reified T, V> Format<V>.serialize(value: T): V {
    return this.serialize(serializersModule.serializer(), value)
}

fun <T : Any, V> Format<V>.serializeNoInline(value: T, cl: Class<T>): V {
    val typeToken = TypeToken.of(cl)
    return serialize(serializersModule.serializer(typeToken.type), value)
}

inline fun <reified T, V> Format<V>.deserialize(tag: V): T {
    return deserialize(serializersModule.serializer(), tag)
}

@Suppress("UNCHECKED_CAST")
fun <T : Any, V> Format<V>.deserializeNoInline(tag: V, cl: Class<out T>): T {
    val typeToken = TypeToken.of(cl)
    return deserialize(serializersModule.serializer(typeToken.type), tag) as T
}