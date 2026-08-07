package ru.hollowhorizon.hollowengine.common.compiler.configuration

import ru.hollowhorizon.hollowengine.common.scripting.compiling.isSharedScript
import kotlin.script.experimental.api.RefineScriptCompilationConfigurationHandler
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.asSuccess

/** Persists the opt-in marker in compiled metadata so cached scripts keep the same evaluation semantics. */
class SharedScriptConfigurator : RefineScriptCompilationConfigurationHandler {
    override fun invoke(
        context: ScriptConfigurationRefinementContext,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> =
        ScriptCompilationConfiguration(context.compilationConfiguration) {
            isSharedScript(true)
        }.asSuccess()
}
