package ru.hollowhorizon.hc.client.audio.decoder

internal class BitReserve {
    private var offset = 0
    private var totbit = 0
    private var bufByteIdx = 0
    private val buf = IntArray(BUFSIZE)


    fun hsstell(): Int {
        return totbit
    }
    fun hgetbits(v: Int): Int {
        var n = v
        totbit += n

        var bits = 0

        var pos = bufByteIdx
        if (pos + n < BUFSIZE) while (n-- > 0) {
            bits = bits shl 1
            bits = bits or if (buf[pos++] != 0) 1 else 0
        }
        else while (n-- > 0) {
            bits = bits shl 1
            bits = bits or if (buf[pos] != 0) 1 else 0
            pos = pos + 1 and BUFSIZE_MASK
        }
        bufByteIdx = pos
        return bits
    }

    fun hget1bit(): Int {
        totbit++
        val bits = buf[bufByteIdx]
        bufByteIdx = bufByteIdx + 1 and BUFSIZE_MASK
        return bits
    }

    fun hputbuf(bits: Int) {
        var ofs = offset
        buf[ofs++] = bits and 0x80
        buf[ofs++] = bits and 0x40
        buf[ofs++] = bits and 0x20
        buf[ofs++] = bits and 0x10
        buf[ofs++] = bits and 0x08
        buf[ofs++] = bits and 0x04
        buf[ofs++] = bits and 0x02
        buf[ofs++] = bits and 0x01

        offset = if (ofs == BUFSIZE) 0
        else ofs
    }

    fun rewindNbits(n: Int) {
        totbit -= n
        bufByteIdx -= n
        if (bufByteIdx < 0) bufByteIdx += BUFSIZE
    }

    fun rewindNbytes(n: Int) {
        val bits = n shl 3
        totbit -= bits
        bufByteIdx -= bits
        if (bufByteIdx < 0) bufByteIdx += BUFSIZE
    }

    companion object {
        private const val BUFSIZE = 4096 * 8
        private const val BUFSIZE_MASK = BUFSIZE - 1
    }
}
