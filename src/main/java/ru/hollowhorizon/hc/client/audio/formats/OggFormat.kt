package ru.hollowhorizon.hc.client.audio.formats

import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import ru.hollowhorizon.hc.client.audio.Wave
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

object OggFormat {
    @Throws(IOException::class)
    fun read(stream: InputStream): Wave {
        val info = STBVorbisInfo.malloc()
        val stack = MemoryStack.stackPush()

        try {
            val buffer: ByteBuffer = readByteBuffer(stream)
            val error = stack.mallocInt(1)
            val decoder = STBVorbis.stb_vorbis_open_memory(buffer, error, null)

            if (decoder == 0L) throw IllegalArgumentException("Failed to read Ogg audio... Error code: " + error.get())

            STBVorbis.stb_vorbis_get_info(decoder, info)
            val channels = info.channels()
            val size = STBVorbis.stb_vorbis_stream_length_in_samples(decoder) * channels
            val samples = MemoryUtil.memAllocShort(size)
            STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, samples)
            STBVorbis.stb_vorbis_close(decoder)
            val byteBuffer = MemoryUtil.memAlloc(size * 2)

            for (i in 0 until samples.limit()) byteBuffer.putShort(samples.get())

            byteBuffer.flip()
            val finalBytes = ByteArray(byteBuffer.limit())

            for (i in 0 until byteBuffer.limit()) finalBytes[i] = byteBuffer.get()

            val wave = Wave(channels, info.sample_rate(), 16, finalBytes)
            MemoryUtil.memFree(buffer)
            MemoryUtil.memFree(samples)
            MemoryUtil.memFree(byteBuffer)

            return wave
        } catch (e: Exception) {
            stack.close()
            info.close()
            throw IllegalArgumentException("Failed to read Ogg audio...", e)
        }
    }

    fun readByteBuffer(stream: InputStream): ByteBuffer {
        val bytes: ByteArray = stream.readBytes()
        val buffer = MemoryUtil.memAlloc(bytes.size)
        buffer.put(bytes)
        buffer.flip()
        return buffer
    }
}