package ru.hollowhorizon.hc.client.audio.decoder

import java.io.*
import kotlin.math.cos

class SynthesisFilter(channelnumber: Int, factor: Float) {
    private val v1: FloatArray
    private val v2: FloatArray
    private lateinit var actualV: FloatArray
    private var actualWritePos = 0
    private val samples: FloatArray
    private val channel: Int
    private val scalefactor: Float

    private fun reset() {
        for (p in 0..511) {
            v2[p] = 0.0f
            v1[p] = v2[p]
        }

        for (p2 in 0..31) samples[p2] = 0.0f

        actualV = v1
        actualWritePos = 15
    }

    fun inputSample(sample: Float, subbandnumber: Int) {
        samples[subbandnumber] = sample
    }

    fun inputSamples(s: FloatArray) {
        System.arraycopy(s, 0, samples, 0, 32)
    }

    private fun computeNewV() {
        val newV0: Float
        var newV1: Float
        var newV2: Float
        var newV3: Float
        var newV4: Float
        val newV5: Float
        val newV6: Float
        val newV7: Float
        var newV8: Float
        var newV9: Float
        var newV10: Float
        var newV11: Float
        var newV12: Float
        var newV13: Float
        var newV14: Float
        var newV15: Float
        val newV16: Float
        val newV17: Float
        val newV18: Float
        val newV19: Float
        val newV20: Float
        val newV21: Float
        val newV22: Float
        val newV23: Float
        val newV24: Float
        val newV25: Float
        val newV26: Float
        val newV27: Float
        val newV28: Float
        val newV29: Float
        val newV30: Float
        val newV31: Float
        val s = samples

        val s0 = s[0]
        val s1 = s[1]
        val s2 = s[2]
        val s3 = s[3]
        val s4 = s[4]
        val s5 = s[5]
        val s6 = s[6]
        val s7 = s[7]
        val s8 = s[8]
        val s9 = s[9]
        val s10 = s[10]
        val s11 = s[11]
        val s12 = s[12]
        val s13 = s[13]
        val s14 = s[14]
        val s15 = s[15]
        val s16 = s[16]
        val s17 = s[17]
        val s18 = s[18]
        val s19 = s[19]
        val s20 = s[20]
        val s21 = s[21]
        val s22 = s[22]
        val s23 = s[23]
        val s24 = s[24]
        val s25 = s[25]
        val s26 = s[26]
        val s27 = s[27]
        val s28 = s[28]
        val s29 = s[29]
        val s30 = s[30]
        val s31 = s[31]

        var p0 = s0 + s31
        var p1 = s1 + s30
        var p2 = s2 + s29
        var p3 = s3 + s28
        var p4 = s4 + s27
        var p5 = s5 + s26
        var p6 = s6 + s25
        var p7 = s7 + s24
        var p8 = s8 + s23
        var p9 = s9 + s22
        var p10 = s10 + s21
        var p11 = s11 + s20
        var p12 = s12 + s19
        var p13 = s13 + s18
        var p14 = s14 + s17
        var p15 = s15 + s16

        var pp0 = p0 + p15
        var pp1 = p1 + p14
        var pp2 = p2 + p13
        var pp3 = p3 + p12
        var pp4 = p4 + p11
        var pp5 = p5 + p10
        var pp6 = p6 + p9
        var pp7 = p7 + p8
        var pp8 = (p0 - p15) * cos1_32
        var pp9 = (p1 - p14) * cos3_32
        var pp10 = (p2 - p13) * cos5_32
        var pp11 = (p3 - p12) * cos7_32
        var pp12 = (p4 - p11) * cos9_32
        var pp13 = (p5 - p10) * cos11_32
        var pp14 = (p6 - p9) * cos13_32
        var pp15 = (p7 - p8) * cos15_32

        p0 = pp0 + pp7
        p1 = pp1 + pp6
        p2 = pp2 + pp5
        p3 = pp3 + pp4
        p4 = (pp0 - pp7) * cos1_16
        p5 = (pp1 - pp6) * cos3_16
        p6 = (pp2 - pp5) * cos5_16
        p7 = (pp3 - pp4) * cos7_16
        p8 = pp8 + pp15
        p9 = pp9 + pp14
        p10 = pp10 + pp13
        p11 = pp11 + pp12
        p12 = (pp8 - pp15) * cos1_16
        p13 = (pp9 - pp14) * cos3_16
        p14 = (pp10 - pp13) * cos5_16
        p15 = (pp11 - pp12) * cos7_16

        pp0 = p0 + p3
        pp1 = p1 + p2
        pp2 = (p0 - p3) * cos1_8
        pp3 = (p1 - p2) * cos3_8
        pp4 = p4 + p7
        pp5 = p5 + p6
        pp6 = (p4 - p7) * cos1_8
        pp7 = (p5 - p6) * cos3_8
        pp8 = p8 + p11
        pp9 = p9 + p10
        pp10 = (p8 - p11) * cos1_8
        pp11 = (p9 - p10) * cos3_8
        pp12 = p12 + p15
        pp13 = p13 + p14
        pp14 = (p12 - p15) * cos1_8
        pp15 = (p13 - p14) * cos3_8

        p0 = pp0 + pp1
        p1 = (pp0 - pp1) * cos1_4
        p2 = pp2 + pp3
        p3 = (pp2 - pp3) * cos1_4
        p4 = pp4 + pp5
        p5 = (pp4 - pp5) * cos1_4
        p6 = pp6 + pp7
        p7 = (pp6 - pp7) * cos1_4
        p8 = pp8 + pp9
        p9 = (pp8 - pp9) * cos1_4
        p10 = pp10 + pp11
        p11 = (pp10 - pp11) * cos1_4
        p12 = pp12 + pp13
        p13 = (pp12 - pp13) * cos1_4
        p14 = pp14 + pp15
        p15 = (pp14 - pp15) * cos1_4

        var tmp1: Float
        newV19 = -(((p7.also { newV12 = it }) + p5).also { newV4 = it }) - p6
        newV27 = -p6 - p7 - p4
        newV6 = (((p15.also { newV14 = it }) + p11).also { newV10 = it }) + p13
        newV17 = -((p15 + p13 + p9).also { newV2 = it }) - p14
        newV21 = ((-p14 - p15 - p10 - p11).also { tmp1 = it }) - p13
        newV29 = -p14 - p15 - p12 - p8
        newV25 = tmp1 - p12
        newV31 = -p0
        newV0 = p1
        newV23 = -(p3.also { newV8 = it }) - p2

        p0 = (s0 - s31) * cos1_64
        p1 = (s1 - s30) * cos3_64
        p2 = (s2 - s29) * cos5_64
        p3 = (s3 - s28) * cos7_64
        p4 = (s4 - s27) * cos9_64
        p5 = (s5 - s26) * cos11_64
        p6 = (s6 - s25) * cos13_64
        p7 = (s7 - s24) * cos15_64
        p8 = (s8 - s23) * cos17_64
        p9 = (s9 - s22) * cos19_64
        p10 = (s10 - s21) * cos21_64
        p11 = (s11 - s20) * cos23_64
        p12 = (s12 - s19) * cos25_64
        p13 = (s13 - s18) * cos27_64
        p14 = (s14 - s17) * cos29_64
        p15 = (s15 - s16) * cos31_64

        pp0 = p0 + p15
        pp1 = p1 + p14
        pp2 = p2 + p13
        pp3 = p3 + p12
        pp4 = p4 + p11
        pp5 = p5 + p10
        pp6 = p6 + p9
        pp7 = p7 + p8
        pp8 = (p0 - p15) * cos1_32
        pp9 = (p1 - p14) * cos3_32
        pp10 = (p2 - p13) * cos5_32
        pp11 = (p3 - p12) * cos7_32
        pp12 = (p4 - p11) * cos9_32
        pp13 = (p5 - p10) * cos11_32
        pp14 = (p6 - p9) * cos13_32
        pp15 = (p7 - p8) * cos15_32

        p0 = pp0 + pp7
        p1 = pp1 + pp6
        p2 = pp2 + pp5
        p3 = pp3 + pp4
        p4 = (pp0 - pp7) * cos1_16
        p5 = (pp1 - pp6) * cos3_16
        p6 = (pp2 - pp5) * cos5_16
        p7 = (pp3 - pp4) * cos7_16
        p8 = pp8 + pp15
        p9 = pp9 + pp14
        p10 = pp10 + pp13
        p11 = pp11 + pp12
        p12 = (pp8 - pp15) * cos1_16
        p13 = (pp9 - pp14) * cos3_16
        p14 = (pp10 - pp13) * cos5_16
        p15 = (pp11 - pp12) * cos7_16

        pp0 = p0 + p3
        pp1 = p1 + p2
        pp2 = (p0 - p3) * cos1_8
        pp3 = (p1 - p2) * cos3_8
        pp4 = p4 + p7
        pp5 = p5 + p6
        pp6 = (p4 - p7) * cos1_8
        pp7 = (p5 - p6) * cos3_8
        pp8 = p8 + p11
        pp9 = p9 + p10
        pp10 = (p8 - p11) * cos1_8
        pp11 = (p9 - p10) * cos3_8
        pp12 = p12 + p15
        pp13 = p13 + p14
        pp14 = (p12 - p15) * cos1_8
        pp15 = (p13 - p14) * cos3_8

        p0 = pp0 + pp1
        p1 = (pp0 - pp1) * cos1_4
        p2 = pp2 + pp3
        p3 = (pp2 - pp3) * cos1_4
        p4 = pp4 + pp5
        p5 = (pp4 - pp5) * cos1_4
        p6 = pp6 + pp7
        p7 = (pp6 - pp7) * cos1_4
        p8 = pp8 + pp9
        p9 = (pp8 - pp9) * cos1_4
        p10 = pp10 + pp11
        p11 = (pp10 - pp11) * cos1_4
        p12 = pp12 + pp13
        p13 = (pp12 - pp13) * cos1_4
        p14 = pp14 + pp15
        p15 = (pp14 - pp15) * cos1_4

        var tmp2: Float
        newV5 = (((((p15.also { newV15 = it }) + p7).also { newV13 = it }) + p11).also { newV11 = it }) + p5 + p13
        newV7 = ((p15 + p11 + p3).also { newV9 = it }) + p13
        newV16 = -((((p13 + p15 + p9).also { tmp1 = it }) + p1).also { newV1 = it }) - p14
        newV18 = -((tmp1 + p5 + p7).also { newV3 = it }) - p6 - p14

        newV22 = ((-p10 - p11 - p14 - p15).also { tmp1 = it }) - p13 - p2 - p3
        newV20 = tmp1 - p13 - p5 - p6 - p7
        newV24 = tmp1 - p12 - p2 - p3
        newV26 = tmp1 - p12 - ((p4 + p6 + p7).also { tmp2 = it })
        newV30 = ((-p8 - p12 - p14 - p15).also { tmp1 = it }) - p0
        newV28 = tmp1 - tmp2

        var dest = actualV
        val pos = actualWritePos

        dest[pos] = newV0
        dest[16 + pos] = newV1
        dest[32 + pos] = newV2
        dest[48 + pos] = newV3
        dest[64 + pos] = newV4
        dest[80 + pos] = newV5
        dest[96 + pos] = newV6
        dest[112 + pos] = newV7
        dest[128 + pos] = newV8
        dest[144 + pos] = newV9
        dest[160 + pos] = newV10
        dest[176 + pos] = newV11
        dest[192 + pos] = newV12
        dest[208 + pos] = newV13
        dest[224 + pos] = newV14
        dest[240 + pos] = newV15

        dest[256 + pos] = 0.0f

        dest[272 + pos] = -newV15
        dest[288 + pos] = -newV14
        dest[304 + pos] = -newV13
        dest[320 + pos] = -newV12
        dest[336 + pos] = -newV11
        dest[352 + pos] = -newV10
        dest[368 + pos] = -newV9
        dest[384 + pos] = -newV8
        dest[400 + pos] = -newV7
        dest[416 + pos] = -newV6
        dest[432 + pos] = -newV5
        dest[448 + pos] = -newV4
        dest[464 + pos] = -newV3
        dest[480 + pos] = -newV2
        dest[496 + pos] = -newV1
        dest = if (actualV.contentEquals(v1)) v2 else v1

        dest[pos] = -newV0
        dest[16 + pos] = newV16
        dest[32 + pos] = newV17
        dest[48 + pos] = newV18
        dest[64 + pos] = newV19
        dest[80 + pos] = newV20
        dest[96 + pos] = newV21
        dest[112 + pos] = newV22
        dest[128 + pos] = newV23
        dest[144 + pos] = newV24
        dest[160 + pos] = newV25
        dest[176 + pos] = newV26
        dest[192 + pos] = newV27
        dest[208 + pos] = newV28
        dest[224 + pos] = newV29
        dest[240 + pos] = newV30
        dest[256 + pos] = newV31

        dest[272 + pos] = newV30
        dest[288 + pos] = newV29
        dest[304 + pos] = newV28
        dest[320 + pos] = newV27
        dest[336 + pos] = newV26
        dest[352 + pos] = newV25
        dest[368 + pos] = newV24
        dest[384 + pos] = newV23
        dest[400 + pos] = newV22
        dest[416 + pos] = newV21
        dest[432 + pos] = newV20
        dest[448 + pos] = newV19
        dest[464 + pos] = newV18
        dest[480 + pos] = newV17
        dest[496 + pos] = newV16
    }

    private val _tmpOut = FloatArray(32)

    private fun computePcmSamples0() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]
            _tmpOut[i] =
                ((vp[dvp] * dp!![0] + vp[15 + dvp] * dp[1] + vp[14 + dvp] * dp[2] + vp[13 + dvp] * dp[3] + vp[12 + dvp]
                        * dp[4] + vp[11 + dvp] * dp[5] + vp[10 + dvp] * dp[6] + vp[9 + dvp] * dp[7] + vp[8 + dvp] * dp[8] + vp[7 + dvp]
                        * dp[9] + vp[6 + dvp] * dp[10] + vp[5 + dvp] * dp[11] + vp[4 + dvp] * dp[12] + vp[3 + dvp] * dp[13] + vp[2 + dvp]
                        * dp[14] + vp[1 + dvp] * dp[15]) * scalefactor)

            dvp += 16
        }
    }

    private fun computePcmSamples1() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[1 + dvp] * dp!![0] + vp[dvp] * dp[1] + vp[15 + dvp] * dp[2] + vp[14 + dvp] * dp[3] + vp[13 + dvp]
                        * dp[4] + vp[12 + dvp] * dp[5] + vp[11 + dvp] * dp[6] + vp[10 + dvp] * dp[7] + vp[9 + dvp] * dp[8] + vp[8 + dvp]
                        * dp[9] + vp[7 + dvp] * dp[10] + vp[6 + dvp] * dp[11] + vp[5 + dvp] * dp[12] + vp[4 + dvp] * dp[13] + vp[3 + dvp]
                        * dp[14] + vp[2 + dvp] * dp[15]) * scalefactor



            dvp += 16
        }
    }

    private fun computePcmSamples2() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[2 + dvp] * dp!![0] + vp[1 + dvp] * dp[1] + vp[dvp] * dp[2] + vp[15 + dvp] * dp[3] + vp[14 + dvp]
                        * dp[4] + vp[13 + dvp] * dp[5] + vp[12 + dvp] * dp[6] + vp[11 + dvp] * dp[7] + vp[10 + dvp] * dp[8] + vp[9 + dvp]
                        * dp[9] + vp[8 + dvp] * dp[10] + vp[7 + dvp] * dp[11] + vp[6 + dvp] * dp[12] + vp[5 + dvp] * dp[13] + vp[4 + dvp]
                        * dp[14] + vp[3 + dvp] * dp[15]) * scalefactor


            dvp += 16
        }
    }

    private fun computePcmSamples3() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[3 + dvp] * dp!![0] + vp[2 + dvp] * dp[1] + vp[1 + dvp] * dp[2] + vp[dvp] * dp[3] + vp[15 + dvp]
                        * dp[4] + vp[14 + dvp] * dp[5] + vp[13 + dvp] * dp[6] + vp[12 + dvp] * dp[7] + vp[11 + dvp] * dp[8] + vp[10 + dvp]
                        * dp[9] + vp[9 + dvp] * dp[10] + vp[8 + dvp] * dp[11] + vp[7 + dvp] * dp[12] + vp[6 + dvp] * dp[13] + vp[5 + dvp]
                        * dp[14] + vp[4 + dvp] * dp[15]) * scalefactor


            dvp += 16
        }
    }

    private fun computePcmSamples4() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[4 + dvp] * dp!![0] + vp[3 + dvp] * dp[1] + vp[2 + dvp] * dp[2] + vp[1 + dvp] * dp[3] + vp[dvp]
                        * dp[4] + vp[15 + dvp] * dp[5] + vp[14 + dvp] * dp[6] + vp[13 + dvp] * dp[7] + vp[12 + dvp] * dp[8] + vp[11 + dvp]
                        * dp[9] + vp[10 + dvp] * dp[10] + vp[9 + dvp] * dp[11] + vp[8 + dvp] * dp[12] + vp[7 + dvp] * dp[13] + vp[6 + dvp]
                        * dp[14] + vp[5 + dvp] * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples5() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[5 + dvp] * dp!![0] + vp[4 + dvp] * dp[1] + vp[3 + dvp] * dp[2] + vp[2 + dvp] * dp[3] + vp[1 + dvp]
                        * dp[4] + vp[dvp] * dp[5] + vp[15 + dvp] * dp[6] + vp[14 + dvp] * dp[7] + vp[13 + dvp] * dp[8] + vp[12 + dvp]
                        * dp[9] + vp[11 + dvp] * dp[10] + vp[10 + dvp] * dp[11] + vp[9 + dvp] * dp[12] + vp[8 + dvp] * dp[13] + vp[7 + dvp]
                        * dp[14] + vp[6 + dvp] * dp[15]) * scalefactor


            dvp += 16
        }
    }

    private fun computePcmSamples6() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[6 + dvp] * dp!![0] + vp[5 + dvp] * dp[1] + vp[4 + dvp] * dp[2] + vp[3 + dvp] * dp[3] + vp[2 + dvp]
                        * dp[4] + vp[1 + dvp] * dp[5] + vp[dvp] * dp[6] + vp[15 + dvp] * dp[7] + vp[14 + dvp] * dp[8] + vp[13 + dvp]
                        * dp[9] + vp[12 + dvp] * dp[10] + vp[11 + dvp] * dp[11] + vp[10 + dvp] * dp[12] + vp[9 + dvp] * dp[13] + vp[8 + dvp]
                        * dp[14] + vp[7 + dvp] * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples7() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[7 + dvp] * dp!![0] + vp[6 + dvp] * dp[1] + vp[5 + dvp] * dp[2] + vp[4 + dvp] * dp[3] + vp[3 + dvp]
                        * dp[4] + vp[2 + dvp] * dp[5] + vp[1 + dvp] * dp[6] + vp[dvp] * dp[7] + vp[15 + dvp] * dp[8] + vp[14 + dvp]
                        * dp[9] + vp[13 + dvp] * dp[10] + vp[12 + dvp] * dp[11] + vp[11 + dvp] * dp[12] + vp[10 + dvp] * dp[13] + vp[9 + dvp]
                        * dp[14] + vp[8 + dvp] * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples8() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[8 + dvp] * dp!![0] + vp[7 + dvp] * dp[1] + vp[6 + dvp] * dp[2] + vp[5 + dvp] * dp[3] + vp[4 + dvp]
                        * dp[4] + vp[3 + dvp] * dp[5] + vp[2 + dvp] * dp[6] + vp[1 + dvp] * dp[7] + vp[dvp] * dp[8] + vp[15 + dvp]
                        * dp[9] + vp[14 + dvp] * dp[10] + vp[13 + dvp] * dp[11] + vp[12 + dvp] * dp[12] + vp[11 + dvp] * dp[13]
                        + vp[10 + dvp] * dp[14] + vp[9 + dvp] * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples9() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[9 + dvp] * dp!![0] + vp[8 + dvp] * dp[1] + vp[7 + dvp] * dp[2] + vp[6 + dvp] * dp[3] + vp[5 + dvp]
                        * dp[4] + vp[4 + dvp] * dp[5] + vp[3 + dvp] * dp[6] + vp[2 + dvp] * dp[7] + vp[1 + dvp] * dp[8] + vp[dvp] * dp[9]
                        + vp[15 + dvp] * dp[10] + vp[14 + dvp] * dp[11] + vp[13 + dvp] * dp[12] + vp[12 + dvp] * dp[13] + vp[11 + dvp]
                        * dp[14] + vp[10 + dvp] * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples10() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[10 + dvp] * dp!![0] + vp[9 + dvp] * dp[1] + vp[8 + dvp] * dp[2] + vp[7 + dvp] * dp[3] + vp[6 + dvp]
                        * dp[4] + vp[5 + dvp] * dp[5] + vp[4 + dvp] * dp[6] + vp[3 + dvp] * dp[7] + vp[2 + dvp] * dp[8] + vp[1 + dvp] * dp[9]
                        + vp[dvp] * dp[10] + vp[15 + dvp] * dp[11] + vp[14 + dvp] * dp[12] + vp[13 + dvp] * dp[13] + vp[12 + dvp]
                        * dp[14] + vp[11 + dvp] * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples11() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[11 + dvp] * dp!![0] + vp[10 + dvp] * dp[1] + vp[9 + dvp] * dp[2] + vp[8 + dvp] * dp[3] + vp[7 + dvp]
                        * dp[4] + vp[6 + dvp] * dp[5] + vp[5 + dvp] * dp[6] + vp[4 + dvp] * dp[7] + vp[3 + dvp] * dp[8] + vp[2 + dvp] * dp[9]
                        + vp[1 + dvp] * dp[10] + vp[dvp] * dp[11] + vp[15 + dvp] * dp[12] + vp[14 + dvp] * dp[13] + vp[13 + dvp] * dp[14] + vp[12 + dvp]
                        * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples12() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[12 + dvp] * dp!![0] + vp[11 + dvp] * dp[1] + vp[10 + dvp] * dp[2] + vp[9 + dvp] * dp[3] + vp[8 + dvp]
                        * dp[4] + vp[7 + dvp] * dp[5] + vp[6 + dvp] * dp[6] + vp[5 + dvp] * dp[7] + vp[4 + dvp] * dp[8] + vp[3 + dvp] * dp[9]
                        + vp[2 + dvp] * dp[10] + vp[1 + dvp] * dp[11] + vp[dvp] * dp[12] + vp[15 + dvp] * dp[13] + vp[14 + dvp] * dp[14] + vp[13 + dvp]
                        * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples13() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[13 + dvp] * dp!![0] + vp[12 + dvp] * dp[1] + vp[11 + dvp] * dp[2] + vp[10 + dvp] * dp[3] + vp[9 + dvp]
                        * dp[4] + vp[8 + dvp] * dp[5] + vp[7 + dvp] * dp[6] + vp[6 + dvp] * dp[7] + vp[5 + dvp] * dp[8] + vp[4 + dvp] * dp[9]
                        + vp[3 + dvp] * dp[10] + vp[2 + dvp] * dp[11] + vp[1 + dvp] * dp[12] + vp[dvp] * dp[13] + vp[15 + dvp] * dp[14] + vp[14 + dvp]
                        * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples14() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]

            _tmpOut[i] =
                (vp[14 + dvp] * dp!![0] + vp[13 + dvp] * dp[1] + vp[12 + dvp] * dp[2] + vp[11 + dvp] * dp[3] + vp[10 + dvp]
                        * dp[4] + vp[9 + dvp] * dp[5] + vp[8 + dvp] * dp[6] + vp[7 + dvp] * dp[7] + vp[6 + dvp] * dp[8] + vp[5 + dvp] * dp[9]
                        + vp[4 + dvp] * dp[10] + vp[3 + dvp] * dp[11] + vp[2 + dvp] * dp[12] + vp[1 + dvp] * dp[13] + vp[dvp] * dp[14] + vp[15 + dvp]
                        * dp[15]) * scalefactor

            dvp += 16
        }
    }

    private fun computePcmSamples15() {
        val vp = actualV
        var dvp = 0
        for (i in 0..31) {
            val dp = d16!![i]
            _tmpOut[i] =
                ((vp[15 + dvp] * dp!![0] + vp[14 + dvp] * dp[1] + vp[13 + dvp] * dp[2] + vp[12 + dvp] * dp[3] + vp[11 + dvp]
                        * dp[4] + vp[10 + dvp] * dp[5] + vp[9 + dvp] * dp[6] + vp[8 + dvp] * dp[7] + vp[7 + dvp] * dp[8] + vp[6 + dvp]
                        * dp[9] + vp[5 + dvp] * dp[10] + vp[4 + dvp] * dp[11] + vp[3 + dvp] * dp[12] + vp[2 + dvp] * dp[13] + vp[1 + dvp]
                        * dp[14] + vp[dvp] * dp[15]) * scalefactor)

            dvp += 16
        }
    }

    private fun computePcmSamples(buffer: OutputBuffer) {
        when (actualWritePos) {
            0 -> computePcmSamples0()
            1 -> computePcmSamples1()
            2 -> computePcmSamples2()
            3 -> computePcmSamples3()
            4 -> computePcmSamples4()
            5 -> computePcmSamples5()
            6 -> computePcmSamples6()
            7 -> computePcmSamples7()
            8 -> computePcmSamples8()
            9 -> computePcmSamples9()
            10 -> computePcmSamples10()
            11 -> computePcmSamples11()
            12 -> computePcmSamples12()
            13 -> computePcmSamples13()
            14 -> computePcmSamples14()
            15 -> computePcmSamples15()
        }
        buffer.appendSamples(channel, _tmpOut)
    }

    fun calculatePcmSamples(buffer: OutputBuffer) {
        computeNewV()
        computePcmSamples(buffer)

        actualWritePos = actualWritePos + 1 and 0xf
        actualV = if (actualV.contentEquals(v1)) v2 else v1
        for (p in 0..31) samples[p] = 0.0f
    }

    init {
        if (d == null) {
            d = loadD()
            d16 = splitArray(d, 16)
        }

        v1 = FloatArray(512)
        v2 = FloatArray(512)
        samples = FloatArray(32)
        channel = channelnumber
        scalefactor = factor

        reset()
    }

    companion object {
        private const val MY_PI = 3.141592653589793
        private val cos1_64 = (1.0 / (2.0 * cos(MY_PI / 64.0))).toFloat()
        private val cos3_64 = (1.0 / (2.0 * cos(MY_PI * 3.0 / 64.0))).toFloat()
        private val cos5_64 = (1.0 / (2.0 * cos(MY_PI * 5.0 / 64.0))).toFloat()
        private val cos7_64 = (1.0 / (2.0 * cos(MY_PI * 7.0 / 64.0))).toFloat()
        private val cos9_64 = (1.0 / (2.0 * cos(MY_PI * 9.0 / 64.0))).toFloat()
        private val cos11_64 = (1.0 / (2.0 * cos(MY_PI * 11.0 / 64.0))).toFloat()
        private val cos13_64 = (1.0 / (2.0 * cos(MY_PI * 13.0 / 64.0))).toFloat()
        private val cos15_64 = (1.0 / (2.0 * cos(MY_PI * 15.0 / 64.0))).toFloat()
        private val cos17_64 = (1.0 / (2.0 * cos(MY_PI * 17.0 / 64.0))).toFloat()
        private val cos19_64 = (1.0 / (2.0 * cos(MY_PI * 19.0 / 64.0))).toFloat()
        private val cos21_64 = (1.0 / (2.0 * cos(MY_PI * 21.0 / 64.0))).toFloat()
        private val cos23_64 = (1.0 / (2.0 * cos(MY_PI * 23.0 / 64.0))).toFloat()
        private val cos25_64 = (1.0 / (2.0 * cos(MY_PI * 25.0 / 64.0))).toFloat()
        private val cos27_64 = (1.0 / (2.0 * cos(MY_PI * 27.0 / 64.0))).toFloat()
        private val cos29_64 = (1.0 / (2.0 * cos(MY_PI * 29.0 / 64.0))).toFloat()
        private val cos31_64 = (1.0 / (2.0 * cos(MY_PI * 31.0 / 64.0))).toFloat()
        private val cos1_32 = (1.0 / (2.0 * cos(MY_PI / 32.0))).toFloat()
        private val cos3_32 = (1.0 / (2.0 * cos(MY_PI * 3.0 / 32.0))).toFloat()
        private val cos5_32 = (1.0 / (2.0 * cos(MY_PI * 5.0 / 32.0))).toFloat()
        private val cos7_32 = (1.0 / (2.0 * cos(MY_PI * 7.0 / 32.0))).toFloat()
        private val cos9_32 = (1.0 / (2.0 * cos(MY_PI * 9.0 / 32.0))).toFloat()
        private val cos11_32 = (1.0 / (2.0 * cos(MY_PI * 11.0 / 32.0))).toFloat()
        private val cos13_32 = (1.0 / (2.0 * cos(MY_PI * 13.0 / 32.0))).toFloat()
        private val cos15_32 = (1.0 / (2.0 * cos(MY_PI * 15.0 / 32.0))).toFloat()
        private val cos1_16 = (1.0 / (2.0 * cos(MY_PI / 16.0))).toFloat()
        private val cos3_16 = (1.0 / (2.0 * cos(MY_PI * 3.0 / 16.0))).toFloat()
        private val cos5_16 = (1.0 / (2.0 * cos(MY_PI * 5.0 / 16.0))).toFloat()
        private val cos7_16 = (1.0 / (2.0 * cos(MY_PI * 7.0 / 16.0))).toFloat()
        private val cos1_8 = (1.0 / (2.0 * cos(MY_PI / 8.0))).toFloat()
        private val cos3_8 = (1.0 / (2.0 * cos(MY_PI * 3.0 / 8.0))).toFloat()
        private val cos1_4 = (1.0 / (2.0 * cos(MY_PI / 4.0))).toFloat()

        private var d: FloatArray? = null

        private var d16: Array<FloatArray?>? = null

        private fun loadD(): FloatArray {
            try {
                val elemType: Class<*> = java.lang.Float.TYPE
                val o = deserializeArray(
                    SynthesisFilter::class.java.getResourceAsStream("/sfd.ser")
                        ?: throw IllegalStateException("Failed to deserialize SynthesisFilter."),
                    elemType,
                    512
                )
                return o as FloatArray
            } catch (ex: IOException) {
                throw ExceptionInInitializerError(ex)
            }
        }

        @Throws(IOException::class)
        private fun deserializeArray(`in`: InputStream, elemType: Class<*>?, length: Int): Any {
            if (elemType == null) throw NullPointerException("elemType")

            require(length >= -1) { "length" }

            val obj = deserialize(`in`)

            val cls: Class<*> = obj.javaClass

            if (!cls.isArray) throw InvalidObjectException("object is not an array")

            val arrayElemType = cls.componentType
            if (arrayElemType != elemType) throw InvalidObjectException("unexpected array component type")

            if (length != -1) {
                val arrayLength = java.lang.reflect.Array.getLength(obj)
                if (arrayLength != length) throw InvalidObjectException("array length mismatch")
            }

            return obj
        }

        @Throws(IOException::class)
        fun deserialize(`in`: InputStream?): Any {
            if (`in` == null) throw NullPointerException("in")

            val objIn = ObjectInputStream(`in`)

            val obj: Any

            try {
                obj = objIn.readObject()
            } catch (ex: ClassNotFoundException) {
                throw InvalidClassException(ex.toString())
            }
            return obj
        }

        private fun splitArray(array: FloatArray?, blockSize: Int): Array<FloatArray?> {
            val size = array!!.size / blockSize
            val split = arrayOfNulls<FloatArray>(size)
            for (i in 0 until size) split[i] = subArray(array, i * blockSize, blockSize)
            return split
        }

        private fun subArray(array: FloatArray?, offs: Int, len: Int): FloatArray {
            var length = len
            if (offs + length > array!!.size) length = array.size - offs

            if (length < 0) length = 0

            val subarray = FloatArray(length)
            System.arraycopy(array, offs, subarray, 0, length)
            return subarray
        }
    }
}
