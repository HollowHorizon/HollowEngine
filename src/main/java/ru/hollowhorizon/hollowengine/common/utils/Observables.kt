package ru.hollowhorizon.hollowengine.common.utils

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure

interface SerializerProvider {
    fun serializer(): KSerializer<*>
}

fun main() {
}

open class ObservableCollection<E>(
    val original: MutableCollection<E>, val onChange: () -> Unit, protected val elementSerializer: KSerializer<E>,
) : MutableCollection<E>, SerializerProvider {
    override fun iterator(): MutableIterator<E> = ObservableIterator()

    override fun add(element: E): Boolean {
        return original.add(element).apply {
            onChange()
        }
    }

    override fun remove(element: E): Boolean {
        return original.remove(element).apply {
            onChange()
        }
    }

    override fun addAll(elements: Collection<E>): Boolean {
        return original.addAll(elements).apply {
            onChange()
        }
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        return original.removeAll(elements).apply {
            onChange()
        }
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        return original.retainAll(elements).apply {
            onChange()
        }
    }

    override fun clear() {
        original.clear()
        onChange()
    }

    override val size: Int
        get() = original.size

    override fun isEmpty(): Boolean = original.isEmpty()

    override fun contains(element: E): Boolean = original.contains(element)

    override fun containsAll(elements: Collection<E>) = original.containsAll(elements)

    @OptIn(InternalSerializationApi::class)
    override fun serializer(): KSerializer<*> {
        return ListSerializer(elementSerializer)
    }

    inner class ObservableIterator : MutableIterator<E> {
        val iterator = original.iterator()

        override fun remove() {
            iterator.remove()
            onChange()
        }

        override fun next(): E {
            return iterator.next()
        }

        override fun hasNext(): Boolean = iterator.hasNext()
    }
}

class ObservableList<E>(val list: MutableList<E>, onChange: () -> Unit, elementSerializer: KSerializer<E>) :
    MutableList<E>,
    ObservableCollection<E>(list, onChange, elementSerializer) {


    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        return list.addAll(index, elements).apply {
            onChange()
        }
    }

    override fun set(index: Int, element: E): E {
        return list.set(index, element).apply {
            onChange()
        }
    }

    override fun add(index: Int, element: E) {
        return list.add(index, element).apply {
            onChange()
        }
    }

    override fun removeAt(index: Int): E {
        return list.removeAt(index).apply {
            onChange()
        }
    }

    override fun listIterator(): MutableListIterator<E> {
        return ObservableItr(list.listIterator())
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        return ObservableItr(list.listIterator(index))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        return ObservableList(list.subList(fromIndex, toIndex), onChange, elementSerializer)
    }

    override fun get(index: Int): E = list[index]

    override fun indexOf(element: E): Int = list.indexOf(element)

    override fun lastIndexOf(element: E): Int = list.lastIndexOf(element)

    private open inner class Itr<U>(val internalIterator: MutableIterator<U>) : MutableIterator<U> {
        override fun hasNext() = internalIterator.hasNext()

        override fun next(): U = internalIterator.next()

        override fun remove() {
            internalIterator.remove()
            onChange()
        }
    }

    private inner class ObservableItr(internalIterator: MutableListIterator<E>) : Itr<E>(internalIterator),
        MutableListIterator<E> {
        private fun internalIterator(): MutableListIterator<E> {
            return internalIterator as MutableListIterator<E>
        }

        override fun add(element: E) {
            internalIterator().add(element)
            onChange()
        }

        override fun hasPrevious() = internalIterator().hasPrevious()

        override fun nextIndex() = internalIterator().nextIndex()

        override fun previous() = internalIterator().previous()

        override fun previousIndex() = internalIterator().previousIndex()

        override fun set(element: E) {
            internalIterator().set(element)
            onChange()
        }
    }

    companion object {
        fun <T> serializer(elementSerializer: KSerializer<T>) = ListSerializer(elementSerializer)
    }
}

class ObservableSet<E>(val set: MutableSet<E>, onChange: () -> Unit, elementSerializer: KSerializer<E>) :
    ObservableCollection<E>(set, onChange, elementSerializer),
    MutableSet<E>

private val NULL = Any()

sealed class KeyValueSerializer<K, V, R>(
    protected val keySerializer: KSerializer<K>,
    protected val valueSerializer: KSerializer<V>,
) : KSerializer<R> {

    protected abstract val R.key: K
    protected abstract val R.value: V
    protected abstract fun toResult(key: K, value: V): R

    override fun serialize(encoder: Encoder, value: R) {
        val structuredEncoder = encoder.beginStructure(descriptor)
        structuredEncoder.encodeSerializableElement(descriptor, 0, keySerializer, value.key)
        structuredEncoder.encodeSerializableElement(descriptor, 1, valueSerializer, value.value)
        structuredEncoder.endStructure(descriptor)
    }

    override fun deserialize(decoder: Decoder): R = decoder.decodeStructure(descriptor) {
        if (decodeSequentially()) {
            val key = decodeSerializableElement(descriptor, 0, keySerializer)
            val value = decodeSerializableElement(descriptor, 1, valueSerializer)
            return@decodeStructure toResult(key, value)
        }

        var key: Any? = NULL
        var value: Any? = NULL
        mainLoop@ while (true) {
            when (val idx = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> {
                    break@mainLoop
                }

                0 -> {
                    key = decodeSerializableElement(descriptor, 0, keySerializer)
                }

                1 -> {
                    value = decodeSerializableElement(descriptor, 1, valueSerializer)
                }

                else -> throw SerializationException("Invalid index: $idx")
            }
        }
        if (key === NULL) throw SerializationException("Element 'key' is missing")
        if (value === NULL) throw SerializationException("Element 'value' is missing")
        @Suppress("UNCHECKED_CAST")
        return@decodeStructure toResult(key as K, value as V)
    }
}

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
class MutableMapEntrySerializer<K, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>,
) : KeyValueSerializer<K, V, MutableMap.MutableEntry<K, V>>(keySerializer, valueSerializer) {
    private data class MapEntry<K, V>(override val key: K, override var value: V) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V {
            val old = value
            value = newValue
            return old
        }
    }

    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("kotlin.collections.Map.Entry", StructureKind.MAP) {
            element("key", keySerializer.descriptor)
            element("value", valueSerializer.descriptor)
        }

    override val MutableMap.MutableEntry<K, V>.key: K get() = this.key
    override val MutableMap.MutableEntry<K, V>.value: V get() = this.value
    override fun toResult(key: K, value: V): MutableMap.MutableEntry<K, V> = MapEntry(key, value)
}

open class ObservableMap<K : Any, V : Any>(
    val original: @Serializable MutableMap<K, V>,
    val onChange: () -> Unit,
    protected val keyType: KSerializer<K>,
    protected val valueType: KSerializer<V>,
) : MutableMap<K, V>, SerializerProvider {
    override val keys: MutableSet<K>
        get() = ObservableSet(original.keys, onChange, keyType)
    override val values: MutableCollection<V>
        get() = ObservableCollection(original.values, onChange, valueType)
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = ObservableSet(original.entries, onChange, MutableMapEntrySerializer(keyType, valueType))

    override fun put(key: K, value: V): V? {
        return original.put(key, value).apply {
            onChange()
        }
    }

    override fun remove(key: K): V? {
        return original.remove(key).apply {
            onChange()
        }
    }

    override fun putAll(from: Map<out K, V>) {
        return original.putAll(from).apply {
            onChange()
        }
    }

    override fun clear() {
        original.clear()
        onChange()
    }

    override val size: Int
        get() = original.size

    override fun isEmpty(): Boolean = original.isEmpty()

    override fun containsKey(key: K): Boolean = original.containsKey(key)

    override fun containsValue(value: V): Boolean = original.containsValue(value)

    override fun get(key: K): V? = original[key]

    override fun serializer(): KSerializer<*> {
        return MapSerializer(keyType, valueType)
    }
}