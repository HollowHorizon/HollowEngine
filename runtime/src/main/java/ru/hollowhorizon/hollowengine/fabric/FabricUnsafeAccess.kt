package ru.hollowhorizon.hollowengine.fabric

import sun.misc.Unsafe

private val unsafe by lazy {
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    theUnsafe[null] as Unsafe
}

@Suppress("UNCHECKED_CAST")
fun <T> findField(lookup: Any, name: String): T {
    val lookupClass = lookup::class.java
    val field = lookupClass.getDeclaredField(name)
    val offset = unsafe.objectFieldOffset(field)
    return unsafe.getObject(lookup, offset) as T
}