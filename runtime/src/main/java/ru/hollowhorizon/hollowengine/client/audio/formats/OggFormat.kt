package ru.hollowhorizon.hollowengine.client.audio.formats

import org.lwjgl.stb.STBVorbis
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import ru.hollowhorizon.hollowengine.client.audio.Wave
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

object OggFormat {
    @Throws(IOException::class)
    fun read(stream: InputStream): Wave {
        val encoded = stream.readBytes().toDirectBuffer()
        var samples: ShortBuffer? = null
        var decoder = 0L
        val info = STBVorbisInfo.malloc()

        try {
            MemoryStack.stackPush().use { stack ->
                val error = stack.mallocInt(1)
                decoder = STBVorbis.stb_vorbis_open_memory(encoded, error, null)
                if (decoder == 0L) throw IOException("Failed to read Ogg audio. Error code: ${error.get(0)}")
            }

            STBVorbis.stb_vorbis_get_info(decoder, info)
            val channels = info.channels()
            val capacity = STBVorbis.stb_vorbis_stream_length_in_samples(decoder) * channels
            val buffer = MemoryUtil.memAllocShort(capacity).also { samples = it }
            val decoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, buffer)

            return Wave(channels, info.sample_rate(), 16, buffer.toPcm16(decoded * channels))
        } finally {
            if (decoder != 0L) STBVorbis.stb_vorbis_close(decoder)
            info.free()
            samples?.let(MemoryUtil::memFree)
            MemoryUtil.memFree(encoded)
        }
    }

    private fun ByteArray.toDirectBuffer(): ByteBuffer = MemoryUtil.memAlloc(size).put(this).flip()

    private fun ShortBuffer.toPcm16(count: Int): ByteArray {
        val data = ByteArray(count * 2)
        val out = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) out.putShort(get(i))
        return data
    }
}
