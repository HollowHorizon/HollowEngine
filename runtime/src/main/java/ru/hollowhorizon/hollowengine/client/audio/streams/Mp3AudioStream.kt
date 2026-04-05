package ru.hollowhorizon.hollowengine.client.audio.streams

import net.minecraft.client.sounds.AudioStream
import org.lwjgl.system.MemoryUtil
import ru.hollowhorizon.hollowengine.client.audio.decoder.Bitstream
import ru.hollowhorizon.hollowengine.client.audio.decoder.Header
import ru.hollowhorizon.hollowengine.client.audio.decoder.OutputBuffer
import ru.hollowhorizon.hollowengine.client.audio.formats.Mp3Format
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat

// TODO: read(size: Int) not working properly
class Mp3StreamingAudioStream(input: InputStream) : AudioStream {
    private val bitstream = Bitstream(input.buffered())
    private val decoder = Mp3Format.Decode()
    private val outputBuffer: OutputBuffer
    private val channels: Int
    private val rate: Int

    init {
        val header = bitstream.readFrame() ?: throw error("Empty MP3 stream")
        channels = if (header.mode() == Header.SINGLE_CHANNEL) 1 else 2
        rate = header.sampleRate

        outputBuffer = OutputBuffer(channels, false)
        decoder.output = outputBuffer
        decoder.decodeFrame(header, bitstream)
        bitstream.closeFrame()
    }

    override fun getFormat(): AudioFormat {
        return AudioFormat(
            rate.toFloat(),
            16,
            channels,
            true,  // signed
            false  // little endian
        )
    }

    override fun read(size: Int): ByteBuffer {
        val out = ByteArrayOutputStream(size)

        while (out.size() < size) {
            val header = bitstream.readFrame() ?: break
            try {
                decoder.decodeFrame(header, bitstream)
            } catch (ex: Exception) {
                ex.printStackTrace()
                break
            }
            bitstream.closeFrame()

            val len = outputBuffer.reset()
            val needed = size - out.size()
            out.write(outputBuffer.buffer, 0, minOf(len, needed))
        }

        val data = out.toByteArray()
        if (data.isEmpty()) return EMPTY_BUFFER

        val buffer = MemoryUtil.memAlloc(data.size)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    fun readAll(): ByteBuffer {
        val out = ByteArrayOutputStream()

        while (true) {
            val header = bitstream.readFrame() ?: break
            try {
                decoder.decodeFrame(header, bitstream)
            } catch (ex: Exception) {
                ex.printStackTrace()
                break
            }
            bitstream.closeFrame()
            out.write(outputBuffer.buffer, 0, outputBuffer.reset())
        }

        val data = out.toByteArray()
        if (data.isEmpty()) return EMPTY_BUFFER

        val buffer = MemoryUtil.memAlloc(data.size)
        buffer.put(data)
        buffer.flip()
        return buffer
    }

    override fun close() {
        bitstream.close()
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocate(0)
    }
}