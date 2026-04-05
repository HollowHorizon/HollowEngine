package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hollowengine.common.scripting.compiling.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import java.io.File

interface ScriptingEnvironmentInitializer {
    fun initialize(javaHome: File, classpath: List<File>, scriptTypes: List<ScriptClassProvider>, mappings: Mappings)
}

interface ScriptingEnvironment {
    val javaHome: File
    val classpath: List<File>
    val mappings: Mappings

    val analyzer: ScriptingAnalyzer
    val compiler: ScriptingCompiler

    fun close()

    companion object {
        lateinit var INSTANCE: ScriptingEnvironment
    }
}