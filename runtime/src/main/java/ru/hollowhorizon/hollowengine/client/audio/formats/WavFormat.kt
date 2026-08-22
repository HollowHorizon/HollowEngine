package ru.hollowhorizon.hollowengine.client.audio.formats

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.audio.*
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.StandardCharsets

object WavFormat : BinaryReader() {

    @Throws(Exception::class)
    fun read(stream: InputStream): Wave {
        val riffChunk = readChunkHeader(stream)
        if (riffChunk.id != "RIFF") {
            throw IllegalArgumentException("Invalid WAV file: missing 'RIFF' header (found '${riffChunk.id}')")
        }

        val format = readFourByteString(stream)
        if (format != "WAVE") {
            throw IllegalArgumentException("Invalid RIFF file: format is '$format', expected 'WAVE'")
        }

        var numChannels = 0
        var sampleRate = 0
        var byteRate = 0
        var blockAlign = 0
        var bitsPerSample = 0
        var data: ByteArray? = null
        val lists = ArrayList<WaveList>()
        val cues = ArrayList<WaveCue>()

        try {
            while (stream.available() > 0) {
                val chunk = try {
                    readChunkHeader(stream)
                } catch (e: EOFException) {
                    break
                }

                when (chunk.id) {
                    "fmt " -> {
                        readShort(stream) // audio format: 1 = PCM, 3 = Float, 65534 = Ext
                        numChannels = readShort(stream)
                        sampleRate = readInt(stream)
                        byteRate = readInt(stream)
                        blockAlign = readShort(stream)
                        bitsPerSample = readShort(stream)

                        val bytesReadSoFar = 16 // 2+2+4+4+2+2
                        if (chunk.size > bytesReadSoFar) {
                            skipFully(stream, (chunk.size - bytesReadSoFar).toLong())
                        }
                    }

                    "data" -> {
                        data = ByteArray(chunk.size)
                        readFully(stream, data)
                    }

                    "LIST" -> {
                        val listData = ByteArray(chunk.size)
                        readFully(stream, listData)
                        parseListChunk(listData, lists)
                    }

                    "cue " -> {
                        val cueData = ByteArray(chunk.size)
                        readFully(stream, cueData)
                        parseCueChunk(cueData, cues)
                    }

                    else -> {
                        // Метаданные или неизвестный чанк
                        skipFully(stream, chunk.size.toLong())
                    }
                }
            }
        } catch (e: Exception) {
            HollowEngine.LOGGER.warn("Error parsing WAV file structure", e)
        }

        if (data == null) {
            throw IllegalStateException("WAV file is missing 'data' chunk!")
        }

        var wave = Wave(numChannels, sampleRate, byteRate, blockAlign, bitsPerSample, data)
        if (bitsPerSample > 16) wave = wave.convertTo16()
        wave.lists = lists
        wave.cues = cues
        return wave
    }

    fun readChunkHeader(stream: InputStream): BinaryChunk {
        val id = readFourByteString(stream)
        val size = readInt(stream)
        return BinaryChunk(id, size)
    }

    private fun readFourByteString(stream: InputStream): String {
        val buffer = ByteArray(4)
        readFully(stream, buffer)
        return String(buffer, StandardCharsets.US_ASCII)
    }

    private fun readFully(stream: InputStream, buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read == -1) throw EOFException("Unexpected end of file while reading chunk data")
            offset += read
        }
    }

    private fun skipFully(stream: InputStream, amount: Long) {
        var remaining = amount
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped <= 0) {
                if (stream.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun parseListChunk(data: ByteArray, lists: MutableList<WaveList>) {
        try {
            val stream = ByteArrayInputStream(data)
            val listType = readFourByteString(stream)
            val list = WaveList(listType)

            while (stream.available() > 0) {
                if (stream.available() < 8) break
                val id = readFourByteString(stream)
                val size = readInt(stream)

                val entryData = ByteArray(size)
                val read = stream.read(entryData)
                if (read != size) break

                val text = String(entryData).trim { it <= ' ' }
                list.entries.add(id to text)

                if (size % 2 != 0) stream.skip(1)
            }
            lists.add(list)
        } catch (e: Exception) {
            // Игнорируем ошибки парсинга метаданных, так как это не критично для звука
        }
    }

    private fun parseCueChunk(data: ByteArray, cues: MutableList<WaveCue>) {
        try {
            val stream = ByteArrayInputStream(data)
            var cueCount = readInt(stream)

            while (cueCount > 0 && stream.available() >= 24) { // 6 * 4 bytes per cue point
                val cue = WaveCue()
                cue.id = readInt(stream)
                cue.position = readInt(stream)
                cue.dataChunkID = readInt(stream)
                cue.chunkStart = readInt(stream)
                cue.blockStart = readInt(stream)
                cue.sampleStart = readInt(stream)
                cues.add(cue)
                cueCount--
            }
        } catch (e: Exception) {
            // Игнорируем
        }
    }
}