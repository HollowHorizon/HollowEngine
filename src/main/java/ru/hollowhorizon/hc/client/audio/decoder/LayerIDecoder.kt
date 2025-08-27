package ru.hollowhorizon.hc.client.audio.decoder

import ru.hollowhorizon.hc.client.audio.formats.Mp3Format

open class LayerIDecoder : FrameDecoder {
    protected lateinit var stream: Bitstream
    protected lateinit var header: Header
    protected var crc = Crc16()

    private lateinit var filter1: SynthesisFilter
    private lateinit var filter2: SynthesisFilter
    protected lateinit var buffer: OutputBuffer
    private var whichChannels: Int = 0

    @JvmField
    protected var mode: Int = 0

    @JvmField
    protected var numSubbands: Int = 0
    protected lateinit var subbands: Array<Subband?>

    fun create(
        stream0: Bitstream,
        header0: Header,
        filtera: SynthesisFilter,
        filterb: SynthesisFilter,
        buffer0: OutputBuffer,
        whichCh0: Int,
    ) {
        stream = stream0
        header = header0
        filter1 = filtera
        filter2 = filterb
        buffer = buffer0
        whichChannels = whichCh0
    }

    @Throws(DecoderException::class)
    override fun decodeFrame() {
        numSubbands = header.numberOfSubbands()
        subbands = arrayOfNulls(32)
        mode = header.mode()

        createSubbands()

        readAllocation()
        readScaleFactorSelection()

        readScaleFactors()

        readSampleData()
    }

    protected open fun createSubbands() {
        var i: Int
        when (mode) {
            Header.SINGLE_CHANNEL -> {
                i = 0
                while (i < numSubbands) {
                    subbands[i] = SubbandLayer1(i)
                    ++i
                }
            }

            Header.JOINT_STEREO -> {
                i = 0
                while (i < header.intensityStereoBound()) {
                    subbands[i] = SubbandLayer1Stereo(i)
                    ++i
                }
                while (i < numSubbands) {
                    subbands[i] = SubbandLayer1IntensityStereo(i)
                    ++i
                }
            }

            else -> {
                i = 0
                while (i < numSubbands) {
                    subbands[i] = SubbandLayer1Stereo(i)
                    ++i
                }
            }
        }
    }

    @Throws(DecoderException::class)
    protected fun readAllocation() {
        // start to read audio data:
        for (i in 0 until numSubbands) subbands[i]!!.readAllocation(stream, header, crc)
    }

    protected open fun readScaleFactorSelection() {
        // scale factor selection not present for layer I.
    }

    private fun readScaleFactors() {
        for (i in 0 until numSubbands) subbands[i]!!.readScalefactor(stream, header)
    }

    private fun readSampleData() {
        var readReady = false
        var writeReady = false
        val mode = header.mode()
        var i: Int
        do {
            i = 0
            while (i < numSubbands) {
                readReady = subbands[i]!!.readSampleData(stream)
                ++i
            }
            do {
                i = 0
                while (i < numSubbands) {
                    writeReady = subbands[i]!!.putNextSample(whichChannels, filter1, filter2)
                    ++i
                }

                filter1.calculatePcmSamples(buffer)
                if (whichChannels == OutputChannels.BOTH_CHANNELS && mode != Header.SINGLE_CHANNEL) filter2.calculatePcmSamples(
                    buffer
                )
            } while (!writeReady)
        } while (!readReady)
    }

    /**
     * Abstract base class for subband classes of layer I and II
     */
    abstract class Subband {
        @Throws(DecoderException::class)
        abstract fun readAllocation(stream: Bitstream, header: Header, crc: Crc16)

        abstract fun readScalefactor(stream: Bitstream, header: Header)

        abstract fun readSampleData(stream: Bitstream): Boolean

        abstract fun putNextSample(channels: Int, filter1: SynthesisFilter, filter2: SynthesisFilter): Boolean
    }

    /**
     * Class for layer I subbands in single channel mode. Used for single channel mode and in derived class for intensity stereo
     * mode
     */
    internal open class SubbandLayer1(protected var subbandnumber: Int) : Subband() {
        private var samplenumber: Int = 0
        protected var allocation: Int = 0
        protected var scalefactor: Float = 0f
        protected var samplelength: Int = 0
        protected var sample: Float = 0f
        protected var factor: Float = 0f
        protected var offset: Float = 0f

        /**
         *
         */
        @Throws(DecoderException::class)
        override fun readAllocation(stream: Bitstream, header: Header, crc: Crc16) {
            if ((stream.get_bits(4).also { allocation = it }) == 15)
                throw DecoderException(Mp3Format.ILLEGAL_SUBBAND_ALLOCATION, null)

            crc.addBits(allocation, 4)
            if (allocation != 0) {
                samplelength = allocation + 1
                factor = table_factor[allocation]
                offset = table_offset[allocation]
            }
        }

        override fun readScalefactor(stream: Bitstream, header: Header) {
            if (allocation != 0) scalefactor = scalefactors[stream.get_bits(6)]
        }

        override fun readSampleData(stream: Bitstream): Boolean {
            if (allocation != 0) sample = stream.get_bits(samplelength).toFloat()
            if (++samplenumber == 12) {
                samplenumber = 0
                return true
            }
            return false
        }

        /**
         *
         */
        override fun putNextSample(channels: Int, filter1: SynthesisFilter, filter2: SynthesisFilter): Boolean {
            if (allocation != 0 && channels != OutputChannels.RIGHT_CHANNEL) {
                val scaledSample = (sample * factor + offset) * scalefactor
                filter1.inputSample(scaledSample, subbandnumber)
            }
            return true
        }

        companion object {
            // Factors and offsets for sample requantization
            val table_factor: FloatArray = floatArrayOf(
                0.0f,
                1.0f / 2.0f * 4.0f / 3.0f,
                1.0f / 4.0f * 8.0f / 7.0f,
                1.0f / 8.0f * 16.0f / 15.0f,
                1.0f / 16.0f * 32.0f / 31.0f,
                1.0f / 32.0f * 64.0f / 63.0f,
                1.0f / 64.0f * 128.0f / 127.0f,
                1.0f / 128.0f * 256.0f / 255.0f,
                1.0f / 256.0f * 512.0f / 511.0f,
                1.0f / 512.0f * 1024.0f / 1023.0f,
                1.0f / 1024.0f * 2048.0f / 2047.0f,
                1.0f / 2048.0f * 4096.0f / 4095.0f,
                1.0f / 4096.0f * 8192.0f / 8191.0f,
                1.0f / 8192.0f * 16384.0f / 16383.0f,
                1.0f / 16384.0f * 32768.0f / 32767.0f
            )

            val table_offset: FloatArray = floatArrayOf(
                0.0f,
                (1.0f / 2.0f - 1.0f) * 4.0f / 3.0f,
                (1.0f / 4.0f - 1.0f) * 8.0f / 7.0f,
                (1.0f / 8.0f - 1.0f) * 16.0f / 15.0f,
                (1.0f / 16.0f - 1.0f) * 32.0f / 31.0f,
                (1.0f / 32.0f - 1.0f) * 64.0f / 63.0f,
                (1.0f / 64.0f - 1.0f) * 128.0f / 127.0f,
                (1.0f / 128.0f - 1.0f) * 256.0f / 255.0f,
                (1.0f / 256.0f - 1.0f) * 512.0f / 511.0f,
                (1.0f / 512.0f - 1.0f) * 1024.0f / 1023.0f,
                (1.0f / 1024.0f - 1.0f) * 2048.0f / 2047.0f,
                (1.0f / 2048.0f - 1.0f) * 4096.0f / 4095.0f,
                (1.0f / 4096.0f - 1.0f) * 8192.0f / 8191.0f,
                (1.0f / 8192.0f - 1.0f) * 16384.0f / 16383.0f,
                (1.0f / 16384.0f - 1.0f) * 32768.0f / 32767.0f
            )
        }
    }

    internal open class SubbandLayer1IntensityStereo(subbandnumber: Int) : SubbandLayer1(subbandnumber) {
        private var channel2Scalefactor: Float = 0f

        override fun readScalefactor(stream: Bitstream, header: Header) {
            if (allocation != 0) {
                scalefactor = scalefactors[stream.get_bits(6)]
                channel2Scalefactor = scalefactors[stream.get_bits(6)]
            }
        }

        override fun putNextSample(channels: Int, filter1: SynthesisFilter, filter2: SynthesisFilter): Boolean {
            if (allocation != 0) {
                sample = sample * factor + offset
                when (channels) {
                    OutputChannels.BOTH_CHANNELS -> {
                        val sample1 = sample * scalefactor
                        val sample2 = sample * channel2Scalefactor
                        filter1.inputSample(sample1, subbandnumber)
                        filter2.inputSample(sample2, subbandnumber)
                    }

                    OutputChannels.LEFT_CHANNEL -> {
                        val sample1 = sample * scalefactor
                        filter1.inputSample(sample1, subbandnumber)
                    }

                    else -> {
                        val sample2 = sample * channel2Scalefactor
                        filter1.inputSample(sample2, subbandnumber)
                    }
                }
            }
            return true
        }
    }

    /**
     * Class for layer I subbands in stereo mode.
     */
    internal open class SubbandLayer1Stereo
    /**
     * Constructor
     */
        (subbandnumber: Int) : SubbandLayer1(subbandnumber) {
        private var channel2Allocation: Int = 0
        private var channel2Scalefactor: Float = 0f
        private var channel2Samplelength: Int = 0
        private var channel2Sample: Float = 0f
        private var channel2Factor: Float = 0f
        private var channel2Offset: Float = 0f

        /**
         *
         */
        @Throws(DecoderException::class)
        override fun readAllocation(stream: Bitstream, header: Header, crc: Crc16) {
            allocation = stream.get_bits(4)
            channel2Allocation = stream.get_bits(4)
            crc.addBits(allocation, 4)
            crc.addBits(channel2Allocation, 4)
            if (allocation != 0) {
                samplelength = allocation + 1
                factor = table_factor[allocation]
                offset = table_offset[allocation]
            }
            if (channel2Allocation != 0) {
                channel2Samplelength = channel2Allocation + 1
                channel2Factor = table_factor[channel2Allocation]
                channel2Offset = table_offset[channel2Allocation]
            }
        }

        /**
         *
         */
        override fun readScalefactor(stream: Bitstream, header: Header) {
            if (allocation != 0) scalefactor = scalefactors[stream.get_bits(6)]
            if (channel2Allocation != 0) channel2Scalefactor = scalefactors[stream.get_bits(6)]
        }

        /**
         *
         */
        override fun readSampleData(stream: Bitstream): Boolean {
            val returnvalue = super.readSampleData(stream)
            if (channel2Allocation != 0) channel2Sample = stream.get_bits(channel2Samplelength).toFloat()
            return returnvalue
        }

        /**
         *
         */
        override fun putNextSample(channels: Int, filter1: SynthesisFilter, filter2: SynthesisFilter): Boolean {
            super.putNextSample(channels, filter1, filter2)
            if (channel2Allocation != 0 && channels != OutputChannels.LEFT_CHANNEL) {
                val sample2 = (channel2Sample * channel2Factor + channel2Offset) * channel2Scalefactor
                if (channels == OutputChannels.BOTH_CHANNELS) filter2.inputSample(sample2, subbandnumber)
                else filter1.inputSample(sample2, subbandnumber)
            }
            return true
        }
    }

    companion object {
        @JvmField
        val scalefactors: FloatArray = floatArrayOf(
            2.00000000000000f,
            1.587401f,
            1.2599211f,
            1.00000000000000f,
            0.7937005f,
            0.62996054f,
            0.50000000000000f,
            0.39685026f,
            0.31498027f,
            0.25000000000000f,
            0.19842513f,
            0.15749013f,
            0.12500000000000f,
            0.099212565f,
            0.07874507f,
            0.06250000000000f,
            0.049606282f,
            0.039372534f,
            0.03125000000000f,
            0.024803141f,
            0.019686267f,
            0.01562500000000f,
            0.012401571f,
            0.009843133f,
            0.00781250000000f,
            0.0062007853f,
            0.0049215667f,
            0.00390625000000f,
            0.0031003926f,
            0.0024607833f,
            0.00195312500000f,
            0.0015501963f,
            0.0012303917f,
            0.00097656250000f,
            7.7509816E-4f,
            6.1519584E-4f,
            0.00048828125000f,
            3.8754908E-4f,
            3.0759792E-4f,
            2.4414062E-4f,
            1.9377454E-4f,
            1.5379896E-4f,
            1.2207031E-4f,
            9.688727E-5f,
            7.689948E-5f,
            6.1035156E-5f,
            4.8443635E-5f,
            3.844974E-5f,
            3.0517578E-5f,
            2.4221818E-5f,
            1.922487E-5f,
            1.5258789E-5f,
            1.2110909E-5f,
            9.612435E-6f,
            7.6293945E-6f,
            6.0554544E-6f,
            4.8062175E-6f,
            3.8146973E-6f,
            3.0277272E-6f,
            2.4031087E-6f,
            1.9073486E-6f,
            1.5138636E-6f,
            1.2015544E-6f,
            0.00000000000000f
        )
    }
}
