package ru.hollowhorizon.hollowengine.common.utils

import sun.misc.Unsafe
import java.lang.invoke.MethodHandles

object UnsafeTools {
    val unsafe: Unsafe

    init {
        val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
        theUnsafe.isAccessible = true
        unsafe = theUnsafe[null] as Unsafe
    }

    val lookup by lazy {
        val lookupClass = Class.forName("java.lang.invoke.MethodHandles\$Lookup", true, Thread.currentThread().contextClassLoader)
        val field = lookupClass.getDeclaredField("IMPL_LOOKUP")
        val base = unsafe.staticFieldBase(field)
        val offset = unsafe.staticFieldOffset(field)
        unsafe.getObject(base, offset) as MethodHandles.Lookup
    }

    inline fun <reified T> getStaticField(name: String): Any? {
        val field = T::class.java.getDeclaredField(name)
        val base = unsafe.staticFieldBase(field)
        val offset = unsafe.staticFieldOffset(field)
        return unsafe.getObject(base, offset)
    }

    inline fun <reified T> setStaticField(name: String, value: Any?) {
        val field = T::class.java.getDeclaredField(name)
        val base = unsafe.staticFieldBase(field)
        val offset = unsafe.staticFieldOffset(field)
        unsafe.putObject(base, offset, value)
    }

    fun getField(value: Any, name: String): Any? {
        val field = value::class.java.getDeclaredField(name)
        val offset = unsafe.objectFieldOffset(field)
        return unsafe.getObject(value, offset)
    }

    fun setField(value: Any, name: String, x: Any?) {
        val loader = Thread.currentThread().contextClassLoader
        val valueLoader = value::class.java.classLoader
        Thread.currentThread().contextClassLoader = valueLoader
        val field = value::class.java.getDeclaredField(name)
        val offset = unsafe.objectFieldOffset(field)
        unsafe.putObject(value, offset, x)
        Thread.currentThread().contextClassLoader = loader
    }
}