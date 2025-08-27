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

package ru.hollowhorizon.hollowengine.common.utils

import sun.misc.Unsafe

object UnsafeTools {
    val UNSAFE: Unsafe

    init {
        val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
        theUnsafe.isAccessible = true
        UNSAFE = theUnsafe[null] as Unsafe
    }

    inline fun <reified T> getStaticField(name: String): Any? {
        val field = T::class.java.getDeclaredField(name)
        val base = UNSAFE.staticFieldBase(field)
        val offset = UNSAFE.staticFieldOffset(field)
        return UNSAFE.getObject(base, offset)
    }

    inline fun <reified T> setStaticField(name: String, value: Any?) {
        val field = T::class.java.getDeclaredField(name)
        val base = UNSAFE.staticFieldBase(field)
        val offset = UNSAFE.staticFieldOffset(field)
        UNSAFE.putObject(base, offset, value)
    }

    fun getField(value: Any, name: String): Any? {
        val field = value::class.java.getDeclaredField(name)
        val offset = UNSAFE.objectFieldOffset(field)
        return UNSAFE.getObject(value, offset)
    }

    fun setField(value: Any, name: String, x: Any?) {
        val loader = Thread.currentThread().contextClassLoader
        val valueLoader = value::class.java.classLoader
        Thread.currentThread().contextClassLoader = valueLoader
        val field = value::class.java.getDeclaredField(name)
        val offset = UNSAFE.objectFieldOffset(field)
        UNSAFE.putObject(value, offset, x)
        Thread.currentThread().contextClassLoader = loader
    }
}