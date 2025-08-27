/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hc.client.utils.math

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.GameRenderer
import org.joml.Vector3d
import org.joml.Vector3f
import ru.hollowhorizon.hc.client.render.OpenGLUtils
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt


class Spline(private val xx: DoubleArray, private val yy: DoubleArray) {
    private var a: DoubleArray
    private var b: DoubleArray
    private var c: DoubleArray
    private var d: DoubleArray

    private var storageIndex = 0

    init {
        if (xx.size < 2) throw IllegalArgumentException("Spline must have at least 2 points")

        val size = yy.size
        a = DoubleArray(size)
        b = DoubleArray(size)
        c = DoubleArray(size)
        d = DoubleArray(size)
        a[0] = yy[0]
        b[0] = yy[1] - yy[0]
        if (size > 2) {
            val h = DoubleArray(size - 1)
            for (i in 0 until size - 1) {
                a[i] = yy[i]
                h[i] = xx[i + 1] - xx[i]
                if (h[i] == 0.0) h[i] = 0.01
            }
            a[size - 1] = yy[size - 1]
            val array = Array(size - 2) { DoubleArray(size - 2) }
            val y = DoubleArray(size - 2)
            for (i in 0 until size - 2) {
                y[i] = (3 * ((yy[i + 2] - yy[i + 1]) / h[i + 1] - (yy[i + 1] - yy[i]) / h[i]))
                array[i][i] = 2 * (h[i] + h[i + 1])
                if (i > 0) array[i][i - 1] = h[i]
                if (i < size - 3) array[i][i + 1] = h[i + 1]
            }
            solve(array, y)
            for (i in 0 until size - 2) {
                c[i + 1] = y[i]
                b[i] = (a[i + 1] - a[i]) / h[i] - (2 * c[i] + c[i + 1]) / 3 * h[i]
                d[i] = (c[i + 1] - c[i]) / (3 * h[i])
            }
            b[size - 2] =
                ((a[size - 1] - a[size - 2]) / h[size - 2] - (2 * c[size - 2] + c[size - 1]) / 3 * h[size - 2])
            d[size - 2] = (c[size - 1] - c[size - 2]) / (3 * h[size - 2])
        }
    }

    fun getValue(x: Double): Double {
        if (xx.isEmpty()) return Double.NaN
        if (xx.size == 1) return if (xx[0] == x) yy[0] else Double.NaN
        var index: Int = Arrays.binarySearch(xx, x)
        if (index > 0) return yy[index]
        index = -(index + 1) - 1

        return if (index < 0) yy[0] else (a[index] + b[index] * (x - xx[index]) + c[index] * (x - xx[index]).pow(2.0) + d[index] * (x - xx[index]).pow(
            3.0
        ))
    }

    fun getFastValue(x: Double): Double {
        if (!(storageIndex > -1 && storageIndex < xx.size - 1 && x > xx[storageIndex] && x < xx[storageIndex + 1])) {
            var index: Int = Arrays.binarySearch(xx, x)
            if (index > 0) return yy[index]
            index = -(index + 1) - 1
            storageIndex = index
        }

        if (storageIndex < 0) return yy[0]
        val value = x - xx[storageIndex]
        return (a[storageIndex]
                + b[storageIndex] * value
                + c[storageIndex] * (value * value)
                + d[storageIndex] * (value * value * value))
    }

    fun checkValues(): Boolean {
        return xx.size >= 2
    }

    fun getDx(x: Double): Double {
        if (xx.isEmpty() || xx.size == 1) return 0.0

        var index = Arrays.binarySearch(xx, x)
        if (index < 0) index = -(index + 1) - 1
        return (b[index]
                + 2 * c[index] * (x - xx[index])
                + 3 * d[index] * (x - xx[index]).pow(2.0))
    }

    fun solve(A: Array<DoubleArray>, b: DoubleArray) {
        val n = b.size
        for (i in 1 until n) {
            A[i][i - 1] = A[i][i - 1] / A[i - 1][i - 1]
            A[i][i] = A[i][i] - A[i - 1][i] * A[i][i - 1]
            b[i] = b[i] - A[i][i - 1] * b[i - 1]
        }
        b[n - 1] = b[n - 1] / A[n - 1][n - 1]
        for (i in b.size - 2 downTo 0) b[i] = (b[i] - A[i][i + 1] * b[i + 1]) / A[i][i]
    }
}

class Spline3D(points: List<Vector3d>, rotations: List<Vector3f>) {
    private lateinit var t: DoubleArray
    private var splineX: Spline? = null
    private var splineY: Spline? = null
    private var splineZ: Spline? = null
    private var splineXR: Spline? = null
    private var splineYR: Spline? = null
    private var splineZR: Spline? = null


    var length = 0.0
        private set

    init {
        var x = points.map { it.x }.toDoubleArray()
        var y = points.map { it.y }.toDoubleArray()
        var z = points.map { it.z }.toDoubleArray()
        initPositions(x, y, z)
        x = rotations.map { it.x().toDouble() }.toDoubleArray()
        y = rotations.map { it.y().toDouble() }.toDoubleArray()
        z = rotations.map { it.z().toDouble() }.toDoubleArray()
        initRotations(x, y, z)
    }

    private fun initPositions(x: DoubleArray, y: DoubleArray, z: DoubleArray) {
        require(!(x.size != y.size || x.size != z.size || y.size != z.size)) { "Arrays must have the same length." }
        require(x.size >= 2) { "Spline edges must have at least two points." }
        t = DoubleArray(x.size)
        t[0] = 0.0

        for (i in 1 until t.size) {
            val lx = x[i] - x[i - 1]
            val ly = y[i] - y[i - 1]
            val lz = z[i] - z[i - 1]

            if (0.0 == lx) t[i] = abs(lz)
            else if (0.0 == lz) t[i] = abs(lx)
            else t[i] = sqrt(lx * lx + ly * ly + lz * lz)

            length += t[i]
            t[i] += t[i - 1]
        }
        if (length != 0.0) {
            for (i in 1 until t.size - 1) {
                t[i] = t[i] / length
            }
        }
        t[t.size - 1] = 1.0
        splineX = Spline(t, x)
        splineY = Spline(t, y)
        splineZ = Spline(t, z)
    }

    private fun initRotations(x: DoubleArray, y: DoubleArray, z: DoubleArray) {
        require(!(x.size != y.size || x.size != z.size || y.size != z.size)) { "Arrays must have the same length." }
        require(x.size >= 2) { "Spline edges must have at least two points." }
        t = DoubleArray(x.size)
        t[0] = 0.0

        length = 0.0

        for (i in 1 until t.size) {
            val lx = x[i] - x[i - 1]
            val ly = y[i] - y[i - 1]
            val lz = z[i] - z[i - 1]

            if (0.0 == lx) t[i] = abs(lz)
            else if (0.0 == lz) t[i] = abs(lx)
            else t[i] = sqrt(lx * lx + ly * ly + lz * lz)

            length += t[i]
            t[i] += t[i - 1]
        }
        if (length != 0.0) {
            for (i in 1 until t.size - 1) {
                t[i] = t[i] / length
            }
        }
        t[t.size - 1] = 1.0
        splineXR = Spline(t, x)
        splineYR = Spline(t, y)
        splineZR = Spline(t, z)
    }

    fun getPoint(t: Double): Vector3d {
        return Vector3d(splineX!!.getValue(t), splineY!!.getValue(t), splineZ!!.getValue(t))
    }

    fun getRotation(t: Double): Vector3f {
        return Vector3f(
            splineXR!!.getValue(t).toFloat(),
            splineYR!!.getValue(t).toFloat(),
            splineZR!!.getValue(t).toFloat()
        )
    }

    fun checkValues(): Boolean {
        return splineX!!.checkValues() && splineY!!.checkValues() && splineZ!!.checkValues()
    }

    fun getDx(t: Double): Double {
        return splineX!!.getDx(t)
    }

    fun getDy(t: Double): Double {
        return splineY!!.getDx(t)
    }

    fun getDz(t: Double): Double {
        return splineZ!!.getDx(t)
    }

    fun draw(stack: PoseStack) {
        RenderSystem.setShader { GameRenderer.getPositionTexShader() }
        val tessellator = Tesselator.getInstance()

        //? if >= 1.21 {
        val bufferbuilder = tessellator.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR)
        var last: Vector3d? = null
        for (i in 0..100) {
            val p = i / 100.0

            val pos = getPoint(p)
            if (last == null) last = pos

            OpenGLUtils.drawLine(bufferbuilder, stack.last().pose(), pos, last, 1.0f, 1.0f, 1.0f, 1.0f)

            last = pos
        }
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow())
        //?} else {
        /*val bufferbuilder = tessellator.builder
        bufferbuilder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR)
        var last: Vector3d? = null
        for (i in 0..100) {
            val p = i / 100.0

            val pos = getPoint(p)
            if (last == null) last = pos

            OpenGLUtils.drawLine(bufferbuilder, stack.last().pose(), pos, last, 1.0f, 1.0f, 1.0f, 1.0f)

            last = pos
        }
        tessellator.end()
        *///?}
    }
}