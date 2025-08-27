package ru.hollowhorizon.hc.client.audio.decoder

class Crc16 {
    private var crc: Short

    init {
        crc = 0xFFFF.toShort()
    }

    fun addBits(bitstring: Int, length: Int) {
        var bitmask = 1 shl length - 1
        do if (((crc.toInt() and 0x8000) == 0) xor ((bitstring and bitmask) == 0)) {
            crc = (crc.toInt() shl 1).toShort()
            crc = (crc.toInt() xor POLYNOMIAL.toInt()).toShort()
        } else crc = (crc.toInt() shl 1).toShort()
        while ((1.let { bitmask = bitmask ushr it; bitmask }) != 0)
    }

    fun checksum(): Short {
        val sum = crc
        crc = 0xFFFF.toShort()
        return sum
    }

    companion object {
        private const val POLYNOMIAL = 0x8005.toShort()
    }
}
