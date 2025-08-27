package ru.hollowhorizon.hc.client.models.gltf

import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.client.utils.stream
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

private typealias NioBuffer = java.nio.Buffer

class DataStream(val data: Uint8Buffer, val byteOffset: Int = 0) {
    var index = 0

    fun hasRemaining() = index < data.capacity

    fun readByte() = data[byteOffset + index++].toInt()

    fun readUByte() = data[byteOffset + index++].toInt() and 0xff

    fun readShort(): Int {
        var s = readUShort()
        if (s > 32767) s -= 65536
        return s
    }

    fun readUShort(): Int {
        var d = 0
        for (i in 0..1) d = d or (readUByte() shl (i * 8))
        return d
    }

    fun readInt() = readUInt()

    fun readUInt(): Int {
        var d = 0
        for (i in 0..3) d = d or (readUByte() shl (i * 8))
        return d
    }

    fun readFloat() = Float.fromBits(readUInt())


    fun readData(len: Int): Uint8Buffer {
        val buf = Uint8Buffer(len)
        for (i in 0 until len) buf[i] = data[index++]
        return buf
    }

    fun skipBytes(nBytes: Int) {
        index += nBytes
    }
}

fun Uint8Buffer(capacity: Int, isAutoLimit: Boolean = false): Uint8Buffer = Uint8BufferImpl(capacity, isAutoLimit)

abstract class GenericBuffer<B : NioBuffer>(
    override val capacity: Int,
    protected val buffer: B,
    isAutoLimit: Boolean,
) : Buffer {

    override var isAutoLimit: Boolean = isAutoLimit
        set(value) {
            field = value
            if (value) buffer.limit(capacity)
        }

    override var limit: Int
        get() = if (isAutoLimit) pos else buffer.limit()
        set(value) {
            buffer.limit(value)
            isAutoLimit = false
        }

    override var position: Int
        get() = pos
        set(value) {
            buffer.position(value)
            pos = value
        }

    protected var pos = 0

    override fun clear() {
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
        if (isAutoLimit) buffer.limit(capacity)
        buffer.position(pos)
    }

    inline fun <R> useRaw(block: (B) -> R): R {
        val result = block(getRawBuffer())
        finishRawBuffer()
        return result
    }
}


interface Buffer {
    val capacity: Int
    var position: Int
    var limit: Int
    var isAutoLimit: Boolean

    val remaining: Int get() = capacity - position

    fun clear()

    fun checkCapacity(requiredSize: Int) = check(requiredSize <= remaining) {
        RuntimeException("Insufficient remaining size. Requested: $requiredSize, remaining: $remaining")
    }
}

interface Uint8Buffer : Buffer {
    operator fun get(i: Int): UByte
    operator fun set(i: Int, value: UByte)
    fun put(value: UByte): Uint8Buffer
    fun put(data: ByteArray, offset: Int, len: Int): Uint8Buffer
    fun put(data: Uint8Buffer): Uint8Buffer

    operator fun plusAssign(value: UByte) {
        put(value)
    }

    operator fun plusAssign(value: Byte) {
        put(value)
    }

    fun put(value: Byte): Uint8Buffer = put(value.toUByte())
    fun put(data: ByteArray): Uint8Buffer = put(data, 0, data.size)

    fun toArray(): ByteArray = ByteArray(capacity) { get(it).toByte() }
}

class Uint8BufferImpl(buffer: ByteBuffer, isAutoLimit: Boolean = false) :
    GenericBuffer<ByteBuffer>(buffer.capacity(), buffer, isAutoLimit), Uint8Buffer {

    constructor(capacity: Int, isAutoLimit: Boolean = false) : this(
        ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder()),
        isAutoLimit
    )

    constructor(data: ByteArray) : this(ByteBuffer.allocateDirect(data.size).order(ByteOrder.nativeOrder()), false) {
        put(data)
    }

    override fun get(i: Int): UByte {
        return buffer[i].toUByte()
    }

    override fun set(i: Int, value: UByte) {
        buffer.put(i, value.toByte())
    }

    override fun put(value: UByte): Uint8Buffer {
        buffer.put(value.toByte())
        pos++
        return this
    }

    override fun put(data: ByteArray, offset: Int, len: Int): Uint8Buffer {
        buffer.put(data, offset, len)
        pos += len
        return this
    }

    override fun put(data: Uint8Buffer): Uint8Buffer {
        data.useRaw {
            buffer.put(it)
            pos += data.limit
        }
        return this
    }
}

inline fun <R> Uint8Buffer.useRaw(block: (ByteBuffer) -> R): R = (this as Uint8BufferImpl).useRaw(block)
fun Uint8Buffer.inflate() = Uint8BufferImpl(GZIPInputStream(ByteArrayInputStream(toArray())).readBytes())


fun Uint8Buffer.decodeToString(): String = toArray().decodeToString()


fun loadBlob(key: String) = Uint8BufferImpl(key.rl.stream.readBytes())