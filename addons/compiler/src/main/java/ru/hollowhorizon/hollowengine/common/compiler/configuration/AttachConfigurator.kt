package ru.hollowhorizon.hollowengine.common.compiler.configuration

import kotlin.reflect.KClass
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.RefineScriptCompilationConfigurationHandler
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptConfigurationRefinementContext
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.implicitReceivers

private const val ATTACH_ANNOTATION = "ru.hollowhorizon.hollowengine.common.scripting.annotations.Attach"

/**
 * Handles `@file:Attach(SomeType::class)` on scripts: appends `SomeType` as an extra implicit
 * receiver so the script body can call the bound host's members directly (see the runtime `NodeScript` /
 * `@file:Attach` design).
 */
class AttachConfigurator : RefineScriptCompilationConfigurationHandler {
    override operator fun invoke(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.map { it.annotation }
            ?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        val attachedTypes = annotations.asSequence()
            .filter { annotation -> annotation.annotationClass.qualifiedName == ATTACH_ANNOTATION }
            .mapNotNull { annotation ->
            runCatching { annotation.javaClass.getMethod("value").invoke(annotation) }
                .getOrNull()
                .let { value ->
                    when (value) {
                        is Class<*> -> value.kotlin
                        is KClass<*> -> value
                        else -> null
                    }
                }
            }
            .distinct()
            .toList()

        if (attachedTypes.isEmpty()) return context.compilationConfiguration.asSuccess()

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            implicitReceivers.append(attachedTypes.map(::KotlinType))
        }.asSuccess()
    }
}
