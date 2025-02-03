package ru.hollowhorizon.hollowengine

import sun.misc.Unsafe
import java.lang.reflect.Field

object ChecksReset {
    @JvmStatic
    fun reset() {
        val theUnsafe: Field = Unsafe::class.java.getDeclaredField("theUnsafe")
        theUnsafe.isAccessible = true
        val unsafe: Unsafe = theUnsafe[null] as Unsafe

        val defined = Class.forName("jdk.internal.module.Checks").getDeclaredField("RESERVED")

        // Если Kotlin можно называть свои пакеты `native`, то почему мне нельзя?
        unsafe.putObject(unsafe.staticFieldBase(defined), unsafe.staticFieldOffset(defined), setOf<String>())
    }
}