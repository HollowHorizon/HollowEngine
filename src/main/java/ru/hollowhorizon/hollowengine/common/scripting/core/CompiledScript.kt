package ru.hollowhorizon.hollowengine.common.scripting.core

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.isProduction
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler.saveScriptToJar
import ru.hollowhorizon.hollowengine.common.scripting.core.remapper.Remapper
import java.io.File
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

data class CompiledScript(
    val scriptName: String,
    val hash: String,
    val script: kotlin.script.experimental.api.CompiledScript?,
    val scriptFile: File?,
) {
    var errors: List<ScriptError>? = null

    fun save(file: File) {
        if (script == null) return
        runBlocking {
            (script as? KJvmCompiledScript)?.saveScriptToJar(file, hash)
        }
    }

    suspend fun execute(body: ScriptEvaluationConfiguration.Builder.() -> Unit = {}): ResultWithDiagnostics<EvaluationResult> {
        if (script == null) {
            return ResultWithDiagnostics.Failure(
                arrayListOf(
                    ScriptDiagnostic(-1, "Script not compiled!", ScriptDiagnostic.Severity.FATAL)
                )
            )
        }

        val evalConfig = ScriptEvaluationConfiguration { body() }
        val evaluator = BasicJvmScriptEvaluator()
        val result = evaluator(script, evalConfig)


        if (result is ResultWithDiagnostics.Success) ScriptStartedEvent(scriptFile)
        else if (result is ResultWithDiagnostics.Failure) {
            val errors = result.reports.map {
                ScriptError(
                    ScriptError.Severity.entries[it.severity.ordinal],
                    it.message,
                    it.sourcePath ?: "",
                    it.location?.start?.line ?: 0,
                    it.location?.start?.col ?: 0,
                    it.exception
                ).apply {
                    HollowCore.LOGGER.warn(this)
                }
            }

            ScriptErrorEvent(scriptFile, ErrorType.RUNTIME_ERROR, errors).post()
        }

        return result
    }
}

suspend fun KJvmCompiledScript.obfuscate(name: String, hash: String): kotlin.script.experimental.api.CompiledScript {
    if (!isProduction) return this

    val source = File("hollowcore/$name.jar")
    saveScriptToJar(source, hash)

    Remapper.remap(
        Remapper.OBFUSCATE_REMAPPER,
        arrayOf(source),
        File("hollowcore/.classpath/").toPath(),
        *deobfClasspath.map { it.toPath() }.toTypedArray()
    )

    source.delete()
    val script = File("hollowcore/.classpath/$name.jar")

    val obf = script.loadScriptFromJar()
    obf.getClass(null) // инициализируем скрипт
    script.delete()
    return obf
}