package ru.hollowhorizon.hc.client.audio

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

class BinaryChunk(var id: String, var size: Int)

abstract class BinaryReader {
    private var buf: ByteArray = ByteArray(4)


    fun readFourString(stream: InputStream): String {
        stream.read(this.buf)
        return String(this.buf)
    }

    fun readInt(stream: InputStream): Int {
        if (stream.read(this.buf) < 4) throw EOFException()
        else return b2i(buf[0], buf[1], buf[2], buf[3])
    }

    fun readShort(stream: InputStream): Int {
        if (stream.read(this.buf, 0, 2) < 2) throw IOException()
        else return b2i(buf[0], buf[1], 0.toByte(), 0.toByte())
    }

    fun skip(stream: InputStream, input: Long) {
        var bytes = input
        while (bytes > 0L) bytes -= stream.skip(bytes)
    }

    companion object {
        fun b2i(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int =
            b0.toInt() and 255 or ((b1.toInt() and 255) shl 8) or ((b2.toInt() and 255) shl 16) or ((b3.toInt() and 255) shl 24)
    }
}