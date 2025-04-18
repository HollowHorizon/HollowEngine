package ru.hollowhorizon.hollowengine.client.gui.scripting

import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import ru.hollowhorizon.hc.common.utils.nbt.NBTFormat
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties

interface SerializableCoroutine<T: Any> {
    fun type(): KSerializer<T>
    fun save(encoder: Encoder)
    fun load(decoder: Decoder)
}

class Serializer<T: Any>(val coroutine: SerializableCoroutine<T>) : KSerializer<SerializableCoroutine<T>> {
    val serializer = MapSerializer(String.serializer(), coroutine.type())
    override val descriptor: SerialDescriptor =
        serializer.descriptor

    override fun serialize(encoder: Encoder, value: SerializableCoroutine<T>) {
        value.save(encoder)
    }

    override fun deserialize(decoder: Decoder): SerializableCoroutine<T> {
        coroutine.load(decoder)
        return coroutine
    }
}

data class Example(
    var data1: String? = null,
    var data2: String? = null
): SerializableCoroutine<String> {
    val serializer = Serializer(this)



    override fun type() = String.serializer()

    override fun save(encoder: Encoder) {
        encoder.encodeSerializableValue(serializer.serializer, mutableMapOf<String, String>().apply {
            if(data1 != null) put("data1", data1!!)
            if(data2 != null) put("data2", data2!!)
        })
    }

    override fun load(decoder: Decoder) {
        val map = decoder.decodeSerializableValue(serializer.serializer)

        map.forEach { (key, value) ->
            when(key) {
                "data1" -> data1 = value
                "data2" -> data2 = value
            }
        }
    }

}

fun main() {
    val example = Example()

    val s1 = NBTFormat.serialize(example.serializer, example).apply(::println)
    example.data1 = "Hello"
    val s2 = NBTFormat.serialize(example.serializer, example).apply(::println)
    example.data1 = null

    println(NBTFormat.deserialize(example.serializer, s1))
    println(NBTFormat.deserialize(example.serializer, s2))
}