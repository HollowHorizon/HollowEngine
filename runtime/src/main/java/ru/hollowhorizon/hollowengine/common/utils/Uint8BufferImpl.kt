package ru.hollowhorizon.hollowengine.common.utils

import ru.hollowhorizon.hollowengine.logE
import java.nio.ByteBuffer

private typealias NioBuffer = java.nio.Buffer

abstract class GenericBuffer<B : NioBuffer>(
    override val capacity: Int,
    protected val buffer: B,
    isAutoLimit: Boolean,
) : Buffer {
    @PublishedApi
    internal var modCount = 0

    override var isAutoLimit: Boolean = isAutoLimit
        set(value) {
            field = value
            if (value) {
                buffer.limit(capacity)
            }
        }

    override var limit: Int
        get() = if (isAutoLimit) pos else buffer.limit()
        set(value) {
            modCount++
            buffer.limit(value)
            isAutoLimit = false
        }

    override var position: Int
        get() = pos
        set(value) {
            modCount++
            buffer.position(value)
            pos = value
        }

    protected var pos = 0

    override fun clear() {
        modCount++
        buffer.clear()
        position = 0
    }

    fun getRawBuffer(): B {
        buffer.position(0)
        if (isAutoLimit) {
            buffer.limit(pos)
        }
        return buffer
    }

    fun finishRawBuffer() {
        if (isAutoLimit) {
            buffer.limit(capacity)
        }
        buffer.position(pos)
    }

    inline fun <R> useRaw(block: (B) -> R): R {
        val modBefore = modCount
        val result = block(getRawBuffer())
        finishRawBuffer()
        val modAfter = modCount
        if (modBefore != modAfter) {
            logE { "Buffer was modified externally while used raw" }
        }
        return result
    }
}

class Uint8BufferImpl(
    buffer: ByteBuffer,
    isAutoLimit: Boolean = false
) : GenericBuffer<ByteBuffer>(buffer.capacity(), buffer, isAutoLimit), Uint8Buffer {
    override fun get(i: Int): UByte {
        return buffer[i].toUByte()
    }

    override fun set(i: Int, value: UByte) {
        modCount++
        buffer.put(i, value.toByte())
    }

    override fun put(value: UByte): Uint8Buffer {
        modCount++
        buffer.put(value.toByte())
        pos++
        return this
    }

    override fun put(data: ByteArray, offset: Int, len: Int): Uint8Buffer {
        modCount++
        buffer.put(data, offset, len)
        pos += len
        return this
    }

    override fun put(data: Uint8Buffer): Uint8Buffer {
        modCount++
        data.useRaw {
            buffer.put(it)
            pos += data.limit
        }
        return this
    }
}

inline fun <R> Uint8Buffer.useRaw(block: (ByteBuffer) -> R): R = (this as Uint8BufferImpl).useRaw(block)
