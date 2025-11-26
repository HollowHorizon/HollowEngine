package ru.hollowhorizon.hollowengine.common.compiler

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.scripting.compiling.CompiledScript
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptEvaluationException
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration

data class CompiledScriptImpl(
    override val name: String,
    val script: kotlin.script.experimental.api.CompiledScript,
    val evalConfiguration: ScriptEvaluationConfiguration,
) : CompiledScript {

    @Suppress("UNCHECKED_CAST")
    override fun <T> execute(): Result<T> {
        val evaluator = HollowEngineScriptEvaluator()

        val result = runBlocking {
            evaluator(script, evalConfiguration)
        }

        return if (result is ResultWithDiagnostics.Success) {
            Result.success(result.value.returnValue.scriptInstance as T)
        } else {
            Result.failure(ScriptEvaluationException(name, result.reports.map { it.convert() }))
        }
    }
}
