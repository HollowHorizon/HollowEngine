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

package ru.hollowhorizon.hc.client.utils

import com.mojang.blaze3d.Blaze3D
import ru.hollowhorizon.hc.client.handlers.TickHandler
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

open class GuiAnimator protected constructor(
    val begin: Int,
    val end: Int,
    val maxTime: Int,
    protected val interpolation: (Float) -> Float,
) : ReadOnlyProperty<Any?, Int> {
    var value: Float = begin.toFloat()
    private var startTime = Blaze3D.getTime()

    open fun update() {
        if (isFinished()) return

        val currentTime = (Blaze3D.getTime() - startTime) * 20
        value = begin + (end - begin) * interpolation(currentTime.toFloat() / maxTime)
    }

    fun isFinished(): Boolean {
        return TickHandler.currentTicks - startTime > maxTime
    }

    fun reset() {
        startTime = Blaze3D.getTime()
        value = begin.toFloat()
    }

    class Reversed(begin: Int, end: Int, time: Int, interpolation: (Float) -> Float) :
        GuiAnimator(begin, end, time, interpolation) {
        private var switch = false

        override fun update() {
            super.update()
            if (switch) value = end - value

            if (isFinished()) {
                switch = !switch
                reset()
            }
        }
    }

    class Looped(begin: Int, end: Int, time: Int, interpolation: (Float) -> Float) :
        GuiAnimator(begin, end, time, interpolation) {
        override fun update() {
            super.update()
            if (isFinished()) reset()
        }
    }

    class Single(begin: Int, end: Int, time: Int, interpolation: (Float) -> Float) :
        GuiAnimator(begin, end, time, interpolation)

    override fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        update()
        return value.toInt()
    }
}