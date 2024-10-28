package ru.hollowhorizon.hollowengine.common.scripting.core

import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.LOGGER
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.common.scripting.core.host.HollowEngineScriptingHost
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvmhost.BasicJvmScriptJarGenerator
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.loadScriptFromJar

fun <R> ResultWithDiagnostics<R>.orException(): R = valueOr {
    throw IllegalStateException(
        it.errors().joinToString("\n"),
        it.reports.find { it.exception != null }?.exception
    )
}

fun ResultWithDiagnostics.Failure.errors(): List<String> = reports.map { diagnostic ->
    buildString {
        if (diagnostic.severity >= ScriptDiagnostic.Severity.WARNING) {
            append(diagnostic.message)

            if (diagnostic.sourcePath != null || diagnostic.location != null) {
                append(" at [")
                diagnostic.sourcePath?.let { append(it.substringAfterLast(File.separatorChar)) }
                diagnostic.location?.let { path ->
                    append(':')
                    append(path.start.line)
                    append(':')
                    append(path.start.col)
                }
                append("]")
            }
            if (diagnostic.exception != null) {
                append(": ")
                append(diagnostic.exception)
                ByteArrayOutputStream().use { os ->
                    val ps = PrintStream(os)
                    diagnostic.exception?.printStackTrace(ps)
                    ps.flush()
                    append("\n")
                    append(os.toString())
                }
            }
        }
    }
}.filter { it.isNotEmpty() }

object ScriptingCompiler {

    suspend inline fun <reified T : Any> compileText(text: String): CompiledScript {
        val hostConfiguration = HollowEngineScriptingHost()
        val compilationConfiguration = createCompilationConfiguration<T>(hostConfiguration)

        val compiler = JvmScriptCompiler(hostConfiguration)
        val result = compiler(StringScriptSource(text), compilationConfiguration)

        logErrors(result)

        return processResult(result, "script.kts")
    }

    suspend inline fun <reified T : Any> compileFile(script: File): CompiledScript {
        val hostConfiguration = HollowEngineScriptingHost()
        val compilationConfiguration = createCompilationConfiguration<T>(hostConfiguration)

        val compiledJar = script.resolveCompiledJar()
        val hashcode = script.readText().hashCode().toString()

        if (compiledJar.exists()) return loadCompiledScript(script, compiledJar, hashcode)

        val compiler = JvmScriptCompiler(hostConfiguration)
        val result = compiler(FileScriptSource(script), compilationConfiguration)

        logErrors(result)

        return processResult(result, script.name, script, compiledJar)
    }

    suspend fun processResult(
        result: ResultWithDiagnostics<*>,
        scriptName: String,
        scriptFile: File? = null,
        compiledJar: File? = null,
    ): CompiledScript {
        val compiledScript = result.valueOrNull() as KJvmCompiledScript
        return CompiledScript(
            scriptName,
            scriptFile?.readText()?.hashCode()?.toString() ?: "",
            compiledScript.obfuscate(scriptName),
            scriptFile ?: File(scriptName)
        ).apply {
            if (result.isError()) {
                handleErrors(result, scriptFile)
            } else {
                compiledJar?.let {
                    compiledScript.saveScriptToJar(it)
                    ScriptCompiledEvent(scriptFile ?: error("Script file not found")).post()
                }
            }
        }
    }

    inline fun <reified T : Any> createCompilationConfiguration(hostConfiguration: HollowEngineScriptingHost) =
        createCompilationConfigurationFromTemplate(
            KotlinType(T::class),
            hostConfiguration,
            HollowCore::class
        ) {}

    private fun CompiledScript.handleErrors(result: ResultWithDiagnostics<*>, scriptFile: File?) {
        val errors = result.errors()
        val event = ScriptErrorEvent(scriptFile, ErrorType.COMPILATION_ERROR, errors)
        event.post()
        if (!event.isCanceled) {
            this.errors = errors.map(ScriptError::toString)
        }
    }

    fun logErrors(result: ResultWithDiagnostics<*>) {
        result.errors().forEach(LOGGER::warn)
        result.errors().mapNotNull { it.exception }.distinct().forEach {
            LOGGER.error(it.stackTraceToString())
        }
    }

    private fun ResultWithDiagnostics<*>.errors() = reports.map {
        ScriptError(
            ScriptError.Severity.entries[it.severity.ordinal],
            it.message,
            it.sourcePath ?: "",
            it.location?.start?.line ?: 0,
            it.location?.start?.col ?: 0,
            it.exception
        )
    }

    fun File.resolveCompiledJar() = absoluteFile.parentFile.resolve("$name.jar")
    fun loadCompiledScript(script: File, jar: File, hashcode: String) =
        CompiledScript(script.name, hashcode, jar.loadScriptFromJar(), script)

    suspend fun KJvmCompiledScript.saveScriptToJar(outputJar: File) {
        BasicJvmScriptJarGenerator(outputJar)(this, ScriptEvaluationConfiguration())
    }
}