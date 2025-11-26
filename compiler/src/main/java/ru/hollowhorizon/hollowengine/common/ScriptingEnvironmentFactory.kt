package ru.hollowhorizon.hollowengine.common

import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import ru.hollowhorizon.hollowengine.common.compiler.ScriptingCompilerImpl
import ru.hollowhorizon.hollowengine.common.compiler.configuration.HollowScriptConfiguration
import ru.hollowhorizon.hollowengine.common.ide.session.AnalysisEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironmentInitializer
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.logI
import java.io.File
import kotlin.script.experimental.api.*
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
        scriptTypes: List<ScriptClassProvider>,
        mappings: Mappings
    ) {
        System.setProperty("kotlin.java.stdlib.jar", classpath.first { it.name.startsWith("kotlin-stdlib-jdk8") }.absolutePath)
        val environment = ScriptingEnvironmentImpl(javaHome, classpath, scriptTypes, mappings)
        logI("ScriptingEnvironment loaded successfully!")
        ScriptingEnvironment.INSTANCE = environment
    }
}

class ScriptingEnvironmentImpl(
    override val javaHome: File,
    override val classpath: List<File>,
    scriptTypes: List<ScriptClassProvider>,
    override val mappings: Mappings
) : ScriptingEnvironment {
    val scriptHostConfig = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
        getScriptingClass(JvmGetScriptingClass())
        configurationDependencies(JvmDependency(classpath))
    }
    val scriptDefinitions = scriptTypes.map { (extension, path, imports) ->
        ScriptDefinition.FromConfigurations(
            scriptHostConfig,
            HollowScriptConfiguration(classpath) {
                baseClass.replaceOnlyDefault(KotlinType(path))
                fileExtension.replaceOnlyDefault(extension)
                defaultImports(imports)
            },
            ScriptEvaluationConfiguration()
        )
    }
    private val environment = AnalysisEnvironment(
        classpath.map { it.toPath() },
        scriptDefinitions,
        javaHome.toPath()
    )

    override val analyzer = environment.analyzer
    override val compiler = ScriptingCompilerImpl(this)

    override fun close() {
        analyzer.fileCache.forEach { file ->
            analyzer.cleanupFile(file.value.file)
        }
        analyzer.fileCache.clear()
        analyzer.libraries.forEach {
            it
        }
        environment.dispose()
    }
}