package ru.hollowhorizon.hollowengine.common.scripting.compiling

import java.io.File

interface ScriptingCompiler {
    fun compile(name: String, code: String): Result<CompiledScript>
    fun compile(file: File): Result<CompiledScript.WithFile>
}