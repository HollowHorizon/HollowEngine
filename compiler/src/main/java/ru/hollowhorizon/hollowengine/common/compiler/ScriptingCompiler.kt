package ru.hollowhorizon.hollowengine.common.compiler

import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.compiler.isolated.ScriptJvmCompilerRemapped
import ru.hollowhorizon.hollowengine.common.scripting.compiling.CompiledScript
import ru.hollowhorizon.hollowengine.common.scripting.compiling.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.ide.*
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

class ScriptingCompilerImpl(val environment: ScriptingEnvironmentImpl) : ScriptingCompiler {
    override fun compile(file: File): Result<CompiledScript.WithFile> {
        val result = compile(file.name, file.readText())

        return result.map { CompiledScript.WithFile(it, file) }
    }

    override fun compile(
        name: String,
        code: String,
    ): Result<CompiledScript> {
        val definition = environment.scriptDefinitions.getDefinitionFor(name)

        val hostConfiguration = definition.hostConfiguration
        val compiler = JvmScriptCompiler(hostConfiguration, ScriptJvmCompilerRemapped(hostConfiguration))
        val result = runScriptingBlocking {
            compiler(StringScriptSource(code), definition.compilationConfiguration)
        }

        return if (result is ResultWithDiagnostics.Success) {
            Result.success(CompiledScriptImpl(name, result.value, definition.evaluationConfiguration!!))
        } else {
            Result.failure(ScriptCompilationException(name, result.reports.map { it.convert() }))
        }
    }
}

private fun List<ScriptDefinition.FromConfigurations>.getDefinitionFor(name: String): ScriptDefinition {
    return sortedWith(
        compareByDescending<ScriptDefinition.FromConfigurations> { it.fileExtension.length }
            .thenByDescending { it.fileExtension }
    ).first { name.endsWith(it.fileExtension) }
}

fun ScriptDiagnostic.convert(): Diagnostic {
    return Diagnostic(
        this.location?.let {
            Range(
                Position(it.start.line, it.start.col),
                Position(it.end?.line ?: it.start.line, it.end?.col ?: it.start.col)
            )
        } ?: Range(Position(-1, -1), Position(-1, -1)),
        when (this.severity) {
            ScriptDiagnostic.Severity.DEBUG -> Severity.DEBUG
            ScriptDiagnostic.Severity.INFO -> Severity.INFO
            ScriptDiagnostic.Severity.WARNING -> Severity.WARNING
            ScriptDiagnostic.Severity.ERROR -> Severity.ERROR
            ScriptDiagnostic.Severity.FATAL -> Severity.FATAL
        },
        this.message
    )
}
