/*
 * 11/19/04 1.0 moved to LGPL.
 *
 * 18/06/01 Michael Scheerer, Fixed bugs which causes negative indexes in method huffmann_decode and in method dequanisize_sample.
 *
 * 16/07/01 Michael Scheerer, Catched a bug in method huffmann_decode, which causes an outOfIndexException. Cause : Indexnumber of
 * 24 at SfBandIndex, which has only a length of 22. I have simply and dirty fixed the index to <= 22, because I'm not really be
 * able to fix the bug. The Indexnumber is taken from the MP3 file and the origin Ma-Player with the same code works well.
 *
 * 02/19/99 Java Conversion by E.B, javalayer@javazoom.net-----------------------------------------------------------------------
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License along with this program; if not, write to the Free
 * Software Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * ----------------------------------------------------------------------
 */
package ru.hollowhorizon.hc.client.audio.decoder

import ru.hollowhorizon.hc.client.audio.decoder.HuffCodeTable.Companion.huffmanDecoder
import ru.hollowhorizon.hc.client.audio.decoder.HuffCodeTable.Companion.inithuff
import kotlin.math.min
import kotlin.math.pow


/**
 * Class Implementing Layer 3 Decoder.
 *
 * @since 0.0
 */
class LayerIIIDecoder(
    stream0: Bitstream, header0: Header, filtera: SynthesisFilter, filterb: SynthesisFilter, buffer0: OutputBuffer,
    whichCh0: Int,
) : FrameDecoder {
    private val d43: Double = 4.0 / 3.0

    private var scalefacBuffer: IntArray
    private var checkSumHuff = 0
    private val is1d: IntArray
    private val ro: Array<Array<FloatArray>>
    private val lr: Array<Array<FloatArray>>
    private val out1d: FloatArray
    private val prevblck: Array<FloatArray>
    private val k: Array<FloatArray>
    private val nonzero: IntArray
    private val stream: Bitstream
    private val header: Header
    private val filter1: SynthesisFilter
    private val filter2: SynthesisFilter
    private val buffer: OutputBuffer
    private val whichChannels: Int
    private val si: IIISideInfo
    private var br: BitReserve

    private val scalefac3: Array<temporaire2?>
    private val scalefac: Array<temporaire2?>
    private val maxGr: Int
    private val channels: Int
    private var firstChannel = 0
    private var lastChannel = 0
    private val sfreq: Int
    private var frameStart: Int
    private var part2Start = 0

    override fun decodeFrame() {
        decode()
    }

    private val samples1 = FloatArray(32)
    private val samples2 = FloatArray(32)

    private fun decode() {
        val slots = header.slots
        var flushMain: Int
        var ch: Int
        var ss: Int
        var sb: Int
        var sb18: Int
        var bytesToDiscard: Int

        getSideInfo()

        var i = 0
        while (i < slots) {
            br.hputbuf(stream.get_bits(8))
            i++
        }

        var mainDataEnd = br.hsstell() ushr 3

        if (((br.hsstell() and 7).also { flushMain = it }) != 0) {
            br.hgetbits(8 - flushMain)
            mainDataEnd++
        }

        bytesToDiscard = frameStart - mainDataEnd - si.main_data_begin

        frameStart += slots

        if (bytesToDiscard < 0) return

        if (mainDataEnd > 4096) {
            frameStart -= 4096
            br.rewindNbytes(4096)
        }

        while (bytesToDiscard > 0) {
            br.hgetbits(8)
            bytesToDiscard--
        }

        var gr = 0
        while (gr < maxGr) {
            ch = 0
            while (ch < channels) {
                part2Start = br.hsstell()

                if (header.version() == Header.MPEG1) getScaleFactors(ch, gr)
                else  // MPEG-2 LSF, SZD: MPEG-2.5 LSF
                    lsfScaleFactors(ch, gr)

                huffmanDecode(ch, gr)
                // System.out.println("CheckSum HuffMan = " + CheckSumHuff);
                dequantizeSample(ro[ch], ch, gr)
                ch++
            }

            stereo(gr)

            if (whichChannels == OutputChannels.DOWNMIX_CHANNELS && channels > 1) doDownmix()

            ch = firstChannel
            while (ch <= lastChannel) {
                reorder(lr[ch], ch, gr)
                antialias(ch, gr)

                // for (int hb = 0;hb<576;hb++) CheckSumOut1d = CheckSumOut1d + out_1d[hb];
                // System.out.println("CheckSumOut1d = "+CheckSumOut1d);
                hybrid(ch, gr)

                // for (int hb = 0;hb<576;hb++) CheckSumOut1d = CheckSumOut1d + out_1d[hb];
                // System.out.println("CheckSumOut1d = "+CheckSumOut1d);
                sb18 = 18
                while (sb18 < 576) {
                    // Frequency inversion
                    ss = 1
                    while (ss < SSLIMIT) {
                        out1d[sb18 + ss] = -out1d[sb18 + ss]
                        ss += 2
                    }
                    sb18 += 36
                }

                if (ch == 0 || whichChannels == OutputChannels.RIGHT_CHANNEL) {
                    ss = 0
                    while (ss < SSLIMIT) {
                        // Polyphase synthesis
                        sb = 0
                        sb18 = 0
                        while (sb18 < 576) {
                            samples1[sb] = out1d[sb18 + ss]
                            // filter1.input_sample(out_1d[sb18+ss], sb);
                            sb++
                            sb18 += 18
                        }
                        filter1.inputSamples(samples1)
                        filter1.calculatePcmSamples(buffer)
                        ss++
                    }
                } else {
                    ss = 0
                    while (ss < SSLIMIT) {
                        // Polyphase synthesis
                        sb = 0
                        sb18 = 0
                        while (sb18 < 576) {
                            samples2[sb] = out1d[sb18 + ss]
                            // filter2.input_sample(out_1d[sb18+ss], sb);
                            sb++
                            sb18 += 18
                        }
                        filter2.inputSamples(samples2)
                        filter2.calculatePcmSamples(buffer)
                        ss++
                    }
                }
                ch++
            }
            gr++
        }
    }

    private fun getSideInfo() {
        var ch: Int
        var gr: Int
        if (header.version() == Header.MPEG1) {
            si.main_data_begin = stream.get_bits(9)
            if (channels == 1) si.private_bits = stream.get_bits(5)
            else si.private_bits = stream.get_bits(3)

            ch = 0
            while (ch < channels) {
                si.ch[ch]!!.scfsi[0] = stream.get_bits(1)
                si.ch[ch]!!.scfsi[1] = stream.get_bits(1)
                si.ch[ch]!!.scfsi[2] = stream.get_bits(1)
                si.ch[ch]!!.scfsi[3] = stream.get_bits(1)
                ch++
            }

            gr = 0
            while (gr < 2) {
                ch = 0
                while (ch < channels) {
                    si.ch[ch]!!.gr[gr]!!.part2_3_length = stream.get_bits(12)
                    si.ch[ch]!!.gr[gr]!!.big_values = stream.get_bits(9)
                    si.ch[ch]!!.gr[gr]!!.global_gain = stream.get_bits(8)
                    si.ch[ch]!!.gr[gr]!!.scalefac_compress = stream.get_bits(4)
                    si.ch[ch]!!.gr[gr]!!.window_switching_flag = stream.get_bits(1)
                    if (si.ch[ch]!!.gr[gr]!!.window_switching_flag != 0) {
                        si.ch[ch]!!.gr[gr]!!.block_type = stream.get_bits(2)
                        si.ch[ch]!!.gr[gr]!!.mixed_block_flag = stream.get_bits(1)

                        si.ch[ch]!!.gr[gr]!!.table_select[0] = stream.get_bits(5)
                        si.ch[ch]!!.gr[gr]!!.table_select[1] = stream.get_bits(5)

                        si.ch[ch]!!.gr[gr]!!.subblock_gain[0] = stream.get_bits(3)
                        si.ch[ch]!!.gr[gr]!!.subblock_gain[1] = stream.get_bits(3)
                        si.ch[ch]!!.gr[gr]!!.subblock_gain[2] = stream.get_bits(3)

                        if (si.ch[ch]!!.gr[gr]!!.block_type == 0) return
                        else if (si.ch[ch]!!.gr[gr]!!.block_type == 2 && si.ch[ch]!!.gr[gr]!!.mixed_block_flag == 0) si.ch[ch]!!.gr[gr]!!.region0_count =
                            8
                        else si.ch[ch]!!.gr[gr]!!.region0_count = 7
                        si.ch[ch]!!.gr[gr]!!.region1_count = 20 - si.ch[ch]!!.gr[gr]!!.region0_count
                    } else {
                        si.ch[ch]!!.gr[gr]!!.table_select[0] = stream.get_bits(5)
                        si.ch[ch]!!.gr[gr]!!.table_select[1] = stream.get_bits(5)
                        si.ch[ch]!!.gr[gr]!!.table_select[2] = stream.get_bits(5)
                        si.ch[ch]!!.gr[gr]!!.region0_count = stream.get_bits(4)
                        si.ch[ch]!!.gr[gr]!!.region1_count = stream.get_bits(3)
                        si.ch[ch]!!.gr[gr]!!.block_type = 0
                    }
                    si.ch[ch]!!.gr[gr]!!.preflag = stream.get_bits(1)
                    si.ch[ch]!!.gr[gr]!!.scalefac_scale = stream.get_bits(1)
                    si.ch[ch]!!.gr[gr]!!.count1table_select = stream.get_bits(1)
                    ch++
                }
                gr++
            }
        } else { // MPEG-2 LSF, SZD: MPEG-2.5 LSF

            si.main_data_begin = stream.get_bits(8)
            if (channels == 1) si.private_bits = stream.get_bits(1)
            else si.private_bits = stream.get_bits(2)

            ch = 0
            while (ch < channels) {
                si.ch[ch]!!.gr[0]!!.part2_3_length = stream.get_bits(12)
                si.ch[ch]!!.gr[0]!!.big_values = stream.get_bits(9)
                si.ch[ch]!!.gr[0]!!.global_gain = stream.get_bits(8)
                si.ch[ch]!!.gr[0]!!.scalefac_compress = stream.get_bits(9)
                si.ch[ch]!!.gr[0]!!.window_switching_flag = stream.get_bits(1)

                if (si.ch[ch]!!.gr[0]!!.window_switching_flag != 0) {
                    si.ch[ch]!!.gr[0]!!.block_type = stream.get_bits(2)
                    si.ch[ch]!!.gr[0]!!.mixed_block_flag = stream.get_bits(1)
                    si.ch[ch]!!.gr[0]!!.table_select[0] = stream.get_bits(5)
                    si.ch[ch]!!.gr[0]!!.table_select[1] = stream.get_bits(5)

                    si.ch[ch]!!.gr[0]!!.subblock_gain[0] = stream.get_bits(3)
                    si.ch[ch]!!.gr[0]!!.subblock_gain[1] = stream.get_bits(3)
                    si.ch[ch]!!.gr[0]!!.subblock_gain[2] = stream.get_bits(3)

                    // Set region_count parameters since they are implicit in this case.
                    if (si.ch[ch]!!.gr[0]!!.block_type == 0) // Side info bad: block_type == 0 in split block
                        return
                    else if (si.ch[ch]!!.gr[0]!!.block_type == 2 && si.ch[ch]!!.gr[0]!!.mixed_block_flag == 0) si.ch[ch]!!.gr[0]!!.region0_count =
                        8
                    else {
                        si.ch[ch]!!.gr[0]!!.region0_count = 7
                        si.ch[ch]!!.gr[0]!!.region1_count = 20 - si.ch[ch]!!.gr[0]!!.region0_count
                    }
                } else {
                    si.ch[ch]!!.gr[0]!!.table_select[0] = stream.get_bits(5)
                    si.ch[ch]!!.gr[0]!!.table_select[1] = stream.get_bits(5)
                    si.ch[ch]!!.gr[0]!!.table_select[2] = stream.get_bits(5)
                    si.ch[ch]!!.gr[0]!!.region0_count = stream.get_bits(4)
                    si.ch[ch]!!.gr[0]!!.region1_count = stream.get_bits(3)
                    si.ch[ch]!!.gr[0]!!.block_type = 0
                }

                si.ch[ch]!!.gr[0]!!.scalefac_scale = stream.get_bits(1)
                si.ch[ch]!!.gr[0]!!.count1table_select = stream.get_bits(1)
                ch++
            }
        } // if (header.version() == MPEG1)
    }

    /**
     *
     */
    private fun getScaleFactors(ch: Int, gr: Int) {
        var sfb: Int
        var window: Int
        val grInfo = si.ch[ch]!!.gr[gr]
        val scaleComp = grInfo!!.scalefac_compress
        val length0 = slen[0][scaleComp]
        val length1 = slen[1][scaleComp]

        if (grInfo.window_switching_flag != 0 && grInfo.block_type == 2) {
            if (grInfo.mixed_block_flag != 0) { // MIXED
                sfb = 0
                while (sfb < 8) {
                    scalefac[ch]!!.l[sfb] = br.hgetbits(slen[0][grInfo.scalefac_compress])
                    sfb++
                }
                sfb = 3
                while (sfb < 6) {
                    window = 0
                    while (window < 3) {
                        scalefac[ch]!!.s[window][sfb] = br.hgetbits(slen[0][grInfo.scalefac_compress])
                        window++
                    }
                    sfb++
                }
                sfb = 6
                while (sfb < 12) {
                    window = 0
                    while (window < 3) {
                        scalefac[ch]!!.s[window][sfb] = br.hgetbits(slen[1][grInfo.scalefac_compress])
                        window++
                    }
                    sfb++
                }
                sfb = 12
                window = 0
                while (window < 3) {
                    scalefac[ch]!!.s[window][sfb] = 0
                    window++
                }
            } else { // SHORT

                scalefac[ch]!!.s[0][0] = br.hgetbits(length0)
                scalefac[ch]!!.s[1][0] = br.hgetbits(length0)
                scalefac[ch]!!.s[2][0] = br.hgetbits(length0)
                scalefac[ch]!!.s[0][1] = br.hgetbits(length0)
                scalefac[ch]!!.s[1][1] = br.hgetbits(length0)
                scalefac[ch]!!.s[2][1] = br.hgetbits(length0)
                scalefac[ch]!!.s[0][2] = br.hgetbits(length0)
                scalefac[ch]!!.s[1][2] = br.hgetbits(length0)
                scalefac[ch]!!.s[2][2] = br.hgetbits(length0)
                scalefac[ch]!!.s[0][3] = br.hgetbits(length0)
                scalefac[ch]!!.s[1][3] = br.hgetbits(length0)
                scalefac[ch]!!.s[2][3] = br.hgetbits(length0)
                scalefac[ch]!!.s[0][4] = br.hgetbits(length0)
                scalefac[ch]!!.s[1][4] = br.hgetbits(length0)
                scalefac[ch]!!.s[2][4] = br.hgetbits(length0)
                scalefac[ch]!!.s[0][5] = br.hgetbits(length0)
                scalefac[ch]!!.s[1][5] = br.hgetbits(length0)
                scalefac[ch]!!.s[2][5] = br.hgetbits(length0)
                scalefac[ch]!!.s[0][6] = br.hgetbits(length1)
                scalefac[ch]!!.s[1][6] = br.hgetbits(length1)
                scalefac[ch]!!.s[2][6] = br.hgetbits(length1)
                scalefac[ch]!!.s[0][7] = br.hgetbits(length1)
                scalefac[ch]!!.s[1][7] = br.hgetbits(length1)
                scalefac[ch]!!.s[2][7] = br.hgetbits(length1)
                scalefac[ch]!!.s[0][8] = br.hgetbits(length1)
                scalefac[ch]!!.s[1][8] = br.hgetbits(length1)
                scalefac[ch]!!.s[2][8] = br.hgetbits(length1)
                scalefac[ch]!!.s[0][9] = br.hgetbits(length1)
                scalefac[ch]!!.s[1][9] = br.hgetbits(length1)
                scalefac[ch]!!.s[2][9] = br.hgetbits(length1)
                scalefac[ch]!!.s[0][10] = br.hgetbits(length1)
                scalefac[ch]!!.s[1][10] = br.hgetbits(length1)
                scalefac[ch]!!.s[2][10] = br.hgetbits(length1)
                scalefac[ch]!!.s[0][11] = br.hgetbits(length1)
                scalefac[ch]!!.s[1][11] = br.hgetbits(length1)
                scalefac[ch]!!.s[2][11] = br.hgetbits(length1)
                scalefac[ch]!!.s[0][12] = 0
                scalefac[ch]!!.s[1][12] = 0
                scalefac[ch]!!.s[2][12] = 0
            } // SHORT
        } else { // LONG types 0,1,3

            if (si.ch[ch]!!.scfsi[0] == 0 || gr == 0) {
                scalefac[ch]!!.l[0] = br.hgetbits(length0)
                scalefac[ch]!!.l[1] = br.hgetbits(length0)
                scalefac[ch]!!.l[2] = br.hgetbits(length0)
                scalefac[ch]!!.l[3] = br.hgetbits(length0)
                scalefac[ch]!!.l[4] = br.hgetbits(length0)
                scalefac[ch]!!.l[5] = br.hgetbits(length0)
            }
            if (si.ch[ch]!!.scfsi[1] == 0 || gr == 0) {
                scalefac[ch]!!.l[6] = br.hgetbits(length0)
                scalefac[ch]!!.l[7] = br.hgetbits(length0)
                scalefac[ch]!!.l[8] = br.hgetbits(length0)
                scalefac[ch]!!.l[9] = br.hgetbits(length0)
                scalefac[ch]!!.l[10] = br.hgetbits(length0)
            }
            if (si.ch[ch]!!.scfsi[2] == 0 || gr == 0) {
                scalefac[ch]!!.l[11] = br.hgetbits(length1)
                scalefac[ch]!!.l[12] = br.hgetbits(length1)
                scalefac[ch]!!.l[13] = br.hgetbits(length1)
                scalefac[ch]!!.l[14] = br.hgetbits(length1)
                scalefac[ch]!!.l[15] = br.hgetbits(length1)
            }
            if (si.ch[ch]!!.scfsi[3] == 0 || gr == 0) {
                scalefac[ch]!!.l[16] = br.hgetbits(length1)
                scalefac[ch]!!.l[17] = br.hgetbits(length1)
                scalefac[ch]!!.l[18] = br.hgetbits(length1)
                scalefac[ch]!!.l[19] = br.hgetbits(length1)
                scalefac[ch]!!.l[20] = br.hgetbits(length1)
            }

            scalefac[ch]!!.l[21] = 0
            scalefac[ch]!!.l[22] = 0
        }
    }

    private val newSlen = IntArray(4)

    private fun getLsfScaleData(ch: Int, gr: Int) {
        val scalefacComp: Int
        val intScalefacComp: Int
        val modeExt = header.modeExtension
        val blocktypenumber: Int
        var blocknumber = 0

        val grInfo = si.ch[ch]!!.gr[gr]

        scalefacComp = grInfo!!.scalefac_compress

        blocktypenumber = if (grInfo.block_type == 2) {
            when (grInfo.mixed_block_flag) {
                0 -> 1
                1 -> 2
                else -> 0
            }
        } else 0

        if (!((modeExt == 1 || modeExt == 3) && ch == 1)) if (scalefacComp < 400) {
            newSlen[0] = (scalefacComp ushr 4) / 5
            newSlen[1] = (scalefacComp ushr 4) % 5
            newSlen[2] = (scalefacComp and 0xF) ushr 2
            newSlen[3] = scalefacComp and 3
            si.ch[ch]!!.gr[gr]!!.preflag = 0
            blocknumber = 0
        } else if (scalefacComp < 500) {
            newSlen[0] = (scalefacComp - 400 ushr 2) / 5
            newSlen[1] = (scalefacComp - 400 ushr 2) % 5
            newSlen[2] = scalefacComp - 400 and 3
            newSlen[3] = 0
            si.ch[ch]!!.gr[gr]!!.preflag = 0
            blocknumber = 1
        } else if (scalefacComp < 512) {
            newSlen[0] = (scalefacComp - 500) / 3
            newSlen[1] = (scalefacComp - 500) % 3
            newSlen[2] = 0
            newSlen[3] = 0
            si.ch[ch]!!.gr[gr]!!.preflag = 1
            blocknumber = 2
        }

        if ((modeExt == 1 || modeExt == 3) && ch == 1) {
            intScalefacComp = scalefacComp ushr 1

            if (intScalefacComp < 180) {
                newSlen[0] = intScalefacComp / 36
                newSlen[1] = intScalefacComp % 36 / 6
                newSlen[2] = intScalefacComp % 36 % 6
                newSlen[3] = 0
                si.ch[ch]!!.gr[gr]!!.preflag = 0
                blocknumber = 3
            } else if (intScalefacComp < 244) {
                newSlen[0] = (intScalefacComp - 180 and 0x3F) ushr 4
                newSlen[1] = (intScalefacComp - 180 and 0xF) ushr 2
                newSlen[2] = intScalefacComp - 180 and 3
                newSlen[3] = 0
                si.ch[ch]!!.gr[gr]!!.preflag = 0
                blocknumber = 4
            } else if (intScalefacComp < 255) {
                newSlen[0] = (intScalefacComp - 244) / 3
                newSlen[1] = (intScalefacComp - 244) % 3
                newSlen[2] = 0
                newSlen[3] = 0
                si.ch[ch]!!.gr[gr]!!.preflag = 0
                blocknumber = 5
            }
        }

        for (x in 0..44)  // why 45, not 54?
            scalefacBuffer[x] = 0

        var m = 0
        for (i in 0..3) for (j in 0 until nr_of_sfb_block[blocknumber][blocktypenumber][i]) {
            scalefacBuffer[m] = if (newSlen[i] == 0) 0 else br.hgetbits(newSlen[i])
            m++
        } // for (unint32 j ...
    }

    private fun lsfScaleFactors(ch: Int, gr: Int) {
        var m = 0
        var sfb: Int
        var window: Int
        val grInfo = si.ch[ch]!!.gr[gr]

        getLsfScaleData(ch, gr)

        if (grInfo!!.window_switching_flag != 0 && grInfo.block_type == 2) {
            if (grInfo.mixed_block_flag != 0) {
                sfb = 0
                while (sfb < 8) {
                    scalefac[ch]!!.l[sfb] = scalefacBuffer[m]
                    m++
                    sfb++
                }
                sfb = 3
                while (sfb < 12) {
                    window = 0
                    while (window < 3) {
                        scalefac[ch]!!.s[window][sfb] = scalefacBuffer[m]
                        m++
                        window++
                    }
                    sfb++
                }
                window = 0
                while (window < 3) {
                    scalefac[ch]!!.s[window][12] = 0
                    window++
                }
            } else { // SHORT

                sfb = 0
                while (sfb < 12) {
                    window = 0
                    while (window < 3) {
                        scalefac[ch]!!.s[window][sfb] = scalefacBuffer[m]
                        m++
                        window++
                    }
                    sfb++
                }

                window = 0
                while (window < 3) {
                    scalefac[ch]!!.s[window][12] = 0
                    window++
                }
            }
        } else { // LONG types 0,1,3

            sfb = 0
            while (sfb < 21) {
                scalefac[ch]!!.l[sfb] = scalefacBuffer[m]
                m++
                sfb++
            }
            scalefac[ch]!!.l[21] = 0 // Jeff
            scalefac[ch]!!.l[22] = 0
        }
    }

    /**
     *
     */
    var x: IntArray = intArrayOf(0)
    var y: IntArray = intArrayOf(0)
    var v: IntArray = intArrayOf(0)
    var w: IntArray = intArrayOf(0)

    private fun huffmanDecode(ch: Int, gr: Int) {
        x[0] = 0
        y[0] = 0
        v[0] = 0
        w[0] = 0

        val partLength = part2Start + si.ch[ch]!!.gr[gr]!!.part2_3_length
        var numBits: Int
        val region1Start: Int
        val region2Start: Int
        var index: Int

        val buf: Int
        var buf1: Int

        var h: HuffCodeTable?

        // Find region boundary for short block case
        if (si.ch[ch]!!.gr[gr]!!.window_switching_flag != 0 && si.ch[ch]!!.gr[gr]!!.block_type == 2) {
            // Region2.
            // MS: Extrahandling for 8KHZ

            region1Start = if (sfreq == 8) 72 else 36 // sfb[9/3]*3=36 or in case 8KHZ = 72
            region2Start = 576 // No Region2 for short block case
        } else { // Find region boundary for long block case

            buf = si.ch[ch]!!.gr[gr]!!.region0_count + 1
            buf1 = buf + si.ch[ch]!!.gr[gr]!!.region1_count + 1

            if (buf1 > sfBandIndex[sfreq]!!.l.size - 1) buf1 = sfBandIndex[sfreq]!!.l.size - 1

            region1Start = sfBandIndex[sfreq]!!.l[buf]
            region2Start = sfBandIndex[sfreq]!!.l[buf1] /* MI */
        }

        index = 0
        // Read bigvalues area
        var i = 0
        while (i < si.ch[ch]!!.gr[gr]!!.big_values shl 1) {
            h = if (i < region1Start) HuffCodeTable.ht[si.ch[ch]!!.gr[gr]!!.table_select[0]]
            else if (i < region2Start) HuffCodeTable.ht[si.ch[ch]!!.gr[gr]!!.table_select[1]]
            else HuffCodeTable.ht[si.ch[ch]!!.gr[gr]!!.table_select[2]]

            huffmanDecoder(h!!, x, y, v, w, br)

            // if (index >= is_1d.length)
            // System.out.println("i0="+i+"/"+(si.ch[ch].gr[gr].big_values<<1)+" Index="+index+" is_1d="+is_1d.length);
            is1d[index++] = x[0]
            is1d[index++] = y[0]

            checkSumHuff += x[0] + y[0]
            i += 2
        }

        // Read count1 area
        h = HuffCodeTable.ht[si.ch[ch]!!.gr[gr]!!.count1table_select + 32]
        numBits = br.hsstell()

        while (numBits < partLength && index < 576) {
            huffmanDecoder(h!!, x, y, v, w, br)

            is1d[index++] = v[0]
            is1d[index++] = w[0]
            is1d[index++] = x[0]
            is1d[index++] = y[0]
            checkSumHuff += v[0] + w[0] + x[0] + y[0]
            numBits = br.hsstell()
        }

        if (numBits > partLength) {
            br.rewindNbits(numBits - partLength)
            index -= 4
        }

        numBits = br.hsstell()

        // Dismiss stuffing bits
        if (numBits < partLength) br.hgetbits(partLength - numBits)

        // Zero out rest
        nonzero[ch] = min(index.toDouble(), 576.0).toInt()

        if (index < 0) index = 0

        // may not be necessary
        while (index < 576) {
            is1d[index] = 0
            index++
        }
    }

    private fun stereoKValues(isPos: Int, ioType: Int, i: Int) {
        if (isPos == 0) {
            k[0][i] = 1.0f
            k[1][i] = 1.0f
        } else if ((isPos and 1) != 0) {
            k[0][i] = io[ioType][isPos + 1 ushr 1]
            k[1][i] = 1.0f
        } else {
            k[0][i] = 1.0f
            k[1][i] = io[ioType][isPos ushr 1]
        }
    }

    private fun dequantizeSample(xr: Array<FloatArray>, ch: Int, gr: Int) {
        val grInfo = si.ch[ch]!!.gr[gr]
        var cb = 0
        var nextCbBoundary: Int
        var cbBegin = 0
        var cbWidth = 0
        var index = 0
        var tIndex: Int

        if (grInfo!!.window_switching_flag != 0 && grInfo.block_type == 2) {
            if (grInfo.mixed_block_flag != 0) nextCbBoundary = sfBandIndex[sfreq]!!.l[1]
            else {
                cbWidth = sfBandIndex[sfreq]!!.s[1]
                nextCbBoundary = (cbWidth shl 2) - cbWidth
                cbBegin = 0
            }
        } else nextCbBoundary = sfBandIndex[sfreq]!!.l[1]

        val gain = 2.0f.pow((0.25f * (grInfo.global_gain - 210.0f)))

        var j = 0
        while (j < nonzero[ch]) {
            val reste = j % SSLIMIT
            val quotien = ((j - reste) / SSLIMIT)
            if (is1d[j] == 0) xr[quotien][reste] = 0.0f
            else {
                val abv = is1d[j]
                if (abv < t_43.size) {
                    if (is1d[j] > 0) xr[quotien][reste] = gain * t_43[abv]
                    else if (-abv < t_43.size) xr[quotien][reste] = -gain * t_43[-abv]
                    else xr[quotien][reste] = (-gain * -abv.toDouble().pow(d43)).toFloat()
                } else if (is1d[j] > 0) xr[quotien][reste] = gain * abv.toDouble().pow(d43).toFloat()
                else xr[quotien][reste] = -gain * (-abv.toDouble().pow(d43)).toFloat()
            }
            j++
        }

        j = 0
        while (j < nonzero[ch]) {
            val reste = j % SSLIMIT
            val quotien = ((j - reste) / SSLIMIT)

            if (index == nextCbBoundary) if (grInfo.window_switching_flag != 0 && grInfo.block_type == 2) {
                if (grInfo.mixed_block_flag != 0) {
                    if (index == sfBandIndex[sfreq]!!.l[8]) {
                        nextCbBoundary = sfBandIndex[sfreq]!!.s[4]
                        nextCbBoundary = (nextCbBoundary shl 2) - nextCbBoundary
                        cb = 3
                        cbWidth = sfBandIndex[sfreq]!!.s[4] - sfBandIndex[sfreq]!!.s[3]

                        cbBegin = sfBandIndex[sfreq]!!.s[3]
                        cbBegin = (cbBegin shl 2) - cbBegin
                    } else if (index < sfBandIndex[sfreq]!!.l[8]) nextCbBoundary = sfBandIndex[sfreq]!!.l[++cb + 1]
                    else {
                        nextCbBoundary = sfBandIndex[sfreq]!!.s[++cb + 1]
                        nextCbBoundary = (nextCbBoundary shl 2) - nextCbBoundary

                        cbBegin = sfBandIndex[sfreq]!!.s[cb]
                        cbWidth = sfBandIndex[sfreq]!!.s[cb + 1] - cbBegin
                        cbBegin = (cbBegin shl 2) - cbBegin
                    }
                } else {
                    nextCbBoundary = sfBandIndex[sfreq]!!.s[++cb + 1]
                    nextCbBoundary = (nextCbBoundary shl 2) - nextCbBoundary

                    cbBegin = sfBandIndex[sfreq]!!.s[cb]
                    cbWidth = sfBandIndex[sfreq]!!.s[cb + 1] - cbBegin
                    cbBegin = (cbBegin shl 2) - cbBegin
                }
            } else nextCbBoundary = sfBandIndex[sfreq]!!.l[++cb + 1]

            // Do long/short dependent scaling operations
            if (grInfo.window_switching_flag != 0 && (grInfo.block_type == 2 && grInfo.mixed_block_flag == 0 || grInfo.block_type == 2 && j >= 36)) {
                tIndex = (index - cbBegin) / cbWidth
                /*
                 * xr[sb][ss] *= pow(2.0, ((-2.0 gr_info.subblock_gain[t_index]) -(0.5 (1.0 + gr_info.scalefac_scale)
                 * scalefac[ch].s[t_index][cb])));
                 */
                var idx = scalefac[ch]!!.s[tIndex][cb] shl grInfo.scalefac_scale
                idx += grInfo.subblock_gain[tIndex] shl 2

                xr[quotien][reste] *= two_to_negative_half_pow[idx]
            } else { // LONG block types 0,1,3 & 1st 2 subbands of switched blocks
                /*
                 * xr[sb][ss] *= pow(2.0, -0.5 (1.0+gr_info.scalefac_scale) (scalefac[ch].l[cb] + gr_info.preflag pretab[cb]));
                 */
                var idx = scalefac[ch]!!.l[cb]

                if (grInfo.preflag != 0) idx += pretab[cb]

                idx = idx shl grInfo.scalefac_scale
                xr[quotien][reste] *= two_to_negative_half_pow[idx]
            }
            index++
            j++
        }

        j = nonzero[ch]
        while (j < 576) {
            // Modif E.B 02/22/99
            var reste = j % SSLIMIT
            var quotien = ((j - reste) / SSLIMIT)
            if (reste < 0) reste = 0
            if (quotien < 0) quotien = 0
            xr[quotien][reste] = 0.0f
            j++
        }
    }

    private fun reorder(xr: Array<FloatArray>, ch: Int, gr: Int) {
        val grInfo = si.ch[ch]!!.gr[gr]
        var freq: Int
        var freq3: Int
        var index: Int
        var sfb: Int
        var sfbStart: Int
        var sfbLines: Int
        var srcLine: Int
        var desLine: Int

        if (grInfo!!.window_switching_flag != 0 && grInfo.block_type == 2) {
            index = 0
            while (index < 576) {
                out1d[index] = 0.0f
                index++
            }

            if (grInfo.mixed_block_flag != 0) {
                // NO REORDER FOR LOW 2 SUBBANDS
                index = 0
                while (index < 36) {
                    // Modif E.B 02/22/99
                    val reste = index % SSLIMIT
                    val quotien = ((index - reste) / SSLIMIT)
                    out1d[index] = xr[quotien][reste]
                    index++
                }
                // REORDERING FOR REST SWITCHED SHORT
                /*
                 * for( sfb=3,sfb_start=sfBandIndex[sfreq].s[3], sfb_lines=sfBandIndex[sfreq].s[4] - sfb_start; sfb < 13;
                 * sfb++,sfb_start = sfBandIndex[sfreq].s[sfb], sfb_lines = sfBandIndex[sfreq].s[sfb+1] - sfb_start ) {
                 */
                sfb = 3
                while (sfb < 13) {
                    // System.out.println("sfreq="+sfreq+" sfb="+sfb+" sfBandIndex="+sfBandIndex.length+" sfBandIndex[sfreq].s="+
                    // sfBandIndex[sfreq].s.length);
                    sfbStart = sfBandIndex[sfreq]!!.s[sfb]
                    sfbLines = sfBandIndex[sfreq]!!.s[sfb + 1] - sfbStart

                    val sfbStart3 = (sfbStart shl 2) - sfbStart

                    freq = 0
                    freq3 = 0
                    while (freq < sfbLines) {
                        srcLine = sfbStart3 + freq
                        desLine = sfbStart3 + freq3
                        var reste = srcLine % SSLIMIT
                        var quotien = ((srcLine - reste) / SSLIMIT)

                        out1d[desLine] = xr[quotien][reste]
                        srcLine += sfbLines
                        desLine++

                        reste = srcLine % SSLIMIT
                        quotien = ((srcLine - reste) / SSLIMIT)

                        out1d[desLine] = xr[quotien][reste]
                        srcLine += sfbLines
                        desLine++

                        reste = srcLine % SSLIMIT
                        quotien = ((srcLine - reste) / SSLIMIT)

                        out1d[desLine] = xr[quotien][reste]
                        freq++
                        freq3 += 3
                    }
                    sfb++
                }
            } else {
                index = 0
                while (index < 576) {
                    val j = reorderTable[sfreq]!![index]
                    val reste = j % SSLIMIT
                    val quotien = ((j - reste) / SSLIMIT)
                    out1d[index] = xr[quotien][reste]
                    index++
                }
            }
        } else {
            index = 0
            while (index < 576) {
                // Modif E.B 02/22/99
                val reste = index % SSLIMIT
                val quotien = ((index - reste) / SSLIMIT)
                out1d[index] = xr[quotien][reste]
                index++
            }
        }
    }

    private var isPos: IntArray = IntArray(576)
    private var isRatio: FloatArray = FloatArray(576)

    private fun stereo(gr: Int) {
        var sb: Int
        var ss: Int

        if (channels == 1) {
            sb = 0
            while (sb < SBLIMIT) {
                ss = 0
                while (ss < SSLIMIT) {
                    lr[0][sb][ss] = ro[0][sb][ss]
                    lr[0][sb][ss + 1] = ro[0][sb][ss + 1]
                    lr[0][sb][ss + 2] = ro[0][sb][ss + 2]
                    ss += 3
                }
                sb++
            }
        } else {
            val grInfo = si.ch[0]!!.gr[gr]
            val modeExt = header.modeExtension
            var sfb: Int
            var i: Int
            var lines: Int
            var temp: Int
            var temp2: Int

            val msStereo = header.mode() == Header.JOINT_STEREO && (modeExt and 0x2) != 0
            val stereo = header.mode() == Header.JOINT_STEREO && (modeExt and 0x1) != 0
            val lsf = header.version() == Header.MPEG2_LSF || header.version() == Header.MPEG25_LSF // SZD

            val ioType = grInfo!!.scalefac_compress and 1

            i = 0
            while (i < 576) {
                isPos[i] = 7

                isRatio[i] = 0.0f
                i++
            }

            if (stereo) if (grInfo.window_switching_flag != 0 && grInfo.block_type == 2) {
                if (grInfo.mixed_block_flag != 0) {
                    var maxSfb = 0

                    for (j in 0..2) {
                        var sfbcnt: Int
                        sfbcnt = 2
                        sfb = 12
                        while (sfb >= 3) {
                            i = sfBandIndex[sfreq]!!.s[sfb]
                            lines = sfBandIndex[sfreq]!!.s[sfb + 1] - i
                            i = (i shl 2) - i + (j + 1) * lines - 1

                            while (lines > 0) {
                                if (ro[1][i / 18][i % 18] != 0.0f) {
                                    // MDM: in java, array access is very slow.
                                    // Is quicker to compute div and mod values.
                                    // if (ro[1][ss_div[i]][ss_mod[i]] != 0.0f) {
                                    sfbcnt = sfb
                                    sfb = -10
                                    lines = -10
                                }

                                lines--
                                i--
                            } // while (lines > 0)


                            sfb--
                        }
                        sfb = sfbcnt + 1

                        if (sfb > maxSfb) maxSfb = sfb

                        while (sfb < 12) {
                            temp = sfBandIndex[sfreq]!!.s[sfb]
                            sb = sfBandIndex[sfreq]!!.s[sfb + 1] - temp
                            i = (temp shl 2) - temp + j * sb

                            while (sb > 0) {
                                isPos[i] = scalefac[1]!!.s[j][sfb]
                                if (isPos[i] != 7) if (lsf) stereoKValues(isPos[i], ioType, i)
                                else isRatio[i] = TAN12[isPos[i]]

                                i++
                                sb--
                            }
                            sfb++
                        } // while (sfb < 12)

                        sfb = sfBandIndex[sfreq]!!.s[10]
                        sb = sfBandIndex[sfreq]!!.s[11] - sfb
                        sfb = (sfb shl 2) - sfb + j * sb
                        temp = sfBandIndex[sfreq]!!.s[11]
                        sb = sfBandIndex[sfreq]!!.s[12] - temp
                        i = (temp shl 2) - temp + j * sb

                        while (sb > 0) {
                            isPos[i] = isPos[sfb]

                            if (lsf) {
                                k[0][i] = k[0][sfb]
                                k[1][i] = k[1][sfb]
                            } else isRatio[i] = isRatio[sfb]
                            i++
                            sb--
                        }
                    }
                    if (maxSfb <= 3) {
                        i = 2
                        ss = 17
                        sb = -1
                        while (i >= 0) if (ro[1][i][ss] != 0.0f) {
                            sb = (i shl 4) + (i shl 1) + ss
                            i = -1
                        } else {
                            ss--
                            if (ss < 0) {
                                i--
                                ss = 17
                            }
                        } // if (ro ...

                        i = 0
                        while (sfBandIndex[sfreq]!!.l[i] <= sb) i++
                        sfb = i
                        i = sfBandIndex[sfreq]!!.l[i]
                        while (sfb < 8) {
                            sb = sfBandIndex[sfreq]!!.l[sfb + 1] - sfBandIndex[sfreq]!!.l[sfb]
                            while (sb > 0) {
                                isPos[i] = scalefac[1]!!.l[sfb]
                                if (isPos[i] != 7) if (lsf) stereoKValues(isPos[i], ioType, i)
                                else isRatio[i] = TAN12[isPos[i]]
                                i++
                                sb--
                            }
                            sfb++
                        }
                    } // for (j=0 ...
                } else for (j in 0..2) {
                    var sfbcnt: Int
                    sfbcnt = -1
                    sfb = 12
                    while (sfb >= 0) {
                        temp = sfBandIndex[sfreq]!!.s[sfb]
                        lines = sfBandIndex[sfreq]!!.s[sfb + 1] - temp
                        i = (temp shl 2) - temp + (j + 1) * lines - 1

                        while (lines > 0) {
                            if (ro[1][i / 18][i % 18] != 0.0f) {
                                // MDM: in java, array access is very slow.
                                // Is quicker to compute div and mod values.
                                // if (ro[1][ss_div[i]][ss_mod[i]] != 0.0f) {
                                sfbcnt = sfb
                                sfb = -10
                                lines = -10
                            }
                            lines--
                            i--
                        } // while (lines > 0) */


                        sfb--
                    }
                    sfb = sfbcnt + 1
                    while (sfb < 12) {
                        temp = sfBandIndex[sfreq]!!.s[sfb]
                        sb = sfBandIndex[sfreq]!!.s[sfb + 1] - temp
                        i = (temp shl 2) - temp + j * sb
                        while (sb > 0) {
                            isPos[i] = scalefac[1]!!.s[j][sfb]
                            if (isPos[i] != 7) if (lsf) stereoKValues(isPos[i], ioType, i)
                            else isRatio[i] = TAN12[isPos[i]]
                            i++
                            sb--
                        }
                        sfb++
                    } // while (sfb<12)


                    temp = sfBandIndex[sfreq]!!.s[10]
                    temp2 = sfBandIndex[sfreq]!!.s[11]
                    sb = temp2 - temp
                    sfb = (temp shl 2) - temp + j * sb
                    sb = sfBandIndex[sfreq]!!.s[12] - temp2
                    i = (temp2 shl 2) - temp2 + j * sb

                    while (sb > 0) {
                        isPos[i] = isPos[sfb]

                        if (lsf) {
                            k[0][i] = k[0][sfb]
                            k[1][i] = k[1][sfb]
                        } else isRatio[i] = isRatio[sfb]
                        i++
                        sb--
                    }
                } // for (sfb=12
            } else { // if (gr_info.window_switching_flag ...
                i = 31
                ss = 17
                sb = 0
                while (i >= 0) if (ro[1][i][ss] != 0.0f) {
                    sb = (i shl 4) + (i shl 1) + ss
                    i = -1
                } else {
                    ss--
                    if (ss < 0) {
                        i--
                        ss = 17
                    }
                }
                i = 0
                while (sfBandIndex[sfreq]!!.l[i] <= sb) i++

                sfb = i
                i = sfBandIndex[sfreq]!!.l[i]
                while (sfb < 21) {
                    sb = sfBandIndex[sfreq]!!.l[sfb + 1] - sfBandIndex[sfreq]!!.l[sfb]
                    while (sb > 0) {
                        isPos[i] = scalefac[1]!!.l[sfb]
                        if (isPos[i] != 7) if (lsf) stereoKValues(isPos[i], ioType, i)
                        else isRatio[i] = TAN12[isPos[i]]
                        i++
                        sb--
                    }
                    sfb++
                }
                sfb = sfBandIndex[sfreq]!!.l[20]
                sb = 576 - sfBandIndex[sfreq]!!.l[21]
                while (sb > 0 && i < 576) {
                    isPos[i] = isPos[sfb] // error here : i >=576

                    if (lsf) {
                        k[0][i] = k[0][sfb]
                        k[1][i] = k[1][sfb]
                    } else isRatio[i] = isRatio[sfb]
                    i++
                    sb--
                }
            } // if (gr_info.window_switching_flag ...


            i = 0
            sb = 0
            while (sb < SBLIMIT) {
                ss = 0
                while (ss < SSLIMIT) {
                    if (isPos[i] == 7) {
                        if (msStereo) {
                            lr[0][sb][ss] = (ro[0][sb][ss] + ro[1][sb][ss]) * 0.70710677f
                            lr[1][sb][ss] = (ro[0][sb][ss] - ro[1][sb][ss]) * 0.70710677f
                        } else {
                            lr[0][sb][ss] = ro[0][sb][ss]
                            lr[1][sb][ss] = ro[1][sb][ss]
                        }
                    } else if (stereo) if (lsf) {
                        lr[0][sb][ss] = ro[0][sb][ss] * k[0][i]
                        lr[1][sb][ss] = ro[0][sb][ss] * k[1][i]
                    } else {
                        lr[1][sb][ss] = ro[0][sb][ss] / (1 + isRatio[i])
                        lr[0][sb][ss] = lr[1][sb][ss] * isRatio[i]
                    }
                    /*
                     * else { System.out.println("Error in stereo processing\n"); }
                     */
                    i++
                    ss++
                }
                sb++
            }
        } // channels == 2
    }

    /**
     *
     */
    private fun antialias(ch: Int, gr: Int) {
        var ss: Int
        val sb18lim: Int
        val grInfo = si.ch[ch]!!.gr[gr]

        // 31 alias-reduction operations between each pair of sub-bands
        // with 8 butterflies between each pair
        if (grInfo!!.window_switching_flag != 0 && grInfo.block_type == 2 && grInfo.mixed_block_flag == 0) return

        sb18lim =
            if (grInfo.window_switching_flag != 0 && grInfo.mixed_block_flag != 0 && grInfo.block_type == 2) 18
            else 558

        var sb18 = 0
        while (sb18 < sb18lim) {
            ss = 0
            while (ss < 8) {
                val srcIdx1 = sb18 + 17 - ss
                val srcIdx2 = sb18 + 18 + ss
                val bu = out1d[srcIdx1]
                val bd = out1d[srcIdx2]
                out1d[srcIdx1] = bu * cs[ss] - bd * ca[ss]
                out1d[srcIdx2] = bd * cs[ss] + bu * ca[ss]
                ss++
            }
            sb18 += 18
        }
    }

    private var tsOutCopy: FloatArray = FloatArray(18)
    private var rawout: FloatArray = FloatArray(36)

    private fun hybrid(ch: Int, gr: Int) {
        var bt: Int
        val grInfo = si.ch[ch]!!.gr[gr]
        var tsOut: FloatArray

        var prvblk: Array<FloatArray>

        var sb18 = 0
        while (sb18 < 576) {
            bt =
                if (grInfo!!.window_switching_flag != 0 && grInfo.mixed_block_flag != 0 && sb18 < 36) 0 else grInfo.block_type

            tsOut = out1d
            // Modif E.B 02/22/99
            System.arraycopy(tsOut, sb18, tsOutCopy, 0, 18)

            invMdct(tsOutCopy, rawout, bt)

            System.arraycopy(tsOutCopy, 0, tsOut, sb18, 18)

            // Fin Modif

            // overlap addition
            prvblk = prevblck

            tsOut[sb18] = rawout[0] + prvblk[ch][sb18]
            prvblk[ch][sb18] = rawout[18]
            tsOut[1 + sb18] = rawout[1] + prvblk[ch][sb18 + 1]
            prvblk[ch][sb18 + 1] = rawout[19]
            tsOut[2 + sb18] = rawout[2] + prvblk[ch][sb18 + 2]
            prvblk[ch][sb18 + 2] = rawout[20]
            tsOut[3 + sb18] = rawout[3] + prvblk[ch][sb18 + 3]
            prvblk[ch][sb18 + 3] = rawout[21]
            tsOut[4 + sb18] = rawout[4] + prvblk[ch][sb18 + 4]
            prvblk[ch][sb18 + 4] = rawout[22]
            tsOut[5 + sb18] = rawout[5] + prvblk[ch][sb18 + 5]
            prvblk[ch][sb18 + 5] = rawout[23]
            tsOut[6 + sb18] = rawout[6] + prvblk[ch][sb18 + 6]
            prvblk[ch][sb18 + 6] = rawout[24]
            tsOut[7 + sb18] = rawout[7] + prvblk[ch][sb18 + 7]
            prvblk[ch][sb18 + 7] = rawout[25]
            tsOut[8 + sb18] = rawout[8] + prvblk[ch][sb18 + 8]
            prvblk[ch][sb18 + 8] = rawout[26]
            tsOut[9 + sb18] = rawout[9] + prvblk[ch][sb18 + 9]
            prvblk[ch][sb18 + 9] = rawout[27]
            tsOut[10 + sb18] = rawout[10] + prvblk[ch][sb18 + 10]
            prvblk[ch][sb18 + 10] = rawout[28]
            tsOut[11 + sb18] = rawout[11] + prvblk[ch][sb18 + 11]
            prvblk[ch][sb18 + 11] = rawout[29]
            tsOut[12 + sb18] = rawout[12] + prvblk[ch][sb18 + 12]
            prvblk[ch][sb18 + 12] = rawout[30]
            tsOut[13 + sb18] = rawout[13] + prvblk[ch][sb18 + 13]
            prvblk[ch][sb18 + 13] = rawout[31]
            tsOut[14 + sb18] = rawout[14] + prvblk[ch][sb18 + 14]
            prvblk[ch][sb18 + 14] = rawout[32]
            tsOut[15 + sb18] = rawout[15] + prvblk[ch][sb18 + 15]
            prvblk[ch][sb18 + 15] = rawout[33]
            tsOut[16 + sb18] = rawout[16] + prvblk[ch][sb18 + 16]
            prvblk[ch][sb18 + 16] = rawout[34]
            tsOut[17 + sb18] = rawout[17] + prvblk[ch][sb18 + 17]
            prvblk[ch][sb18 + 17] = rawout[35]
            sb18 += 18
        }
    }

    private fun doDownmix() {
        for (sb in 0 until SSLIMIT) {
            var ss = 0
            while (ss < SSLIMIT) {
                lr[0][sb][ss] = (lr[0][sb][ss] + lr[1][sb][ss]) * 0.5f
                lr[0][sb][ss + 1] = (lr[0][sb][ss + 1] + lr[1][sb][ss + 1]) * 0.5f
                lr[0][sb][ss + 2] = (lr[0][sb][ss + 2] + lr[1][sb][ss + 2]) * 0.5f
                ss += 3
            }
        }
    }

    private fun invMdct(input: FloatArray, output: FloatArray, blockType: Int) {
        val winBt: FloatArray
        var i: Int

        var tmpf0: Float
        var tmpf1: Float
        var tmpf2: Float
        var tmpf3: Float
        var tmpf4: Float
        var tmpf5: Float
        var tmpf6: Float
        var tmpf7: Float
        var tmpf8: Float
        var tmpf9: Float
        var tmpf10: Float
        var tmpf11: Float
        val tmpf12: Float
        val tmpf13: Float
        val tmpf14: Float
        val tmpf15: Float
        val tmpf16: Float
        val tmpf17: Float

        if (blockType == 2) {
            output.fill(0f, 0, 35)

            var sixI = 0

            i = 0
            while (i < 3) {
                input[15 + i] += input[12 + i]
                input[12 + i] += input[9 + i]
                input[9 + i] += input[6 + i]
                input[6 + i] += input[3 + i]
                input[3 + i] += input[i]
                input[15 + i] += input[9 + i]
                input[9 + i] += input[3 + i]
                var sum: Float
                var pp2 = input[12 + i] * 0.500000000f
                var pp1 = input[6 + i] * 0.8660254f
                sum = input[i] + pp2
                tmpf1 = input[i] - input[12 + i]
                tmpf0 = sum + pp1
                tmpf2 = sum - pp1
                pp2 = input[15 + i] * 0.500000000f
                pp1 = input[9 + i] * 0.8660254f
                sum = input[3 + i] + pp2
                tmpf4 = input[3 + i] - input[15 + i]
                tmpf5 = sum + pp1
                tmpf3 = sum - pp1
                tmpf3 *= 1.9318516f
                tmpf4 *= 0.70710677f
                tmpf5 *= 0.5176381f
                var save = tmpf0
                tmpf0 += tmpf5
                tmpf5 = save - tmpf5
                save = tmpf1
                tmpf1 += tmpf4
                tmpf4 = save - tmpf4
                save = tmpf2
                tmpf2 += tmpf3
                tmpf3 = save - tmpf3
                tmpf0 *= 0.5043145f
                tmpf1 *= 0.541196100f
                tmpf2 *= 0.6302362f
                tmpf3 *= 0.8213398f
                tmpf4 *= 1.306563f
                tmpf5 *= 3.830649f
                tmpf8 = -tmpf0 * 0.7933533f
                tmpf9 = -tmpf0 * 0.6087614f
                tmpf7 = -tmpf1 * 0.9238795f
                tmpf10 = -tmpf1 * 0.38268343f
                tmpf6 = -tmpf2 * 0.9914449f
                tmpf11 = -tmpf2 * 0.13052619f
                tmpf0 = tmpf3
                tmpf1 = tmpf4 * 0.38268343f
                tmpf2 = tmpf5 * 0.6087614f
                tmpf3 = -tmpf5 * 0.7933533f
                tmpf4 = -tmpf4 * 0.9238795f
                tmpf5 = -tmpf0 * 0.9914449f
                tmpf0 *= 0.13052619f
                output[sixI + 6] += tmpf0
                output[sixI + 7] += tmpf1
                output[sixI + 8] += tmpf2
                output[sixI + 9] += tmpf3
                output[sixI + 10] += tmpf4
                output[sixI + 11] += tmpf5
                output[sixI + 12] += tmpf6
                output[sixI + 13] += tmpf7
                output[sixI + 14] += tmpf8
                output[sixI + 15] += tmpf9
                output[sixI + 16] += tmpf10
                output[sixI + 17] += tmpf11
                sixI += 6
                i++
            }
        } else {
            input[17] += input[16]
            input[16] += input[15]
            input[15] += input[14]
            input[14] += input[13]
            input[13] += input[12]
            input[12] += input[11]
            input[11] += input[10]
            input[10] += input[9]
            input[9] += input[8]
            input[8] += input[7]
            input[7] += input[6]
            input[6] += input[5]
            input[5] += input[4]
            input[4] += input[3]
            input[3] += input[2]
            input[2] += input[1]
            input[1] += input[0]
            input[17] += input[15]
            input[15] += input[13]
            input[13] += input[11]
            input[11] += input[9]
            input[9] += input[7]
            input[7] += input[5]
            input[5] += input[3]
            input[3] += input[1]
            val tmp0: Float
            val tmp1: Float
            val tmp2: Float
            val tmp3: Float
            val tmp0_: Float
            val tmp2_: Float
            val tmp3_: Float
            val tmp0o: Float
            val tmp1o: Float
            val tmp2o: Float
            val tmp3o: Float
            val tmp0_o: Float
            val tmp2_o: Float
            val tmp3_o: Float
            val i00 = input[0] + input[0]
            val iip12 = i00 + input[12]
            tmp0 = iip12 + input[4] * 1.8793852f + input[8] * 1.5320889f + input[16] * 0.34729636f
            tmp1 = i00 + input[4] - input[8] - input[12] - input[12] - input[16]
            tmp2 = iip12 - input[4] * 0.34729636f - input[8] * 1.8793852f + input[16] * 1.5320889f
            tmp3 = iip12 - input[4] * 1.5320889f + input[8] * 0.34729636f - input[16] * 1.8793852f
            val tmp4 = input[0] - input[4] + input[8] - input[12] + input[16]
            val i66_ = input[6] * 1.7320508f
            tmp0_ = input[2] * 1.9696155f + i66_ + input[10] * 1.2855753f + input[14] * 0.6840403f
            val tmp1_ = (input[2] - input[10] - input[14]) * 1.7320508f
            tmp2_ = input[2] * 1.2855753f - i66_ - input[10] * 0.6840403f + input[14] * 1.9696155f
            tmp3_ = input[2] * 0.6840403f - i66_ + input[10] * 1.9696155f - input[14] * 1.2855753f
            val i0 = input[1] + input[1]
            val i0p12 = i0 + input[12 + 1]

            tmp0o = i0p12 + input[4 + 1] * 1.8793852f + input[8 + 1] * 1.5320889f + input[16 + 1] * 0.34729636f
            tmp1o = i0 + input[4 + 1] - input[8 + 1] - input[12 + 1] - input[12 + 1] - input[16 + 1]
            tmp2o = i0p12 - input[4 + 1] * 0.34729636f - input[8 + 1] * 1.8793852f + input[16 + 1] * 1.5320889f
            tmp3o = i0p12 - input[4 + 1] * 1.5320889f + input[8 + 1] * 0.34729636f - input[16 + 1] * 1.8793852f
            val tmp4o = (input[0 + 1] - input[4 + 1] + input[8 + 1] - input[12 + 1] + input[16 + 1]) * 0.70710677f
            val i6_ = input[6 + 1] * 1.7320508f
            tmp0_o =
                input[2 + 1] * 1.9696155f + i6_ + input[10 + 1] * 1.2855753f + input[14 + 1] * 0.6840403f
            val tmp1_o = (input[2 + 1] - input[10 + 1] - input[14 + 1]) * 1.7320508f
            tmp2_o =
                input[2 + 1] * 1.2855753f - i6_ - input[10 + 1] * 0.6840403f + input[14 + 1] * 1.9696155f
            tmp3_o =
                input[2 + 1] * 0.6840403f - i6_ + input[10 + 1] * 1.9696155f - input[14 + 1] * 1.2855753f

            var e = tmp0 + tmp0_
            var o = (tmp0o + tmp0_o) * 0.5019099f
            tmpf0 = e + o
            tmpf17 = e - o
            e = tmp1 + tmp1_
            o = (tmp1o + tmp1_o) * 0.5176381f
            tmpf1 = e + o
            tmpf16 = e - o
            e = tmp2 + tmp2_
            o = (tmp2o + tmp2_o) * 0.55168897f
            tmpf2 = e + o
            tmpf15 = e - o
            e = tmp3 + tmp3_
            o = (tmp3o + tmp3_o) * 0.61038727f
            tmpf3 = e + o
            tmpf14 = e - o
            tmpf4 = tmp4 + tmp4o
            tmpf13 = tmp4 - tmp4o
            e = tmp3 - tmp3_
            o = (tmp3o - tmp3_o) * 0.8717234f
            tmpf5 = e + o
            tmpf12 = e - o
            e = tmp2 - tmp2_
            o = (tmp2o - tmp2_o) * 1.1831008f
            tmpf6 = e + o
            tmpf11 = e - o
            e = tmp1 - tmp1_
            o = (tmp1o - tmp1_o) * 1.9318516f
            tmpf7 = e + o
            tmpf10 = e - o
            e = tmp0 - tmp0_
            o = (tmp0o - tmp0_o) * 5.7368565f
            tmpf8 = e + o
            tmpf9 = e - o

            winBt = win[blockType]

            output[0] = -tmpf9 * winBt[0]
            output[1] = -tmpf10 * winBt[1]
            output[2] = -tmpf11 * winBt[2]
            output[3] = -tmpf12 * winBt[3]
            output[4] = -tmpf13 * winBt[4]
            output[5] = -tmpf14 * winBt[5]
            output[6] = -tmpf15 * winBt[6]
            output[7] = -tmpf16 * winBt[7]
            output[8] = -tmpf17 * winBt[8]
            output[9] = tmpf17 * winBt[9]
            output[10] = tmpf16 * winBt[10]
            output[11] = tmpf15 * winBt[11]
            output[12] = tmpf14 * winBt[12]
            output[13] = tmpf13 * winBt[13]
            output[14] = tmpf12 * winBt[14]
            output[15] = tmpf11 * winBt[15]
            output[16] = tmpf10 * winBt[16]
            output[17] = tmpf9 * winBt[17]
            output[18] = tmpf8 * winBt[18]
            output[19] = tmpf7 * winBt[19]
            output[20] = tmpf6 * winBt[20]
            output[21] = tmpf5 * winBt[21]
            output[22] = tmpf4 * winBt[22]
            output[23] = tmpf3 * winBt[23]
            output[24] = tmpf2 * winBt[24]
            output[25] = tmpf1 * winBt[25]
            output[26] = tmpf0 * winBt[26]
            output[27] = tmpf0 * winBt[27]
            output[28] = tmpf1 * winBt[28]
            output[29] = tmpf2 * winBt[29]
            output[30] = tmpf3 * winBt[30]
            output[31] = tmpf4 * winBt[31]
            output[32] = tmpf5 * winBt[32]
            output[33] = tmpf6 * winBt[33]
            output[34] = tmpf7 * winBt[34]
            output[35] = tmpf8 * winBt[35]
        }
    }

    internal class SBI(val l: IntArray, val s: IntArray)

    internal class GrInfo {
        var part2_3_length: Int = 0
        var big_values: Int = 0
        var global_gain: Int = 0
        var scalefac_compress: Int = 0
        var window_switching_flag: Int = 0
        var block_type: Int = 0
        var mixed_block_flag: Int = 0
        var table_select: IntArray = IntArray(3)
        var subblock_gain: IntArray = IntArray(3)
        var region0_count: Int = 0
        var region1_count: Int = 0
        var preflag: Int = 0
        var scalefac_scale: Int = 0
        var count1table_select: Int = 0
    }

    internal class temporaire {
        var scfsi: IntArray = IntArray(4)
        var gr: Array<GrInfo?> = arrayOfNulls(2)

        /**
         * Dummy Constructor
         */
        init {
            gr[0] = GrInfo()
            gr[1] = GrInfo()
        }
    }

    internal class IIISideInfo {
        var main_data_begin: Int = 0
        var private_bits: Int = 0
        var ch: Array<temporaire?> = arrayOfNulls(2)

        /**
         * Dummy Constructor
         */
        init {
            ch[0] = temporaire()
            ch[1] = temporaire()
        }
    }

    internal class temporaire2 {
        var l: IntArray = IntArray(23) /* [cb] */
        var s: Array<IntArray> = Array(3) { IntArray(13) } /* [window][cb] */
    }

    private val sfBandIndex: Array<SBI?>

    class Sftable(var l: IntArray, var s: IntArray)

    private var sftable: Sftable

    init {
        inithuff()
        is1d = IntArray(SBLIMIT * SSLIMIT + 4)
        ro = Array(2) { Array(SBLIMIT) { FloatArray(SSLIMIT) } }
        lr = Array(2) { Array(SBLIMIT) { FloatArray(SSLIMIT) } }
        out1d = FloatArray(SBLIMIT * SSLIMIT)
        prevblck = Array(2) { FloatArray(SBLIMIT * SSLIMIT) }
        k = Array(2) { FloatArray(SBLIMIT * SSLIMIT) }
        nonzero = IntArray(2)

        scalefac3 = arrayOfNulls(2)
        scalefac3[0] = temporaire2()
        scalefac3[1] = temporaire2()
        scalefac = scalefac3

        // L3TABLE INIT
        sfBandIndex = arrayOfNulls(9) // SZD: MPEG2.5 +3 indices
        val l0 = intArrayOf(
            0,
            6,
            12,
            18,
            24,
            30,
            36,
            44,
            54,
            66,
            80,
            96,
            116,
            140,
            168,
            200,
            238,
            284,
            336,
            396,
            464,
            522,
            576
        )
        val s0 = intArrayOf(0, 4, 8, 12, 18, 24, 32, 42, 56, 74, 100, 132, 174, 192)
        val l1 = intArrayOf(
            0,
            6,
            12,
            18,
            24,
            30,
            36,
            44,
            54,
            66,
            80,
            96,
            114,
            136,
            162,
            194,
            232,
            278,
            330,
            394,
            464,
            540,
            576
        )
        val s1 = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 136, 180, 192)
        val l2 = intArrayOf(
            0,
            6,
            12,
            18,
            24,
            30,
            36,
            44,
            54,
            66,
            80,
            96,
            116,
            140,
            168,
            200,
            238,
            284,
            336,
            396,
            464,
            522,
            576
        )
        val s2 = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 134, 174, 192)

        val l3 =
            intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 52, 62, 74, 90, 110, 134, 162, 196, 238, 288, 342, 418, 576)
        val s3 = intArrayOf(0, 4, 8, 12, 16, 22, 30, 40, 52, 66, 84, 106, 136, 192)
        val l4 =
            intArrayOf(0, 4, 8, 12, 16, 20, 24, 30, 36, 42, 50, 60, 72, 88, 106, 128, 156, 190, 230, 276, 330, 384, 576)
        val s4 = intArrayOf(0, 4, 8, 12, 16, 22, 28, 38, 50, 64, 80, 100, 126, 192)
        val l5 = intArrayOf(
            0,
            4,
            8,
            12,
            16,
            20,
            24,
            30,
            36,
            44,
            54,
            66,
            82,
            102,
            126,
            156,
            194,
            240,
            296,
            364,
            448,
            550,
            576
        )
        val s5 = intArrayOf(0, 4, 8, 12, 16, 22, 30, 42, 58, 78, 104, 138, 180, 192)
        // SZD: MPEG2.5
        val l6 = intArrayOf(
            0,
            6,
            12,
            18,
            24,
            30,
            36,
            44,
            54,
            66,
            80,
            96,
            116,
            140,
            168,
            200,
            238,
            284,
            336,
            396,
            464,
            522,
            576
        )
        val s6 = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 134, 174, 192)
        val l7 = intArrayOf(
            0,
            6,
            12,
            18,
            24,
            30,
            36,
            44,
            54,
            66,
            80,
            96,
            116,
            140,
            168,
            200,
            238,
            284,
            336,
            396,
            464,
            522,
            576
        )
        val s7 = intArrayOf(0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 134, 174, 192)
        val l8 = intArrayOf(
            0,
            12,
            24,
            36,
            48,
            60,
            72,
            88,
            108,
            132,
            160,
            192,
            232,
            280,
            336,
            400,
            476,
            566,
            568,
            570,
            572,
            574,
            576
        )
        val s8 = intArrayOf(0, 8, 16, 24, 36, 52, 72, 96, 124, 160, 162, 164, 166, 192)

        sfBandIndex[0] = SBI(l0, s0)
        sfBandIndex[1] = SBI(l1, s1)
        sfBandIndex[2] = SBI(l2, s2)

        sfBandIndex[3] = SBI(l3, s3)
        sfBandIndex[4] = SBI(l4, s4)
        sfBandIndex[5] = SBI(l5, s5)
        // SZD: MPEG2.5
        sfBandIndex[6] = SBI(l6, s6)
        sfBandIndex[7] = SBI(l7, s7)
        sfBandIndex[8] = SBI(l8, s8)

        // END OF L3TABLE INIT
        reorderTable = arrayOfNulls(9)
        for (i in 0..8) reorderTable[i] = reorder(sfBandIndex[i]!!.s)

        // Sftable
        val ll0 = intArrayOf(0, 6, 11, 16, 21)
        val ss0 = intArrayOf(0, 6, 12)
        sftable = Sftable(ll0, ss0)

        // END OF Sftable

        // scalefac_buffer
        scalefacBuffer = IntArray(54)

        // END OF scalefac_buffer
        stream = stream0
        header = header0
        filter1 = filtera
        filter2 = filterb
        buffer = buffer0
        whichChannels = whichCh0

        frameStart = 0
        channels = if (header.mode() == Header.SINGLE_CHANNEL) 1 else 2
        maxGr = if (header.version() == Header.MPEG1) 2 else 1

        sfreq =
            header.sampleFrequency() + (if (header.version() == Header.MPEG1) 3 else if (header.version() == Header.MPEG25_LSF) 6 else 0) // SZD

        if (channels == 2) when (whichChannels) {
            OutputChannels.LEFT_CHANNEL, OutputChannels.DOWNMIX_CHANNELS -> {
                lastChannel = 0
                firstChannel = 0
            }

            OutputChannels.RIGHT_CHANNEL -> {
                lastChannel = 1
                firstChannel = 1
            }

            OutputChannels.BOTH_CHANNELS -> {
                firstChannel = 0
                lastChannel = 1
            }

            else -> {
                firstChannel = 0
                lastChannel = 1
            }
        } else {
            lastChannel = 0
            firstChannel = 0
        }

        for (ch in 0..1) for (j in 0..575) prevblck[ch][j] = 0.0f

        nonzero[1] = 576
        nonzero[0] = nonzero[1]

        br = BitReserve()
        si = IIISideInfo()
    }

    companion object {
        private const val SSLIMIT = 18
        private const val SBLIMIT = 32

        // class III_scalefac_t
        // {
        // public temporaire2[] tab;
        // /**
        // * Dummy Constructor
        // */
        // public III_scalefac_t()
        // {
        // tab = new temporaire2[2];
        // }
        // }
        private val slen = arrayOf(
            intArrayOf(0, 0, 0, 0, 3, 1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4),
            intArrayOf(0, 1, 2, 3, 0, 1, 2, 3, 1, 2, 3, 1, 2, 3, 2, 3)
        )

        val pretab: IntArray = intArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 3, 2, 0)

        val two_to_negative_half_pow: FloatArray = floatArrayOf(
            1.0000000000E+00f,
            0.70710677f,
            5.0000000000E-01f,
            0.35355338f,
            2.5000000000E-01f,
            0.17677669f,
            1.2500000000E-01f,
            0.088388346f,
            6.2500000000E-02f,
            0.044194173f,
            3.1250000000E-02f,
            0.022097087f,
            1.5625000000E-02f,
            0.011048543f,
            7.8125000000E-03f,
            0.0055242716f,
            3.9062500000E-03f,
            0.0027621358f,
            1.9531250000E-03f,
            0.0013810679f,
            9.7656250000E-04f,
            6.9053395E-4f,
            4.8828125000E-04f,
            3.4526698E-4f,
            2.4414062E-4f,
            1.7263349E-4f,
            1.2207031E-4f,
            8.6316744E-5f,
            6.1035156E-5f,
            4.3158372E-5f,
            3.0517578E-5f,
            2.1579186E-5f,
            1.5258789E-5f,
            1.0789593E-5f,
            7.6293945E-6f,
            5.3947965E-6f,
            3.8146973E-6f,
            2.6973983E-6f,
            1.9073486E-6f,
            1.3486991E-6f,
            9.536743E-7f,
            6.7434956E-7f,
            4.7683716E-7f,
            3.3717478E-7f,
            2.3841858E-7f,
            1.6858739E-7f,
            1.1920929E-7f,
            8.4293696E-8f,
            5.9604645E-8f,
            4.2146848E-8f,
            2.9802322E-8f,
            2.1073424E-8f,
            1.4901161E-8f,
            1.0536712E-8f,
            7.4505806E-9f,
            5.268356E-9f,
            3.7252903E-9f,
            2.634178E-9f,
            1.8626451E-9f,
            1.317089E-9f,
            9.313226E-10f,
            6.585445E-10f,
            4.656613E-10f,
            3.2927225E-10f
        )

        val t_43: FloatArray = create_t_43()

        private fun create_t_43(): FloatArray {
            val t43 = FloatArray(8192)
            val d43 = 4.0 / 3.0

            for (i in 0..8191) t43[i] = i.toFloat().pow(d43.toFloat())
            return t43
        }

        val io: Array<FloatArray> = arrayOf(
            floatArrayOf(
                1.0000000000E+00f,
                0.8408964f,
                0.70710677f,
                0.59460354f,
                0.5f,
                0.4204482f,
                0.35355338f,
                0.29730177f,
                0.25f,
                0.2102241f,
                0.17677669f,
                0.14865088f,
                1.2500000000E-01f,
                0.10511205f,
                0.088388346f,
                0.07432544f,
                0.0625f,
                0.052556027f,
                0.044194173f,
                0.03716272f,
                0.03125f,
                0.026278013f,
                0.022097087f,
                0.01858136f,
                0.015625f,
                0.013139007f,
                0.011048543f,
                0.00929068f,
                0.0078125f,
                0.0065695033f,
                0.0055242716f,
                0.00464534f
            ),
            floatArrayOf(
                1.0000000000E+00f,
                0.70710677f,
                5.0000000000E-01f,
                0.35355338f,
                2.5000000000E-01f,
                0.17677669f,
                1.2500000000E-01f,
                0.088388346f,
                0.0625f,
                0.044194173f,
                0.03125f,
                0.022097087f,
                1.5625000000E-02f,
                0.011048543f,
                0.0078125f,
                0.0055242716f,
                0.00390625f,
                0.0027621358f,
                0.001953125f,
                0.0013810679f,
                9.765625E-4f,
                6.9053395E-4f,
                4.8828125E-4f,
                3.4526698E-4f,
                2.4414062E-4f,
                1.7263349E-4f,
                1.2207031E-4f,
                8.6316744E-5f,
                6.1035156E-5f,
                4.3158372E-5f,
                3.0517578E-5f,
                2.1579186E-5f
            )
        )

        val TAN12: FloatArray = floatArrayOf(
            0.0f, 0.2679492f, 0.57735026f, 1.0f, 1.7320508f, 3.732051f, 9.9999998E10f,
            -3.732051f, -1.7320508f, -1.0f, -0.57735026f, -0.2679492f, 0.0f, 0.2679492f, 0.57735026f, 1.0f
        )

        private lateinit var reorderTable: Array<IntArray?>

        fun reorder(scalefacBand: IntArray): IntArray {
            var j = 0
            val ix = IntArray(576)
            for (sfb in 0..12) {
                val start = scalefacBand[sfb]
                val end = scalefacBand[sfb + 1]
                for (window in 0..2) for (i in start until end) ix[3 * i + window] = j++
            }
            return ix
        }

        private val cs = floatArrayOf(
            0.8574929f, 0.881742f, 0.94962865f, 0.9833146f, 0.9955178f,
            0.9991606f, 0.9998992f, 0.99999315f
        )

        private val ca = floatArrayOf(
            -0.51449573f, -0.47173196f, -0.31337744f, -0.1819132f,
            -0.09457419f, -0.040965583f, -0.014198569f, -0.0036999746f
        )

        val win: Array<FloatArray> = arrayOf(
            floatArrayOf(
                -0.016141215f,
                -0.05360318f,
                -0.100707136f,
                -0.16280818f,
                -0.5f,
                -0.38388735f,
                -0.6206114f,
                -1.1659756f,
                -3.8720753f,
                -4.225629f,
                -1.519529f,
                -0.97416484f,
                -0.73744076f,
                -1.2071068f,
                -0.5163616f,
                -0.45426053f,
                -0.40715656f,
                -0.3696946f,
                -0.3387627f,
                -0.31242222f,
                -0.28939587f,
                -0.26880082f,
                -0.5f,
                -0.23251417f,
                -0.21596715f,
                -0.20004979f,
                -0.18449493f,
                -0.16905846f,
                -0.15350361f,
                -0.13758625f,
                -0.12103922f,
                -0.20710678f,
                -0.084752575f,
                -0.06415752f,
                -0.041131172f,
                -0.014790705f
            ),

            floatArrayOf(
                -0.016141215f,
                -0.05360318f,
                -0.100707136f,
                -0.16280818f,
                -0.5f,
                -0.38388735f,
                -0.6206114f,
                -1.1659756f,
                -3.8720753f,
                -4.225629f,
                -1.519529f,
                -0.97416484f,
                -0.73744076f,
                -1.2071068f,
                -0.5163616f,
                -0.45426053f,
                -0.40715656f,
                -0.3696946f,
                -0.33908543f,
                -0.3151181f,
                -0.29642227f,
                -0.28184548f,
                -5.4119610000E-01f,
                -0.2621323f,
                -0.25387916f,
                -0.2329629f,
                -0.19852729f,
                -0.15233535f,
                -0.0964964f,
                -0.03342383f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f
            ),

            floatArrayOf(
                -0.0483008f,
                -0.15715657f,
                -0.28325045f,
                -0.42953748f,
                -1.2071068f,
                -0.8242648f,
                -1.1451749f,
                -1.769529f,
                -4.5470223f,
                -3.489053f,
                -0.7329629f,
                -0.15076515f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f
            ),

            floatArrayOf(
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                0.0000000000E+00f,
                -0.15076514f,
                -0.7329629f,
                -3.489053f,
                -4.5470223f,
                -1.769529f,
                -1.1451749f,
                -0.8313774f,
                -1.306563f,
                -0.54142016f,
                -0.46528974f,
                -0.4106699f,
                -0.3700468f,
                -0.3387627f,
                -0.31242222f,
                -0.28939587f,
                -0.26880082f,
                -0.5f,
                -0.23251417f,
                -0.21596715f,
                -0.20004979f,
                -0.18449493f,
                -0.16905846f,
                -0.15350361f,
                -0.13758625f,
                -0.12103922f,
                -0.20710678f,
                -0.084752575f,
                -0.06415752f,
                -0.041131172f,
                -0.014790705f
            )
        )

        val nr_of_sfb_block: Array<Array<IntArray>> = arrayOf(
            arrayOf(intArrayOf(6, 5, 5, 5), intArrayOf(9, 9, 9, 9), intArrayOf(6, 9, 9, 9)),
            arrayOf(intArrayOf(6, 5, 7, 3), intArrayOf(9, 9, 12, 6), intArrayOf(6, 9, 12, 6)),
            arrayOf(intArrayOf(11, 10, 0, 0), intArrayOf(18, 18, 0, 0), intArrayOf(15, 18, 0, 0)),
            arrayOf(intArrayOf(7, 7, 7, 0), intArrayOf(12, 12, 12, 0), intArrayOf(6, 15, 12, 0)),
            arrayOf(intArrayOf(6, 6, 6, 3), intArrayOf(12, 9, 9, 6), intArrayOf(6, 12, 9, 6)),
            arrayOf(intArrayOf(8, 8, 5, 0), intArrayOf(15, 12, 9, 0), intArrayOf(6, 18, 9, 0))
        )
    }
}