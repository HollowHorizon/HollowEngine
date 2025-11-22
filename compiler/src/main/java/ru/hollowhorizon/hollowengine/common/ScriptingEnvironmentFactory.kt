package ru.hollowhorizon.hollowengine.common

import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import ru.hollowhorizon.hollowengine.common.ide.session.AnalysisEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironmentInitializer
import ru.hollowhorizon.hollowengine.logI
import java.io.File
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class ScriptingEnvironmentInitializerImpl : ScriptingEnvironmentInitializer {
    override fun initialize(
        javaHome: File,
        classpath: List<File>,
    ) {
        val environment = ScriptingEnvironmentImpl(javaHome, classpath)
        logI("ScriptingEnvironment loaded successfully!")
        ScriptingEnvironment.INSTANCE = environment
    }
}

class ScriptingEnvironmentImpl(
    override val javaHome: File,
    override val classpath: List<File>,
) : ScriptingEnvironment {
    private val environment = AnalysisEnvironment(
        classpath.map { it.toPath() },
        listOf(ScriptDefinition.getDefault(defaultJvmScriptingHostConfiguration)),
        javaHome.toPath()
    )

    override val analyzer = environment.analyzer

    override fun close() {
        environment.dispose()
    }
}