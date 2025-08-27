package ru.hollowhorizon.hc.client.audio

import org.lwjgl.openal.AL10
import org.lwjgl.system.MemoryUtil

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
    val bytesPerSample get() = bitsPerSample / 8
    var byteRate: Int = sampleRate * numChannels * bytesPerSample
    var blockAlign: Int = numChannels * bytesPerSample
    var lists: List<WaveList> = ArrayList()
    var cues: List<WaveCue> = ArrayList()

    constructor(
        numChannels: Int, sampleRate: Int,
        byteRate: Int, blockAlign: Int, bitsPerSample: Int, data: ByteArray,
    ) : this(numChannels, sampleRate, bitsPerSample, data) {
        this.byteRate = byteRate
        this.blockAlign = blockAlign
        this.bitsPerSample = bitsPerSample
        this.data = data
    }

    val duration: Float
        get() = data.size.toFloat() / numChannels.toFloat() / bytesPerSample.toFloat() / sampleRate.toFloat()

    val aLFormat: Int
        get() {
            val bytes = this.bytesPerSample
            if (bytes == 1) {
                if (this.numChannels == 2) return AL10.AL_FORMAT_STEREO8
                if (this.numChannels == 1) return AL10.AL_FORMAT_MONO8
            } else if (bytes == 2) {
                if (this.numChannels == 2) return AL10.AL_FORMAT_STEREO16
                if (this.numChannels == 1) return AL10.AL_FORMAT_MONO16
            }

            throw IllegalStateException("Current WAV file has unusual configuration... channels: " + this.numChannels + ", BPS: " + bytes)
        }

    fun getScanRegion(pixelsPerSecond: Float): Int {
        return (sampleRate.toFloat() / pixelsPerSecond).toInt() * this.bytesPerSample * this.numChannels
    }

    fun convertTo16(): Wave {
        val c = data.size / this.numChannels / this.bytesPerSample
        val byteRate = c * this.numChannels * 2
        val data = ByteArray(byteRate)
        val isFloat = this.bytesPerSample == 4
        val wave = Wave(this.numChannels, this.sampleRate, byteRate, 2 * this.numChannels, 16, data)
        val sample = MemoryUtil.memAlloc(4)
        val dataBuffer = MemoryUtil.memAlloc(data.size)

        for (i in 0 until c * this.numChannels) {
            sample.clear()

            for (j in 0 until this.bytesPerSample) {
                sample.put(this.data[i * this.bytesPerSample + j])
            }

            if (isFloat) {
                sample.flip()
                dataBuffer.putShort((sample.getFloat() * 65535.0f / 2.0f).toInt().toShort())
            } else {
                sample.put(0.toByte())
                sample.flip()
                dataBuffer.putShort(
                    (sample.getInt().toFloat() / 8388607.5f * 32767.5f).toInt()
                        .toShort()
                )
            }
        }

        dataBuffer.flip()
        dataBuffer[data]
        MemoryUtil.memFree(sample)
        MemoryUtil.memFree(dataBuffer)
        wave.lists = this.lists
        wave.cues = this.cues
        return wave
    }

    fun getCues(): FloatArray {
        val cues = FloatArray(cues.size)

        this.cues.forEachIndexed { index, waveCue ->
            cues[index] = waveCue.position.toFloat() / sampleRate.toFloat()
        }

        return cues
    }
}