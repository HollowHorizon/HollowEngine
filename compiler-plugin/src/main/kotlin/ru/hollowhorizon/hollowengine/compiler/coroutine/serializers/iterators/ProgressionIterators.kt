@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.iterators

import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import java.util.*
import java.util.function.Consumer

val ListType = pluginContext.referenceClass(ClassId(FqName("kotlin.collections"), Name.identifier("List")))!!.defaultType
val CharRangeType = pluginContext.referenceClass(ClassId(FqName("kotlin.ranges"), Name.identifier("CharRange")))!!.defaultType
val IntRangeType = pluginContext.referenceClass(ClassId(FqName("kotlin.ranges"), Name.identifier("IntRange")))!!.defaultType
val LongRangeType = pluginContext.referenceClass(ClassId(FqName("kotlin.ranges"), Name.identifier("LongRange")))!!.defaultType

val pkg = FqName("ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.iterators")
val CharProgressionCtor = pluginContext.referenceClass(ClassId(pkg, Name.identifier("CharProgressionIterator")))!!
    .constructors.first { it.owner.valueParameters.size == 1 }
val IntProgressionCtor = pluginContext.referenceClass(ClassId(pkg, Name.identifier("IntProgressionIterator")))!!
    .constructors.first { it.owner.valueParameters.size == 1 }
val LongProgressionCtor = pluginContext.referenceClass(ClassId(pkg, Name.identifier("LongProgressionIterator")))!!
    .constructors.first { it.owner.valueParameters.size == 1 }
val SerializableIteratorCtor = pluginContext.referenceClass(ClassId(pkg, Name.identifier("SerializableIterator")))!!
    .constructors.first { it.owner.valueParameters.size == 1 }

@Serializable
class SerializableIterator<T>(private val values: List<T>): Iterator<T> {
    private var cursor: Int = 0 // index of next element to return

    override fun hasNext() = cursor != values.size

    override fun next(): T {
        return values[cursor++]
    }
}

@Serializable
class CharProgressionIterator(val first: Char, val last: Char, val step: Int) : CharIterator() {
    constructor(range: CharRange) : this(range.first, range.last, 1)

    private val finalElement: Int = last.code
    private var hasNext: Boolean = if (step > 0) first <= last else first >= last
    private var next: Int = if (hasNext) first.code else finalElement

    override fun hasNext(): Boolean = hasNext

    override fun nextChar(): Char {
        val value = next
        if (value == finalElement) {
            if (!hasNext) throw kotlin.NoSuchElementException()
            hasNext = false
        } else {
            next += step
        }
        return value.toChar()
    }
}

@Serializable
class IntProgressionIterator(val first: Int, val last: Int, val step: Int) : IntIterator() {
    constructor(range: IntProgression) : this(range.first, range.last, 1)

    private val finalElement: Int = last
    private var hasNext: Boolean = if (step > 0) first <= last else first >= last
    private var next: Int = if (hasNext) first else finalElement

    override fun hasNext(): Boolean = hasNext

    override fun nextInt(): Int {
        val value = next
        if (value == finalElement) {
            if (!hasNext) throw kotlin.NoSuchElementException()
            hasNext = false
        } else {
            next += step
        }
        return value
    }
}

@Serializable
class LongProgressionIterator(val first: Long, val last: Long, val step: Long) : LongIterator() {
    constructor(range: LongRange) : this(range.first, range.last, 1)

    private val finalElement: Long = last
    private var hasNext: Boolean = if (step > 0) first <= last else first >= last
    private var next: Long = if (hasNext) first else finalElement

    override fun hasNext(): Boolean = hasNext

    override fun nextLong(): Long {
        val value = next

        if (value == finalElement) {
            if (!hasNext) throw kotlin.NoSuchElementException()
            hasNext = false
        } else {
            next += step
        }
        return value
    }
}

