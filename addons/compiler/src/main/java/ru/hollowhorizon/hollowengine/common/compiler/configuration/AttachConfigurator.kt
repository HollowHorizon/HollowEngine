package ru.hollowhorizon.hollowengine.common.compiler.configuration

import kotlin.script.experimental.api.*

/**
 * Handles `@file:Attach("com.example.SomeType")` on scripts: appends `SomeType` as an extra implicit
 * receiver so the script body can call the bound host's members directly (see the runtime `NodeScript` /
 * `@file:Attach` design).
 */
class AttachConfigurator : RefineScriptCompilationConfigurationHandler {
    override operator fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.map { it.annotation }
            ?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        val attachedTypes = annotations.mapNotNull { annotation ->
            runCatching { annotation.javaClass.getDeclaredMethod("value").invoke(annotation) as? String }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }

        if (attachedTypes.isEmpty()) return context.compilationConfiguration.asSuccess()

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            implicitReceivers.append(attachedTypes.map(::KotlinType))
        }.asSuccess()
    }
}
