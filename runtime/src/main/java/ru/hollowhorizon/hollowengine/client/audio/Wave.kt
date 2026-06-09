package ru.hollowhorizon.hollowengine.client.audio

import org.lwjgl.openal.AL10
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveCue {
    var id: Int = 0
    var position: Int = 0
    var dataChunkID: Int = 0
    var chunkStart: Int = 0
    var blockStart: Int = 0
    var sampleStart: Int = 0
}

class WaveList(var type: String) {
    var entries: MutableList<Pair<String, String>> = ArrayList()
}

class Wave(
    var numChannels: Int,
    var sampleRate: Int,
    var bitsPerSample: Int,
    var data: ByteArray,
) {
    var byteRate: Int = sampleRate * numChannels * (bitsPerSample / 8)
    var blockAlign: Int = numChannels * (bitsPerSample / 8)
    var lists: List<WaveList> = ArrayList()
    var cues: List<WaveCue> = ArrayList()

    constructor(
        numChannels: Int, sampleRate: Int,
        byteRate: Int, blockAlign: Int, bitsPerSample: Int, data: ByteArray,
    ) : this(numChannels, sampleRate, bitsPerSample, data) {
        this.byteRate = byteRate
        this.blockAlign = blockAlign
    }

    val bytesPerSample: Int
        get() = bitsPerSample / 8

    val duration: Float
        get() = if (byteRate > 0) data.size.toFloat() / byteRate.toFloat() else 0f

    val aLFormat: Int
        get() {
            return when (numChannels) {
                1 -> when (bitsPerSample) {
                    8 -> AL10.AL_FORMAT_MONO8
                    16 -> AL10.AL_FORMAT_MONO16
                    else -> throw IllegalStateException("Unsupported BPS: $bitsPerSample ($bytesPerSample bytes). Call convertTo16() first.")
                }
                2 -> when (bitsPerSample) {
                    8 -> AL10.AL_FORMAT_STEREO8
                    16 -> AL10.AL_FORMAT_STEREO16
                    else -> throw IllegalStateException("Unsupported BPS: $bitsPerSample ($bytesPerSample bytes). Call convertTo16() first.")
                }
                else -> throw IllegalStateException("Unsupported channel count: $numChannels")
            }
        }

    fun convertTo16(): Wave {
        if (bitsPerSample == 16) return this

        val sourceBytes = bitsPerSample / 8
        if (sourceBytes == 0) throw IllegalStateException("Invalid bitsPerSample: $bitsPerSample")

        val sampleCount = data.size / sourceBytes
        val destData = ByteArray(sampleCount * 2)

        val srcBuffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val destBuffer = ByteBuffer.wrap(destData).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleCount) {
            val pcm16: Short = when (bitsPerSample) {
                8 -> {
                    val sample = srcBuffer.get().toInt() and 0xFF
                    ((sample - 128) * 256).toShort()
                }

                24 -> {
                    val b1 = srcBuffer.get().toInt() and 0xFF
                    val b2 = srcBuffer.get().toInt() and 0xFF
                    val b3 = srcBuffer.get().toInt()
                    val sample32 = b1 or (b2 shl 8) or (b3 shl 16)
                    (sample32 shr 8).toShort()
                }

                32 -> (srcBuffer.float * 32767.0f).coerceIn(-32768.0f, 32767.0f).toInt().toShort()
                else -> 0
            }
            destBuffer.putShort(pcm16)
        }

        val newWave = Wave(
            numChannels = numChannels,
            sampleRate = sampleRate,
            byteRate = sampleRate * numChannels * 2,
            blockAlign = numChannels * 2,
            bitsPerSample = 16,
            data = destData
        )
        newWave.lists = this.lists
        newWave.cues = this.cues
        return newWave
    }

}