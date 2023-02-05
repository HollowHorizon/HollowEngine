package ru.hollowhorizon.hollowstory.client.screen

import org.lwjgl.BufferUtils
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.abs

object GLU {
    private val inArray = FloatArray(4)
    private val outArray = FloatArray(4)
    private val finalMatrix = BufferUtils.createFloatBuffer(16)
    private val tempMatrix = BufferUtils.createFloatBuffer(16)
    private val IDENTITY_MATRIX =
        floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f)

    fun gluUnProject(
        winx: Float,
        winy: Float,
        winz: Float,
        modelMatrix: FloatBuffer,
        projMatrix: FloatBuffer,
        viewport: IntBuffer,
        obj_pos: FloatBuffer,
    ): Boolean {
        __gluMultMatricesf(modelMatrix, projMatrix, finalMatrix)
        return if (!__gluInvertMatrixf(finalMatrix, finalMatrix)) {
            false
        } else {
            inArray[0] = winx
            inArray[1] = winy
            inArray[2] = winz
            inArray[3] = 1.0f
            inArray[0] =
                (inArray[0] - viewport[viewport.position() + 0].toFloat()) / viewport[viewport.position() + 2].toFloat()
            inArray[1] =
                (inArray[1] - viewport[viewport.position() + 1].toFloat()) / viewport[viewport.position() + 3].toFloat()
            inArray[0] = inArray[0] * 2.0f - 1.0f
            inArray[1] = inArray[1] * 2.0f - 1.0f
            inArray[2] = inArray[2] * 2.0f - 1.0f
            __gluMultMatrixVecf(finalMatrix, inArray, outArray)
            if (outArray[3].toDouble() == 0.0) {
                false
            } else {
                outArray[3] = 1.0f / outArray[3]
                obj_pos.put(obj_pos.position() + 0, outArray[0] * outArray[3])
                obj_pos.put(obj_pos.position() + 1, outArray[1] * outArray[3])
                obj_pos.put(obj_pos.position() + 2, outArray[2] * outArray[3])
                true
            }
        }
    }

    private fun __gluMultMatricesf(a: FloatBuffer, b: FloatBuffer, r: FloatBuffer) {
        for (i in 0..3) {
            for (j in 0..3) {
                r.put(
                    r.position() + i * 4 + j,
                    a[a.position() + i * 4 + 0] * b[b.position() + 0 + j] + a[a.position() + i * 4 + 1] * b[b.position() + 4 + j] + a[a.position() + i * 4 + 2] * b[b.position() + 8 + j] + a[a.position() + i * 4 + 3] * b[b.position() + 12 + j]
                )
            }
        }
    }

    private fun __gluInvertMatrixf(src: FloatBuffer, inverse: FloatBuffer): Boolean {
        val temp = tempMatrix
        var i: Int
        i = 0
        while (i < 16) {
            temp.put(i, src[i + src.position()])
            ++i
        }
        __gluMakeIdentityf(inverse)
        i = 0
        while (i < 4) {
            var swap = i
            var j: Int
            j = i + 1
            while (j < 4) {
                if (abs(temp[j * 4 + i]) > abs(temp[i * 4 + i])) {
                    swap = j
                }
                ++j
            }
            var k: Int
            var t: Float
            if (swap != i) {
                k = 0
                while (k < 4) {
                    t = temp[i * 4 + k]
                    temp.put(i * 4 + k, temp[swap * 4 + k])
                    temp.put(swap * 4 + k, t)
                    t = inverse[i * 4 + k]
                    inverse.put(i * 4 + k, inverse[swap * 4 + k])
                    inverse.put(swap * 4 + k, t)
                    ++k
                }
            }
            if (temp[i * 4 + i] == 0.0f) {
                return false
            }
            t = temp[i * 4 + i]
            k = 0
            while (k < 4) {
                temp.put(i * 4 + k, temp[i * 4 + k] / t)
                inverse.put(i * 4 + k, inverse[i * 4 + k] / t)
                ++k
            }
            j = 0
            while (j < 4) {
                if (j != i) {
                    t = temp[j * 4 + i]
                    k = 0
                    while (k < 4) {
                        temp.put(j * 4 + k, temp[j * 4 + k] - temp[i * 4 + k] * t)
                        inverse.put(j * 4 + k, inverse[j * 4 + k] - inverse[i * 4 + k] * t)
                        ++k
                    }
                }
                ++j
            }
            ++i
        }
        return true
    }

    private fun __gluMultMatrixVecf(m: FloatBuffer, inArray: FloatArray, outArray: FloatArray) {
        for (i in 0..3) {
            outArray[i] =
                inArray[0] * m[m.position() + 0 + i] + inArray[1] * m[m.position() + 4 + i] + inArray[2] * m[m.position() + 8 + i] + inArray[3] * m[m.position() + 12 + i]
        }
    }

    private fun __gluMakeIdentityf(m: FloatBuffer) {
        val oldPos = m.position()
        m.put(IDENTITY_MATRIX)
        m.position(oldPos)
    }
}