package ru.hollowhorizon.hollowengine.client.ui.render

import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import java.nio.FloatBuffer
import java.nio.IntBuffer

internal class UiFloatArrayBuilder(initialCapacity: Int = 256) {
    private var values = FloatArray(initialCapacity)
    var size: Int = 0
        private set

    operator fun get(index: Int): Float = values[index]

    fun add(first: Float, second: Float, third: Float, fourth: Float) {
        ensureCapacity(size + 4)
        values[size++] = first
        values[size++] = second
        values[size++] = third
        values[size++] = fourth
    }

    fun add(first: Float, second: Float, third: Float, fourth: Float, fifth: Float, sixth: Float) {
        ensureCapacity(size + 6)
        values[size++] = first
        values[size++] = second
        values[size++] = third
        values[size++] = fourth
        values[size++] = fifth
        values[size++] = sixth
    }

    fun add(
        first: Float,
        second: Float,
        third: Float,
        fourth: Float,
        fifth: Float,
        sixth: Float,
        seventh: Float,
        eighth: Float,
        ninth: Float,
    ) {
        ensureCapacity(size + 9)
        values[size++] = first
        values[size++] = second
        values[size++] = third
        values[size++] = fourth
        values[size++] = fifth
        values[size++] = sixth
        values[size++] = seventh
        values[size++] = eighth
        values[size++] = ninth
    }

    fun addMatrix(matrix: UiMatrix4) {
        ensureCapacity(size + 16)
        matrix.writeValues(values, size)
        size += 16
    }

    fun writeTo(destination: FloatBuffer) {
        destination.put(values, 0, size)
    }

    fun addAll(source: FloatArray) {
        ensureCapacity(size + source.size)
        source.copyInto(values, size)
        size += source.size
    }

    fun copyRange(fromIndex: Int, toIndex: Int = size): FloatArray = values.copyOfRange(fromIndex, toIndex)

    fun clear() {
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required > values.size) values = values.copyOf(maxOf(required, values.size * 2))
    }
}

internal class UiIntArrayBuilder(initialCapacity: Int = 256) {
    private var values = IntArray(initialCapacity)
    var size: Int = 0
        private set

    operator fun get(index: Int): Int = values[index]

    fun add(value: Int) {
        ensureCapacity(size + 1)
        values[size++] = value
    }

    fun add(first: Int, second: Int, third: Int, fourth: Int) {
        ensureCapacity(size + 4)
        values[size++] = first
        values[size++] = second
        values[size++] = third
        values[size++] = fourth
    }

    fun writeTo(destination: IntBuffer) {
        destination.put(values, 0, size)
    }

    fun copyRange(fromIndex: Int, toIndex: Int = size): IntArray = values.copyOfRange(fromIndex, toIndex)

    fun clear() {
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required > values.size) values = values.copyOf(maxOf(required, values.size * 2))
    }
}
