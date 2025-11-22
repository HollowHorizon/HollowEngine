package ru.hollowhorizon.hollowengine.common

import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import ru.hollowhorizon.hollowengine.common.compiler.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.common.ide.session.AnalysisEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironmentInitializer
import ru.hollowhorizon.hollowengine.logI
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class ScriptingEnvironmentInitializerImpl : ScriptingEnvironmentInitializer {
    override fun initialize(
        javaHome: File,
        classpath: List<File>,
        scriptTypes: Map<String, String>,
    ) {
        val environment = ScriptingEnvironmentImpl(javaHome, classpath, scriptTypes)
        logI("ScriptingEnvironment loaded successfully!")
        ScriptingEnvironment.INSTANCE = environment
    }
}

class ScriptingEnvironmentImpl(
    override val javaHome: File,
    override val classpath: List<File>,
    val scriptTypes: Map<String, String>,
) : ScriptingEnvironment {
    val scriptHostConfig = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
        getScriptingClass(JvmGetScriptingClass())
        configurationDependencies(JvmDependency(classpath))
    }
    private val environment = AnalysisEnvironment(
        classpath.map { it.toPath() },
        scriptTypes.map { (extension, path) ->
            ScriptDefinition.FromConfigurations(
                scriptHostConfig,
                HollowScriptConfiguration(classpath) {
                    baseClass.replaceOnlyDefault(KotlinType(path))
                    fileExtension.replaceOnlyDefault(extension)
                },
                null
            )
        },
        javaHome.toPath()
    )

    override val analyzer = environment.analyzer

    override fun close() {
        environment.dispose()
    }
}