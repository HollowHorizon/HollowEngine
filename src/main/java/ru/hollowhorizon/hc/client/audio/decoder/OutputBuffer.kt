package ru.hollowhorizon.hc.client.audio.decoder

class OutputBuffer(private val channels: Int, private val isBigEndian: Boolean) {
    private var replayGainScale: Float? = null
    val buffer: ByteArray = ByteArray(BUFFERSIZE * channels)
    private val channelPointer: IntArray = IntArray(channels)

    init {
        reset()
    }

    private fun append(channel: Int, value: Short) {
        val firstByte: Byte
        val secondByte: Byte
        if (isBigEndian) {
            firstByte = (value.toInt() ushr 8 and 0xFF).toByte()
            secondByte = (value.toInt() and 0xFF).toByte()
        } else {
            firstByte = (value.toInt() and 0xFF).toByte()
            secondByte = (value.toInt() ushr 8 and 0xFF).toByte()
        }
        buffer[channelPointer[channel]] = firstByte
        buffer[channelPointer[channel] + 1] = secondByte
        channelPointer[channel] += channels * 2
    }

    fun appendSamples(channel: Int, f: FloatArray) {
        var s: Short
        if (replayGainScale != null) {
            var i = 0
            while (i < 32) {
                s = clip(f[i++] * replayGainScale!!)
                append(channel, s)
            }
        } else {
            var i = 0
            while (i < 32) {
                s = clip(f[i++])
                append(channel, s)
            }
        }
    }

    fun reset(): Int {
        try {
            val index = channels - 1
            return channelPointer[index] - index * 2
        } finally {
            for (i in 0 until channels) channelPointer[i] = i * 2
        }
    }

    val isStereo: Boolean
        get() = channelPointer[1] == 2

    private fun clip(sample: Float): Short {
        return if (sample > 32767.0f) 32767 else if (sample < -32768.0f) -32768 else sample.toInt().toShort()
    }

    companion object {
        const val BUFFERSIZE: Int = 2 * 1152 // max. 2 * 1152 samples per frame
    }
}
