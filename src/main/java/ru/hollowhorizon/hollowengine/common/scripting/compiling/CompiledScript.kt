package ru.hollowhorizon.hollowengine.common.scripting.compiling

import java.io.File

interface CompiledScript {
    val name: String

    fun <T> execute(): Result<T>

    class WithFile(val base: CompiledScript, val file: File) : CompiledScript by base
}

fun CompiledScript.start() {
    execute<Any>()
}