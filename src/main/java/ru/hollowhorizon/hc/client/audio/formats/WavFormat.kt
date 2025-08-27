package ru.hollowhorizon.hc.client.audio.formats

import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.audio.*
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream

// TODO: read(size: Int) not working properly
object WavFormat : BinaryReader() {
    @Throws(Exception::class)
    fun read(stream: InputStream): Wave {

        val main = this.readChunk(stream)
        if (main.id != "RIFF") throw IllegalArgumentException("Given file is not 'RIFF'! It's '${main.id}' instead...")

        val format: String = this.readFourString(stream)
        if (format != "WAVE") throw Exception("Given RIFF file is not a 'WAVE' file! It's '$format' instead...")

        var audioFormat = -1
        var numChannels = -1
        var sampleRate = -1
        var byteRate = -1
        var blockAlign = -1
        var bitsPerSample = -1
        var data: ByteArray? = null
        val lists: MutableList<WaveList> = ArrayList()
        val cues: MutableList<WaveCue> = ArrayList()

        while (stream.available() > 0) {
            try {
                val chunk: BinaryChunk = this.readChunk(stream)
                when (chunk.id) {
                    "fmt " -> {
                        audioFormat = this.readShort(stream)
                        numChannels = this.readShort(stream)
                        sampleRate = this.readInt(stream)
                        byteRate = this.readInt(stream)
                        blockAlign = this.readShort(stream)
                        bitsPerSample = this.readShort(stream)
                        if (chunk.size > 16) stream.skip((chunk.size - 16).toLong())
                    }

                    "data" -> {
                        data = ByteArray(chunk.size)
                        stream.read(data)
                    }

                    else -> {
                        var cueData: ByteArray
                        var bytes: ByteArrayInputStream
                        when (chunk.id) {
                            "LIST" -> {
                                cueData = ByteArray(chunk.size)
                                stream.read(cueData)

                                bytes = ByteArrayInputStream(cueData)
                                val list = WaveList(this.readFourString(bytes))

                                while (bytes.available() > 0) {
                                    val id: String = this.readFourString(bytes)
                                    val size: Int = this.readInt(bytes)
                                    val stringData = ByteArray(size)
                                    bytes.read(stringData)
                                    val string = String(stringData)
                                    list.entries.add(Pair(id, string))
                                }

                                lists.add(list)
                            }

                            "cue " -> {
                                cueData = ByteArray(chunk.size)
                                stream.read(cueData)
                                bytes = ByteArrayInputStream(cueData)
                                var cuesCount: Int = this.readInt(bytes)

                                while (cuesCount > 0) {
                                    val cue = WaveCue()
                                    cue.id = this.readInt(bytes)
                                    cue.position = this.readInt(bytes)
                                    cue.dataChunkID = this.readInt(bytes)
                                    cue.chunkStart = this.readInt(bytes)
                                    cue.blockStart = this.readInt(bytes)
                                    cue.sampleStart = this.readInt(bytes)
                                    --cuesCount
                                    cues.add(cue)
                                }
                            }

                            else -> skip(stream, chunk.size.toLong())
                        }
                    }
                }
            } catch (ex: EOFException) {
                HollowCore.LOGGER.warn("End of file while reading WAV file!", ex)
            }
        }

        if (data == null) throw IllegalStateException("The data chunk isn't present in this file!")

        val wave = Wave(numChannels, sampleRate, byteRate, blockAlign, bitsPerSample, data)
        wave.lists = lists
        wave.cues = cues
        return wave
    }

    @Throws(Exception::class)
    fun readChunk(stream: InputStream): BinaryChunk {
        val id: String = this.readFourString(stream)
        val size: Int = this.readInt(stream)
        return BinaryChunk(id, size)
    }
}
