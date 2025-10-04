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