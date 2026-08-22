package ru.hollowhorizon.hollowengine.client.audio.streams

import net.minecraft.client.sounds.AudioStream
import org.lwjgl.system.MemoryUtil
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.audio.Wave
import ru.hollowhorizon.hollowengine.client.audio.WaveCue
import ru.hollowhorizon.hollowengine.client.audio.WaveList
import ru.hollowhorizon.hollowengine.client.audio.formats.WavFormat
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat

class WavAudioStream(wavInputStream: InputStream) : AudioStream {
    private val inputStream = BufferedInputStream(wavInputStream)
    private val wave: Wave
    private val audioFormat: AudioFormat
    private var dataChunkSize: Int = -1
    private var bytesRead: Int = 0
    private var dataChunkReached: Boolean = false

    init {
        wave = readWaveHeader(inputStream)

        audioFormat = AudioFormat(
            wave.sampleRate.toFloat(),
            wave.bitsPerSample,
            wave.numChannels,
            wave.bitsPerSample > 8,
            false
        )
    }

    @Throws(Exception::class)
    private fun readWaveHeader(stream: InputStream): Wave {
        val wavFormat = WavFormat
        val main = wavFormat.readChunkHeader(stream)
        if (main.id != "RIFF") throw IllegalArgumentException("Not a RIFF file: ${main.id}")

        val format = wavFormat.readFourString(stream)
        if (format != "WAVE") throw IllegalArgumentException("Not a WAVE file: $format")

        var audioFormat = -1
        var numChannels = -1
        var sampleRate = -1
        var byteRate = -1
        var blockAlign = -1
        var bitsPerSample = -1
        val lists: MutableList<WaveList> = ArrayList()
        val cues: MutableList<WaveCue> = ArrayList()

        while (stream.available() > 0 && !dataChunkReached) {
            try {
                val chunk = wavFormat.readChunkHeader(stream)
                when (chunk.id) {
                    "fmt " -> {
                        audioFormat = wavFormat.readShort(stream)
                        numChannels = wavFormat.readShort(stream)
                        sampleRate = wavFormat.readInt(stream)
                        byteRate = wavFormat.readInt(stream)
                        blockAlign = wavFormat.readShort(stream)
                        bitsPerSample = wavFormat.readShort(stream)
                        if (chunk.size > 16) stream.skip((chunk.size - 16).toLong())
                    }

                    "data" -> {
                        dataChunkSize = chunk.size
                        dataChunkReached = true
                    }

                    else -> {
                        var cueData: ByteArray
                        var bytes: ByteArrayInputStream
                        when (chunk.id) {
                            "LIST" -> {
                                cueData = ByteArray(chunk.size)
                                stream.read(cueData)
                                bytes = ByteArrayInputStream(cueData)
                                val list = WaveList(wavFormat.readFourString(bytes))
                                while (bytes.available() > 0) {
                                    val id = wavFormat.readFourString(bytes)
                                    val size = wavFormat.readInt(bytes)
                                    val stringData = ByteArray(size)
                                    bytes.read(stringData)
                                    list.entries.add(Pair(id, String(stringData)))
                                }
                                lists.add(list)
                            }

                            "cue " -> {
                                cueData = ByteArray(chunk.size)
                                stream.read(cueData)
                                bytes = ByteArrayInputStream(cueData)
                                var cuesCount = wavFormat.readInt(bytes)
                                while (cuesCount > 0) {
                                    val cue = WaveCue()
                                    cue.id = wavFormat.readInt(bytes)
                                    cue.position = wavFormat.readInt(bytes)
                                    cue.dataChunkID = wavFormat.readInt(bytes)
                                    cue.chunkStart = wavFormat.readInt(bytes)
                                    cue.blockStart = wavFormat.readInt(bytes)
                                    cue.sampleStart = wavFormat.readInt(bytes)
                                    cues.add(cue)
                                    cuesCount--
                                }
                            }

                            else -> stream.skip(chunk.size.toLong())
                        }
                    }
                }
            } catch (ex: EOFException) {
                HollowEngine.LOGGER.warn("End of file while reading WAV file!", ex)
            }
        }

        if (!dataChunkReached) throw IllegalStateException("No data chunk found in WAV file!")

        return Wave(numChannels, sampleRate, byteRate, blockAlign, bitsPerSample, byteArrayOf())
            .apply {
                this.lists = lists
                this.cues = cues
            }
    }

    override fun getFormat(): AudioFormat {
        return audioFormat
    }

    override fun read(size: Int): ByteBuffer {
        if (!dataChunkReached || bytesRead >= dataChunkSize) {
            return ByteBuffer.allocate(0) // Нет данных для чтения
        }

        val actualSize = minOf(size, dataChunkSize - bytesRead)
        val buffer = ByteArray(actualSize)
        val read = inputStream.read(buffer, 0, actualSize)
        if (read > 0) {
            bytesRead += read
            return ByteBuffer.wrap(buffer, 0, read)
        }
        return ByteBuffer.allocate(0)
    }

    var byteBuffer: ByteBuffer? = null

    fun readAll(): ByteBuffer {
        if (!dataChunkReached || bytesRead >= dataChunkSize) {
            return ByteBuffer.allocate(0)
        }

        val remaining = dataChunkSize - bytesRead
        val buffer = ByteArray(remaining)
        val read = inputStream.read(buffer)
        if (read > 0) {
            bytesRead += read
            MemoryUtil.memFree(byteBuffer)
            val buffer = MemoryUtil.memAlloc(buffer.size).apply {
                put(buffer)
                flip()
            }
            byteBuffer = buffer
            return buffer
        }
        return ByteBuffer.allocate(0)
    }

    override fun close() {
        inputStream.close()
        MemoryUtil.memFree(byteBuffer)
    }
}