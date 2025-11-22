package ru.hollowhorizon.hollowengine.common.scripting

import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import java.io.File

interface ScriptingEnvironmentInitializer {
    fun initialize(javaHome: File, classpath: List<File>, scriptTypes: Map<String, String>)
}

interface ScriptingEnvironment {
    val javaHome: File
    val classpath: List<File>

    val analyzer: ScriptingAnalyzer

    fun close()

    companion object {
        lateinit var INSTANCE: ScriptingEnvironment
    }
}