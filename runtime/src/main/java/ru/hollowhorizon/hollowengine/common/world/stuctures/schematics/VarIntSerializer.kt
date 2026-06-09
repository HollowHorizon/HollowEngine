package ru.hollowhorizon.hollowengine.common.world.stuctures.schematics

import java.io.ByteArrayInputStream
import java.io.InputStream

object VarIntSerializer {
    fun readVarInt(input: InputStream): Int {
        var value = 0
        var size = 0
        var b: Int
        while (input.read().also { b = it } != -1) {
            value = value or (b and 0x7F shl size * 7)
            size++
            if (size > 5) throw RuntimeException("VarInt is too big")
            if (b and 0x80 == 0) break
        }
        return value
    }

    fun readVarIntArray(bytes: ByteArray, expectedSize: Int): List<Int> {
        val input = ByteArrayInputStream(bytes)
        val result = ArrayList<Int>(expectedSize)
        for (i in 0 until expectedSize) {
            result.add(i, readVarInt(input))
        }
        return result
    }
}