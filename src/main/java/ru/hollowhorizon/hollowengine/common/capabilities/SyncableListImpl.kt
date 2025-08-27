/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:Suppress("UnstableApiUsage", "UNCHECKED_CAST")

package ru.hollowhorizon.hollowengine.common.capabilities

import com.google.common.reflect.TypeToken
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.utils.nbt.INBTSerializable
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import java.util.function.Predicate

class SyncableListImpl<T : Any>(
    val list: MutableList<T>,
    type: Class<T>? = null,
    private val updateMethod: () -> Unit = {},
) : MutableList<T>, INBTSerializable {
    private val serializer = SyncableListSerializer(type ?: throw IllegalStateException("Type must be not null"))

    companion object {
        inline fun <reified E : Any> create(
            list: MutableList<E> = ArrayList(),
            noinline updateMethod: () -> Unit = {},
        ) = SyncableListImpl(list, E::class.java, updateMethod)
    }

    override val size get() = list.size

    override fun clear() {
        list.clear()
        updateMethod()
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val addedAny = list.addAll(elements)
        if (addedAny) updateMethod()
        return addedAny
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        val addedAny = list.addAll(index, elements)
        if (addedAny) updateMethod()
        return addedAny
    }

    fun addNoUpdate(index: Int, element: T) {
        list.add(index, element)
    }

    fun addNoUpdate(element: T) {
        list.add(element)
    }

    override fun add(index: Int, element: T) {
        list.add(index, element)
        updateMethod()
    }

    override fun add(element: T): Boolean {
        return list.add(element).apply {
            updateMethod()
        }
    }

    override fun get(index: Int) = list[index]

    override fun indexOf(element: T) = list.indexOf(element)

    override fun containsAll(elements: Collection<T>) = list.containsAll(elements)

    override fun isEmpty() = list.isEmpty()

    override fun contains(element: T) = list.contains(element)

    override fun iterator(): MutableIterator<T> {
        return Itr(list.iterator())
    }

    override fun listIterator(): MutableListIterator<T> {
        return ListItr(list.listIterator())
    }

    override fun listIterator(index: Int): MutableListIterator<T> {
        return ListItr(list.listIterator(index))
    }

    override fun removeAt(index: Int): T {
        return list.removeAt(index).apply {
            updateMethod()
        }
    }

    fun removeAtNoUpdate(index: Int) {
        list.removeAt(index)
    }

    fun removeIfNoUpdate(filter: Predicate<T>): Boolean {
        val each = Itr(list.iterator())
        var removed = false

        while (each.hasNext()) {
            if (filter.test(each.next())) {
                each.removeNoUpdate()
                removed = true
            }
        }

        return removed
    }

    override fun set(index: Int, element: T): T {
        return list.set(index, element).apply {
            updateMethod()
        }
    }

    fun setNoUpdate(index: Int, element: T) {
        list[index] = element
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        val changedAny = list.retainAll(elements)
        if (changedAny) updateMethod()
        return changedAny
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        val changedAny = list.removeAll(elements)
        if (changedAny) updateMethod()
        return changedAny
    }

    override fun remove(element: T): Boolean {
        val changedAny = list.remove(element)
        if (changedAny) updateMethod()
        return changedAny
    }

    fun removeNoUpdate(element: T) {
        list.remove(element)
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return list.subList(fromIndex, toIndex)
    }

    override fun lastIndexOf(element: T) = list.lastIndexOf(element)

    private open inner class Itr<U>(val internalIterator: MutableIterator<U>) : MutableIterator<U> {
        override fun hasNext() = internalIterator.hasNext()

        override fun next(): U = internalIterator.next()

        override fun remove() {
            internalIterator.remove()
            updateMethod()
        }

        fun removeNoUpdate() {
            internalIterator.remove()
        }
    }

    private inner class ListItr(internalIterator: MutableListIterator<T>) : Itr<T>(internalIterator),
        MutableListIterator<T> {
        private fun internalIterator(): MutableListIterator<T> {
            return internalIterator as MutableListIterator<T>
        }

        override fun add(element: T) {
            internalIterator().add(element)
            updateMethod()
        }

        override fun hasPrevious() = internalIterator().hasPrevious()

        override fun nextIndex() = internalIterator().nextIndex()

        override fun previous() = internalIterator().previous()

        override fun previousIndex() = internalIterator().previousIndex()

        override fun set(element: T) {
            internalIterator().set(element)
            updateMethod()
        }

    }

    override fun serialize(): Tag {
        return NBTFormat.serialize(serializer, this)
    }

    override fun deserialize(nbt: Tag) {
        val list = NBTFormat.deserialize(serializer, nbt)
        this.list.clear()
        this.list.addAll(list)
    }

    override fun toString(): String {
        return list.toString()
    }
}

class SyncableListSerializer<T : Any>(val type: Class<T>) : KSerializer<SyncableListImpl<T>> {
    private val baseTypeSerializer = NBTFormat.serializersModule.serializer(TypeToken.of(type).type) as KSerializer<T>


    private val delegatedSerializer = ListSerializer(baseTypeSerializer)

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor = SerialDescriptor("list_serializer", delegatedSerializer.descriptor)

    override fun serialize(encoder: Encoder, value: SyncableListImpl<T>) {
        val l = value.toList()
        encoder.encodeSerializableValue(delegatedSerializer, l)
    }

    override fun deserialize(decoder: Decoder): SyncableListImpl<T> {
        val l = decoder.decodeSerializableValue(delegatedSerializer)
        return SyncableListImpl(l.toMutableList(), type)
    }
}