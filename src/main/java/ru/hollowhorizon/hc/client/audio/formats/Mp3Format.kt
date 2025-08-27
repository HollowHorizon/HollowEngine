package ru.hollowhorizon.hc.client.audio.formats

import org.lwjgl.system.MemoryUtil
import ru.hollowhorizon.hc.client.audio.Wave
import ru.hollowhorizon.hc.client.audio.decoder.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteOrder


object Mp3Format {
    private const val DECODER_ERROR: Int = 0x200
    const val UNKNOWN_ERROR: Int = DECODER_ERROR
    const val UNSUPPORTED_LAYER: Int = DECODER_ERROR + 1
    const val ILLEGAL_SUBBAND_ALLOCATION: Int = DECODER_ERROR + 2

    class Decode {
        lateinit var output: OutputBuffer
        private lateinit var filter1: SynthesisFilter
        private lateinit var filter2: SynthesisFilter

        private var l3decoder: LayerIIIDecoder? = null
        private var l2decoder: LayerIIDecoder? = null
        private var l1decoder: LayerIDecoder? = null

        private var initialized = false

        fun decodeFrame(header: Header, stream: Bitstream) {
            if (!initialized) initialize()
            val layer: Int = header.layer()
            val decoder = retrieveDecoder(header, stream, layer)
            decoder.decodeFrame()
        }

        private fun retrieveDecoder(header: Header, stream: Bitstream, layer: Int): FrameDecoder {
            var decoder: FrameDecoder? = null

            when (layer) {
                3 -> {
                    if (l3decoder == null) l3decoder =
                        LayerIIIDecoder(stream, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS)

                    decoder = l3decoder
                }

                2 -> {
                    if (l2decoder == null) {
                        l2decoder = LayerIIDecoder()
                        l2decoder!!.create(stream, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS)
                    }
                    decoder = l2decoder
                }

                1 -> {
                    if (l1decoder == null) {
                        l1decoder = LayerIDecoder()
                        l1decoder!!.create(stream, header, filter1, filter2, output, OutputChannels.BOTH_CHANNELS)
                    }
                    decoder = l1decoder
                }
            }
            if (decoder == null) throw DecoderException(UNSUPPORTED_LAYER, null)

            return decoder
        }

        private fun initialize() {
            val scalefactor = 32700.0f

            filter1 = SynthesisFilter(0, scalefactor)
            filter2 = SynthesisFilter(1, scalefactor)

            initialized = true
        }
    }

    fun read(stream: InputStream): Wave {
        val bitstream = Bitstream(stream)
        var header: Header? = bitstream.readFrame()
            ?: throw IllegalStateException("Empty mp3 file!")
        val channels = if (header?.mode() == Header.SINGLE_CHANNEL) 1 else 2
        val rate: Int = header?.sampleRate ?: -1
        val outputBuffer = OutputBuffer(channels, false)
        val buffer = ByteArrayOutputStream(4096)
        val decoder = Decode()
        decoder.output = outputBuffer
        while (true) {
            header = bitstream.readFrame()
            if (header == null) break
            try {
                decoder.decodeFrame(header, bitstream)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            bitstream.closeFrame()
            buffer.write(outputBuffer.buffer, 0, outputBuffer.reset())
        }
        bitstream.close()

        val bytes = buffer.size() - (buffer.size() % (if (channels > 1) 4 else 2))
        val output = MemoryUtil.memAlloc(bytes)
        output.order(ByteOrder.nativeOrder())
        output.put(buffer.toByteArray(), 0, bytes)
        output.flip()
        return Wave(channels, rate, 16, buffer.toByteArray())
    }
}