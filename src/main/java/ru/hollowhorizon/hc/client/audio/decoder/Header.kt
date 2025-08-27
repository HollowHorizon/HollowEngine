package ru.hollowhorizon.hc.client.audio.decoder

/**
 * Class for extracting information from a frame header.
 */
class Header internal constructor() {
    private var hLayer = 0
    private var hProtectionBit = 0
    private var hBitrateIndex = 0
    private var hPaddingBit = 0
    var modeExtension = 0
        private set
    private var hVersion = 0
    private var hMode = 0
    private var hSampleFrequency = 0
    private var hNumberOfSubbands = 0
    private var hIntensityStereoBound = 0
    private var hCopyright = false
    private var hOriginal = false
    private val hVbrTimePerFrame = doubleArrayOf(-1.0, 384.0, 1152.0, 1152.0)
    private var hVbr = false
    private var hVbrFrames = 0
    private var hVbrScale = 0
    private var hVbrBytes = 0
    private lateinit var hVbrToc: ByteArray

    private var syncmode = Bitstream.INITIAL_SYNC
    private var crc: Crc16? = null

    private var checksum: Short = 0
    private var framesize: Int = 0
    var slots: Int = 0
        private set
    private var syncHeader: Int = -1

    override fun toString(): String {
        val buffer = StringBuilder(200)
        buffer.append("Layer ")
        buffer.append(layerString())
        buffer.append(" frame ")
        buffer.append(modeString())
        buffer.append(' ')
        buffer.append(versionString())
        if (!checksums()) buffer.append(" no")
        buffer.append(" checksums")
        buffer.append(' ')
        buffer.append(sampleFrequencyString())
        buffer.append(',')
        buffer.append(' ')
        buffer.append(bitrateString())

        return buffer.toString()
    }

    @Throws(BitstreamException::class)
    fun readHeader(stream: Bitstream, crcp: Array<Crc16?>) {
        var headerstring: Int
        var channelBitrate: Int
        var sync = false
        do {
            headerstring = stream.syncHeader(syncmode)
            syncHeader = headerstring // E.B
            if (syncmode == Bitstream.INITIAL_SYNC) {
                hVersion = headerstring ushr 19 and 1
                if ((headerstring ushr 20 and 1) == 0) // SZD: MPEG2.5 detection
                    if (hVersion == MPEG2_LSF) hVersion = MPEG25_LSF
                    else throw stream.newBitstreamException(Bitstream.UNKNOWN_ERROR)
                if (((headerstring ushr 10 and 3).also {
                        hSampleFrequency = it
                    }) == 3) throw stream.newBitstreamException(
                    Bitstream.UNKNOWN_ERROR
                )
            }
            hLayer = 4 - (headerstring ushr 17) and 3
            hProtectionBit = headerstring ushr 16 and 1
            hBitrateIndex = headerstring ushr 12 and 0xF
            hPaddingBit = headerstring ushr 9 and 1
            hMode = headerstring ushr 6 and 3
            modeExtension = headerstring ushr 4 and 3
            hIntensityStereoBound = if (hMode == JOINT_STEREO) (modeExtension shl 2) + 4
            else 0 // should never be used

            if ((headerstring ushr 3 and 1) == 1) hCopyright = true
            if ((headerstring ushr 2 and 1) == 1) hOriginal = true
            // calculate number of subbands:
            if (hLayer == 1) hNumberOfSubbands = 32
            else {
                channelBitrate = hBitrateIndex
                // calculate bitrate per channel:
                if (hMode != SINGLE_CHANNEL) if (channelBitrate == 4) channelBitrate = 1
                else channelBitrate -= 4
                hNumberOfSubbands =
                    if (channelBitrate == 1 || channelBitrate == 2) if (hSampleFrequency == THIRTYTWO) 12
                    else 8
                    else if (hSampleFrequency == FOURTYEIGHT || channelBitrate in 3..5) 27
                    else 30
            }
            if (hIntensityStereoBound > hNumberOfSubbands) hIntensityStereoBound = hNumberOfSubbands
            // calculate framesize and nSlots
            calculateFramesize()
            // read framedata:
            val framesizeloaded = stream.read_frame_data(framesize)
            if (framesize >= 0 && framesizeloaded != framesize) // Data loaded does not match to expected framesize,
            // it might be an ID3v1 TAG. (Fix 11/17/04).
                throw stream.newBitstreamException(Bitstream.INVALIDFRAME)
            if (stream.isSyncCurrentPosition(syncmode.toInt())) {
                if (syncmode == Bitstream.INITIAL_SYNC) {
                    syncmode = Bitstream.STRICT_SYNC
                    stream.set_syncword(headerstring and -0x7f340)
                }
                sync = true
            } else stream.unreadFrame()
        } while (!sync)
        stream.parse_frame()
        if (hProtectionBit == 0) {
            // frame contains a crc checksum
            checksum = stream.get_bits(16).toShort()
            if (crc == null) crc = Crc16()
            crc!!.addBits(headerstring, 16)
            crcp[0] = crc
        } else crcp[0] = null/*
         * if (offset == null) { int max = max_number_of_frames(stream); offset = new int[max]; for(int i=0; i<max; i++)
         * offset[i] = 0; } // E.B : Investigate more int cf = stream.current_frame(); int lf = stream.last_frame(); if ((cf > 0)
         * && (cf == lf)) { offset[cf] = offset[cf-1] + h_padding_bit; } else { offset[0] = h_padding_bit; }
         */
    }

    fun parseVBR(firstframe: ByteArray) {
        val xing = "Xing"
        val tmp = ByteArray(4)
        var offset = if (hVersion == MPEG1) {
            if (hMode == SINGLE_CHANNEL) 21 - 4
            else 36 - 4
        } else if (hMode == SINGLE_CHANNEL) 13 - 4
        else 21 - 4
        try {
            System.arraycopy(firstframe, offset, tmp, 0, 4)
            // Is "Xing" ?
            if (xing == String(tmp)) {
                // Yes.
                hVbr = true
                hVbrFrames = -1
                hVbrBytes = -1
                hVbrScale = -1
                hVbrToc = ByteArray(100)

                var length = 4
                // Read flags.
                val flags = ByteArray(4)
                System.arraycopy(firstframe, offset + length, flags, 0, flags.size)
                length += flags.size
                // Read number of frames (if available).
                if ((flags[3].toInt() and 1.toByte().toInt()) != 0) {
                    System.arraycopy(firstframe, offset + length, tmp, 0, tmp.size)
                    hVbrFrames =
                        tmp[0].toInt() shl 24 and -0x1000000 or (tmp[1].toInt() shl 16 and 0x00FF0000) or (tmp[2].toInt() shl 8 and 0x0000FF00) or (tmp[3].toInt() and 0x000000FF)
                    length += 4
                }
                // Read size (if available).
                if ((flags[3].toInt() and (1 shl 1).toByte().toInt()) != 0) {
                    System.arraycopy(firstframe, offset + length, tmp, 0, tmp.size)
                    hVbrBytes =
                        tmp[0].toInt() shl 24 and -0x1000000 or (tmp[1].toInt() shl 16 and 0x00FF0000) or (tmp[2].toInt() shl 8 and 0x0000FF00) or (tmp[3].toInt() and 0x000000FF)
                    length += 4
                }
                // Read TOC (if available).
                if ((flags[3].toInt() and (1 shl 2).toByte().toInt()) != 0) {
                    System.arraycopy(firstframe, offset + length, hVbrToc, 0, hVbrToc.size)
                    length += hVbrToc.size
                }
                // Read scale (if available).
                if ((flags[3].toInt() and (1 shl 3).toByte().toInt()) != 0) {
                    System.arraycopy(firstframe, offset + length, tmp, 0, tmp.size)
                    hVbrScale =
                        tmp[0].toInt() shl 24 and -0x1000000 or (tmp[1].toInt() shl 16 and 0x00FF0000) or (tmp[2].toInt() shl 8 and 0x0000FF00) or (tmp[3].toInt() and 0x000000FF)
                    length += 4
                }
                // System.out.println("VBR:"+xing+" Frames:"+ h_vbr_frames +" Size:"+h_vbr_bytes);
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw BitstreamException("XingVBRHeader Corrupted", e)
        }

        // Trying VBRI header.
        val vbri = "VBRI"
        offset = 36 - 4
        try {
            System.arraycopy(firstframe, offset, tmp, 0, 4)
            // Is "VBRI" ?
            if (vbri == String(tmp)) {
                // Yes.
                hVbr = true
                hVbrFrames = -1
                hVbrBytes = -1
                hVbrScale = -1
                hVbrToc = ByteArray(100)
                // Bytes.
                var length = 4 + 6
                System.arraycopy(firstframe, offset + length, tmp, 0, tmp.size)
                hVbrBytes =
                    tmp[0].toInt() shl 24 and -0x1000000 or (tmp[1].toInt() shl 16 and 0x00FF0000) or (tmp[2].toInt() shl 8 and 0x0000FF00) or (tmp[3].toInt() and 0x000000FF)
                length += 4
                // Frames.
                System.arraycopy(firstframe, offset + length, tmp, 0, tmp.size)
                hVbrFrames =
                    tmp[0].toInt() shl 24 and -0x1000000 or (tmp[1].toInt() shl 16 and 0x00FF0000) or (tmp[2].toInt() shl 8 and 0x0000FF00) or (tmp[3].toInt() and 0x000000FF)
                length += 4
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            throw BitstreamException("VBRIVBRHeader Corrupted", e)
        }
    }

    fun checksumOk() = checksum == crc?.checksum()

    fun version() = hVersion

    /**
     * Returns Layer ID.
     */
    fun layer() = hLayer

    fun bitrateIndex() = hBitrateIndex

    fun sampleFrequency() = hSampleFrequency

    private fun frequency() = frequencies[hVersion][hSampleFrequency]

    fun mode() = hMode

    private fun checksums() = hProtectionBit == 0

    fun copyright() = hCopyright

    private fun calculateFramesize(): Int {
        if (hLayer == 1) {
            framesize = 12 * bitrates[hVersion][0][hBitrateIndex] / frequencies[hVersion][hSampleFrequency]
            if (hPaddingBit != 0) framesize++
            framesize = framesize shl 2 // one slot is 4 bytes long
            slots = 0
        } else {
            framesize = 144 * bitrates[hVersion][hLayer - 1][hBitrateIndex] / frequencies[hVersion][hSampleFrequency]
            if (hVersion == MPEG2_LSF || hVersion == MPEG25_LSF) framesize = framesize shr 1 // SZD

            if (hPaddingBit != 0) framesize++
            // Layer III slots
            slots = if (hLayer == 3) {
                if (hVersion == MPEG1) (framesize - (if (hMode == SINGLE_CHANNEL) 17 else 32) // side info size
                        - (if (hProtectionBit != 0) 0 else 2) // CRC size
                        - 4) // header size
                else (framesize - (if (hMode == SINGLE_CHANNEL) 9 else 17) // side info size
                        - (if (hProtectionBit != 0) 0 else 2) // CRC size
                        - 4) // header size
            } else 0
        }
        framesize -= 4 // subtract header size
        return framesize
    }

    private fun msPerFrame(): Float {
        if (hVbr) {
            var tpf = hVbrTimePerFrame[layer()] / frequency()
            if (hVersion == MPEG2_LSF || hVersion == MPEG25_LSF) tpf /= 2.0
            return (tpf * 1000).toFloat()
        } else {
            return msPerFrameArray[hLayer - 1][hSampleFrequency]
        }
    }


    private fun layerString(): String? {
        when (hLayer) {
            1 -> return "I"
            2 -> return "II"
            3 -> return "III"
        }
        return null
    }

    /**
     * Return Bitrate.
     * @return bitrate in bps
     */
    private fun bitrateString(): String {
        return if (hVbr) (bitrate() / 1000).toString() + " kb/s"
        else bitrate_str[hVersion][hLayer - 1][hBitrateIndex]
    }

    /**
     * Return Bitrate.
     * @return bitrate in bps and average bitrate for VBR header
     */
    private fun bitrate(): Int {
        return if (hVbr) (hVbrBytes * 8 / (msPerFrame() * hVbrFrames)).toInt() * 1000
        else bitrates[hVersion][hLayer - 1][hBitrateIndex]
    }

    private fun sampleFrequencyString(): String? {
        when (hSampleFrequency) {
            THIRTYTWO -> return when (hVersion) {
                MPEG1 -> "32 kHz"
                MPEG2_LSF -> "16 kHz"
                else -> "8 kHz"
            }

            FOURTYFOUR_POINT_ONE -> return when (hVersion) {
                MPEG1 -> "44.1 kHz"
                MPEG2_LSF -> "22.05 kHz"
                else -> "11.025 kHz"
            }

            FOURTYEIGHT -> return when (hVersion) {
                MPEG1 -> "48 kHz"
                MPEG2_LSF -> "24 kHz"
                else -> "12 kHz"
            }
        }
        return null
    }

    val sampleRate: Int
        get() {
            when (hSampleFrequency) {
                THIRTYTWO -> return when (hVersion) {
                    MPEG1 -> 32000
                    MPEG2_LSF -> 16000
                    else -> 8000
                }

                FOURTYFOUR_POINT_ONE -> return when (hVersion) {
                    MPEG1 -> 44100
                    MPEG2_LSF -> 22050
                    else -> 11025
                }

                FOURTYEIGHT -> return when (hVersion) {
                    MPEG1 -> 48000
                    MPEG2_LSF -> 24000
                    else -> 12000
                }
            }
            return 0
        }

    /**
     * Returns Mode.
     */
    private fun modeString(): String? {
        when (hMode) {
            STEREO -> return "Stereo"
            JOINT_STEREO -> return "Joint stereo"
            DUAL_CHANNEL -> return "Dual channel"
            SINGLE_CHANNEL -> return "Single channel"
        }
        return null
    }

    /**
     * Returns Version.
     * @return MPEG-1 or MPEG-2 LSF or MPEG-2.5 LSF
     */
    private fun versionString(): String? {
        when (hVersion) {
            MPEG1 -> return "MPEG-1"
            MPEG2_LSF -> return "MPEG-2 LSF"
            MPEG25_LSF -> return "MPEG-2.5 LSF"
        }
        return null
    }

    /**
     * Returns the number of subbands in the current frame.
     * @return number of subbands
     */
    fun numberOfSubbands(): Int {
        return hNumberOfSubbands
    }

    fun intensityStereoBound(): Int {
        return hIntensityStereoBound
    }

    companion object {
        val frequencies: Array<IntArray> = arrayOf(
            intArrayOf(22050, 24000, 16000, 1), intArrayOf(44100, 48000, 32000, 1), intArrayOf(11025, 12000, 8000, 1)
        )

        val msPerFrameArray = arrayOf(
            floatArrayOf(8.707483f, 8.0f, 12.0f),
            floatArrayOf(26.12245f, 24.0f, 36.0f),
            floatArrayOf(26.12245f, 24.0f, 36.0f)
        )

        const val MPEG2_LSF: Int = 0
        const val MPEG25_LSF: Int = 2
        const val MPEG1: Int = 1
        const val STEREO: Int = 0
        const val JOINT_STEREO: Int = 1
        const val DUAL_CHANNEL: Int = 2
        const val SINGLE_CHANNEL: Int = 3
        const val FOURTYFOUR_POINT_ONE: Int = 0
        const val FOURTYEIGHT: Int = 1
        const val THIRTYTWO: Int = 2

        private val bitrates = arrayOf(
            arrayOf(
                intArrayOf(
                    0,
                    32000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    144000,
                    160000,
                    176000,
                    192000,
                    224000,
                    256000,
                    0
                ), intArrayOf(
                    0,
                    8000,
                    16000,
                    24000,
                    32000,
                    40000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    144000,
                    160000,
                    0
                ), intArrayOf(
                    0,
                    8000,
                    16000,
                    24000,
                    32000,
                    40000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    144000,
                    160000,
                    0
                )
            ),

            arrayOf(
                intArrayOf(
                    0,
                    32000,
                    64000,
                    96000,
                    128000,
                    160000,
                    192000,
                    224000,
                    256000,
                    288000,
                    320000,
                    352000,
                    384000,
                    416000,
                    448000,
                    0
                ), intArrayOf(
                    0,
                    32000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    160000,
                    192000,
                    224000,
                    256000,
                    320000,
                    384000,
                    0
                ), intArrayOf(
                    0,
                    32000,
                    40000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    160000,
                    192000,
                    224000,
                    256000,
                    320000,
                    0
                )
            ),
            arrayOf(
                intArrayOf(
                    0,
                    32000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    144000,
                    160000,
                    176000,
                    192000,
                    224000,
                    256000,
                    0
                ), intArrayOf(
                    0,
                    8000,
                    16000,
                    24000,
                    32000,
                    40000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    144000,
                    160000,
                    0
                ), intArrayOf(
                    0,
                    8000,
                    16000,
                    24000,
                    32000,
                    40000,
                    48000,
                    56000,
                    64000,
                    80000,
                    96000,
                    112000,
                    128000,
                    144000,
                    160000,
                    0
                )
            ),

            )

        private val bitrate_str = arrayOf(
            arrayOf(
                arrayOf(
                    "free format",
                    "32 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "144 kbit/s",
                    "160 kbit/s",
                    "176 kbit/s",
                    "192 kbit/s",
                    "224 kbit/s",
                    "256 kbit/s",
                    "forbidden"
                ), arrayOf(
                    "free format",
                    "8 kbit/s",
                    "16 kbit/s",
                    "24 kbit/s",
                    "32 kbit/s",
                    "40 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "144 kbit/s",
                    "160 kbit/s",
                    "forbidden"
                ), arrayOf(
                    "free format",
                    "8 kbit/s",
                    "16 kbit/s",
                    "24 kbit/s",
                    "32 kbit/s",
                    "40 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "144 kbit/s",
                    "160 kbit/s",
                    "forbidden"
                )
            ),

            arrayOf(
                arrayOf(
                    "free format",
                    "32 kbit/s",
                    "64 kbit/s",
                    "96 kbit/s",
                    "128 kbit/s",
                    "160 kbit/s",
                    "192 kbit/s",
                    "224 kbit/s",
                    "256 kbit/s",
                    "288 kbit/s",
                    "320 kbit/s",
                    "352 kbit/s",
                    "384 kbit/s",
                    "416 kbit/s",
                    "448 kbit/s",
                    "forbidden"
                ), arrayOf(
                    "free format",
                    "32 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "160 kbit/s",
                    "192 kbit/s",
                    "224 kbit/s",
                    "256 kbit/s",
                    "320 kbit/s",
                    "384 kbit/s",
                    "forbidden"
                ), arrayOf(
                    "free format",
                    "32 kbit/s",
                    "40 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "160 kbit/s",
                    "192 kbit/s",
                    "224 kbit/s",
                    "256 kbit/s",
                    "320 kbit/s",
                    "forbidden"
                )
            ),  // SZD: MPEG2.5
            arrayOf(
                arrayOf(
                    "free format",
                    "32 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "144 kbit/s",
                    "160 kbit/s",
                    "176 kbit/s",
                    "192 kbit/s",
                    "224 kbit/s",
                    "256 kbit/s",
                    "forbidden"
                ), arrayOf(
                    "free format",
                    "8 kbit/s",
                    "16 kbit/s",
                    "24 kbit/s",
                    "32 kbit/s",
                    "40 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "144 kbit/s",
                    "160 kbit/s",
                    "forbidden"
                ), arrayOf(
                    "free format",
                    "8 kbit/s",
                    "16 kbit/s",
                    "24 kbit/s",
                    "32 kbit/s",
                    "40 kbit/s",
                    "48 kbit/s",
                    "56 kbit/s",
                    "64 kbit/s",
                    "80 kbit/s",
                    "96 kbit/s",
                    "112 kbit/s",
                    "128 kbit/s",
                    "144 kbit/s",
                    "160 kbit/s",
                    "forbidden"
                )
            ),
        )
    }
}
