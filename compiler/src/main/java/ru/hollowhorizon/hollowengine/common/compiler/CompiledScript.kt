package ru.hollowhorizon.hollowengine.common.compiler

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.scripting.compiling.CompiledScript
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptEvaluationException
import kotlin.reflect.KClass
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.with

data class CompiledScriptImpl(
    override val name: String,
    val script: kotlin.script.experimental.api.CompiledScript,
    val evalConfiguration: ScriptEvaluationConfiguration,
) : CompiledScript {

    override val type: KClass<*>
        get() = runBlocking {
            (script.getClass(evalConfiguration) as ResultWithDiagnostics.Success).value
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T> execute(vararg constructorArgs: Any?): Result<T> {
        val evaluator = HollowEngineScriptEvaluator()

        val result = runBlocking {
            evaluator(script, evalConfiguration.with {
                constructorArgs(*constructorArgs)
            })
        }

        return if (result is ResultWithDiagnostics.Success) {
            Result.success(result.value.returnValue.scriptInstance as T)
        } else {
            Result.failure(ScriptEvaluationException(name, result.reports.map { it.convert() }))
        }
    }
}
