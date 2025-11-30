package ru.hollowhorizon.hollowengine.common.scripting.compiling

import java.io.File
import kotlin.reflect.KClass

interface CompiledScript {
    val name: String
    val type: KClass<*>

    fun <T> execute(vararg constructorArgs: Any?): Result<T>

    class WithFile(val base: CompiledScript, val file: File) : CompiledScript by base
}

fun CompiledScript.start() {
    execute<Any>()
}