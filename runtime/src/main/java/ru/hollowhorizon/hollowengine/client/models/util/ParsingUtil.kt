package ru.hollowhorizon.hollowengine.client.models.util

import org.lwjgl.system.MemoryUtil
import ru.hollowhorizon.hollowengine.HollowEngine
import java.nio.ByteBuffer

fun ByteBuffer.strtol10(begin: Int, end: Int): Int {
    val bytes = ByteArray(end - begin) { get(begin + it) }
    return String(bytes).toInt()
}

fun ByteBuffer.strtoul10_64(beginOutMax: IntArray): Long {
    var cur = 0
    var value = 0L
    var begin = beginOutMax[0]
    var c = get(begin).toInt().toChar()
    if (c < '0' || c > '9') throw Exception("The string starting with \"$c\" cannot be converted into a value.")
    while (true) {
        if (c < '0' || c > '9') break
        val newValue = value * 10 + (c - '0')
        // numeric overflow, we rely on you
        if (newValue < value) HollowEngine.LOGGER.warn("Converting the string starting with \"$c\" into a value resulted in overflow.")
        //throw std::overflow_error();
        value = newValue
        c = get(++begin).toInt().toChar()
        ++cur
        if (beginOutMax[2] != 0 && beginOutMax[2] == cur) {
            if (beginOutMax[1] != 0) { /* skip to end */
                while (c in '0'..'9')
                    c = get(++begin).toInt().toChar()
                beginOutMax[1] = begin
            }
            return value
        }
    }
    if (beginOutMax[1] != 0) beginOutMax[1] = begin
    if (beginOutMax[2] != 0) beginOutMax[2] = cur
    return value
}

fun ByteBuffer.strtol10_64(begin: Int, end: Int): Long {
    val bytes = ByteArray(end - begin) { get(begin + it) }
    return String(bytes).toLong()
}

fun ByteBuffer.fast_atof(begin: Int, end: Int): Float {
    val bytes = ByteArray(end - begin) { get(begin + it) }
    return String(bytes).toFloat()
}

inline val Int.bool get() = toInt() != 0

infix fun ByteBuffer.startsWith(string: String) = string.all { get() == it.code.toByte() }

inline val ByteBuffer.rem: Int
    get() = remaining()

fun Buffer(size: Int): ByteBuffer = MemoryUtil.memCalloc(size)
operator fun ByteBuffer.set(index: Int, byte: Byte): ByteBuffer = put(index, byte)

val NUL = '\u0000'
val SP = '\u0020'
val HT = '\u0009'
val LF = '\u000A'
val CR = '\u000D'
val FF = '\u000C'

val Byte.isLineEnd get() = this == CR.code.toByte() || this == LF.code.toByte() || this == NUL.code.toByte() || this == FF.code.toByte()
val Char.isLineEnd get() = this == CR || this == LF || this == NUL || this == FF
val Char.isSpaceOrNewLine get() = isSpace || isLineEnd
val Byte.isSpace get() = this == SP.code.toByte() || this == HT.code.toByte()
val Char.isSpace get() = this == SP || this == HT

fun ByteBuffer.strncmp(string: String, ptr: Int = position(), length: Int = string.length): Boolean {
    // If lengths don't match, the strings can't be equal
    if (length != string.length) return false
    for (i in 0 until length)
        if (get(ptr + i).toInt().toChar() != string[i])
            return false
    return true
}

fun String.trimNUL(): String {
    val nulIdx = indexOf(NUL)
    return if (nulIdx != -1) substring(0, nulIdx)
    else this
}

