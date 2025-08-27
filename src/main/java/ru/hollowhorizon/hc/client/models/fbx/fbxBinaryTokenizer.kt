package ru.hollowhorizon.hc.client.models.fbx

import ru.hollowhorizon.hc.client.models.util.rem
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty0

fun tokenizeError(message: String, offset: Int): Nothing = throw Exception(Util.addOffset("FBX-Tokenize", message, offset))

fun tokenizeError(message: String, input: ByteBuffer): Nothing = tokenizeError(message, input.pos)

fun ByteBuffer.readString(beginOut: KMutableProperty0<Int>? = null, endOut: KMutableProperty0<Int>? = null, longLength: Boolean = false, allowNull: Boolean = false): Int {
    val lenLen = if (longLength) 4 else 1
    if (remaining() < lenLen) tokenizeError("cannot ReadString, out of bounds reading length", this)

    val length = if (longLength) int else get().toInt()

    if (remaining() < length) tokenizeError("cannot ReadString, length is out of bounds", this)

    val b = pos
    beginOut?.set(pos)
    pos += length

    endOut?.set(position())

    if (!allowNull)
        for (i in 0 until length)
            if (get(b + i) == 0.toByte())
                tokenizeError("failed ReadString, unexpected NUL character in string", this)

    return length
}

var ByteBuffer.pos: Int
    get() = position()
    set(value) {
        position(value)
    }

fun ByteBuffer.readData(beginOut: KMutableProperty0<Int>, endOut: KMutableProperty0<Int>) {
    if (remaining() < 1) tokenizeError("cannot ReadData, out of bounds reading length", this)

    val type = get(position()).toInt().toChar()
    beginOut.set(pos++)

    when (type) {
        // 16 bit int
        'Y' -> pos += Short.SIZE_BYTES
        // 1 bit bool flag (yes/no)
        'C' -> pos++
        // 32 bit int or float
        'I', 'F' -> pos += Int.SIZE_BYTES
        // double
        'D' -> pos += Double.SIZE_BYTES
        // 64 bit int
        'L' -> pos += Long.SIZE_BYTES
        // note: do not write cursor += ReadWord(...cursor) as this would be UB
        // raw binary data
        'R' -> {
            val length = int
            pos += length
        }
        // array of *
        'b', 'c', 'f', 'd', 'l', 'i' -> {
            val length = int
            val encoding = int
            val compLen = int

            // compute length based on type and check against the stored value
            if (encoding == 0) {
                val stride = when (type) {
                    'b', 'c' -> 1
                    'f', 'i' -> 4
                    'd', 'l' -> 8
                    else -> throw Exception("invalid type")
                }
                if (length * stride != compLen) tokenizeError("cannot ReadData, calculated data stride differs from what the file claims", this)
            }
            // zip/deflate algorithm (encoding==1)? take given length. anything else? die
            else if (encoding != 1) tokenizeError("cannot ReadData, unknown encoding", this)
            pos += compLen
        }
        // string
        'S' -> readString(longLength = true, allowNull = true) // 0 characters can legally happen in such strings
        else -> tokenizeError("cannot ReadData, unexpected type code: $type", this)
    }

    if (pos > capacity()) tokenizeError("cannot ReadData, the remaining size is too small for the data type: $type", this)

    // the type code is contained in the returned range
    endOut.set(pos)
}

fun readScope(outputTokens: ArrayList<Token>, input: ByteBuffer, end: Int, is64bits: Boolean): Boolean {

    // the first word contains the offset at which this block ends
    val endOffset = if (is64bits) input.double.toInt() else input.int

    /*  we may get 0 if reading reached the end of the file - fbx files have a mysterious extra footer which I don't
        know how to extract any information from, but at least it always starts with a 0. */
    if (endOffset == 0) return false

    if (endOffset > end) tokenizeError("block offset is out of range", input)
    else if (endOffset < input.pos) tokenizeError("block offset is negative out of range", input)

    // the second data word contains the number of properties in the scope
    val propCount = if (is64bits) input.double.toInt() else input.int

    // the third data word contains the length of the property list
    val propLength = if (is64bits) input.double.toInt() else input.int

    // now comes the name of the scope/key
    input.readString(::sBeg, ::sEnd)

    outputTokens.add(Token(sBeg, sEnd, TokenType.KEY, input.pos))

    // now come the individual properties
    val beginCursor = input.pos
    repeat(propCount) {
        val begin = input.pos
        input.readData(::sBeg, ::sEnd)

        outputTokens.add(Token(sBeg, sEnd, TokenType.DATA, input.pos))

        if (it != propCount - 1) outputTokens.add(Token(input.pos, input.pos + 1, TokenType.COMMA, input.pos))
    }

    if (input.pos - beginCursor != propLength) tokenizeError("property length not reached, something is wrong", input)

    /*  at the end of each nested block, there is a NUL record to indicate that the sub-scope exists
        (i.e. to distinguish between P: and P : {})
        this NUL record is 13 bytes long on 32 bit version and 25 bytes long on 64 bit. */
    val sentinelBlockLength = (if (is64bits) Long.SIZE_BYTES else Int.SIZE_BYTES) * 3 + 1

    if (input.pos < endOffset) {
        if (endOffset - input.pos < sentinelBlockLength) tokenizeError("insufficient padding bytes at block end", input)

        outputTokens.add(Token(input.pos, input.pos + 1, TokenType.OPEN_BRACKET, input.pos))

        // XXX this is vulnerable to stack overflowing ..
        while (input.pos < endOffset - sentinelBlockLength)
            readScope(outputTokens, input, endOffset - sentinelBlockLength, is64bits)
        outputTokens.add(Token(input.pos, input.pos + 1, TokenType.CLOSE_BRACKET, input.pos))

        for (i in 0 until sentinelBlockLength)
            if (input.get(input.pos + i) != 0.toByte())
                tokenizeError("failed to read nested block sentinel, expected all bytes to be 0", input)
        input.pos += sentinelBlockLength
    }

    if (input.pos != endOffset) tokenizeError("scope length not reached, something is wrong", input)

    return true
}

private var sBeg = -1
private var sEnd = -1

// TODO: Test FBX Binary files newer than the 7500 version to check if the 64 bits address behaviour is consistent
fun tokenizeBinary(outputTokens: ArrayList<Token>, input: ByteBuffer) {

    if (input.rem < 0x1b) tokenizeError("file is too short", 0)

    /*Result ignored*/ input.get()
    /*Result ignored*/ input.get()
    /*Result ignored*/ input.get()
    /*Result ignored*/ input.get()
    /*Result ignored*/ input.get()
    val version = input.int
    val is64bits = version >= 7500
    while (true)
        if (!readScope(outputTokens, input, input.capacity(), is64bits))
            break
}