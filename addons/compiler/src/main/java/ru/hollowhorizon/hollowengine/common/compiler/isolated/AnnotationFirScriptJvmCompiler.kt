/*
 * Based on ScriptJvmK2CompilerImpl from the Kotlin project.
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package ru.hollowhorizon.hollowengine.common.compiler.isolated

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.LegacyK2CliPipeline
import org.jetbrains.kotlin.cli.common.checkKotlinPackageUsageForLightTree
import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.cli.common.fir.reportToMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.ModuleCompilerEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.convertAnalyzedFirToIr
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.generateCodeFromIr
import org.jetbrains.kotlin.config.jvmTarget
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirScript
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.resolveAndCheckFir
import org.jetbrains.kotlin.fir.pipeline.runPlatformCheckers
import org.jetbrains.kotlin.fir.resolve.transformers.FirGlobalResolveProcessor
import org.jetbrains.kotlin.fir.resolve.transformers.FirTransformerBasedResolveProcessor
import org.jetbrains.kotlin.fir.resolve.transformers.createAllCompilerResolveProcessors
import org.jetbrains.kotlin.fir.session.FirJvmSessionFactory.createSourceSession
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.fir.withFileAnalysisExceptionWrapping
import org.jetbrains.kotlin.modules.TargetId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.NameUtils
import org.jetbrains.kotlin.scripting.compiler.plugin.configureFirSession
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.getOrStoreRefinedCompilationConfiguration
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.getRefinedOrBaseCompilationConfiguration
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.scriptRefinedCompilationConfigurationsCache
import org.jetbrains.kotlin.scripting.compiler.plugin.dependencies.collectScriptsCompilationDependenciesRecursively
import org.jetbrains.kotlin.scripting.compiler.plugin.fir.FirScriptCompilationComponent
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ScriptingCompilerEnvironment
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.K2ScriptingCompilerEnvironmentInternal
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.configureLibrarySessionIfNeeded
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.extractResultFields
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.makeCompiledScript
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.refineAllForK2
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.selectJvmTarget
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.updateWithCompilerOptions
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.compilerOptions
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.api.valueOr
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.impl._languageVersion
import kotlin.script.experimental.jvm.util.toClassPathOrEmpty

/**
 * Kotlin's standalone K2 script compiler keeps the annotation-resolution source module as a dependency of the real
 * compilation module, but leaves its declarations as raw FIR. Imported declarations are therefore visible twice, and
 * call resolution can select the raw symbol instead of the resolved declaration from the compilation source session.
 *
 * Keep this adapter aligned with [org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmK2CompilerImpl]. Its only
 * intentional pipeline difference is [prepareAnnotationFir], which resolves that dependency before it becomes visible.
 */
internal class AnnotationFirScriptJvmCompiler(
    state: K2ScriptingCompilerEnvironment,
    private val convertToFir: SourceCode.(FirSession, BaseDiagnosticsCollector) -> FirFile,
) {
    private val state = state as? K2ScriptingCompilerEnvironmentInternal
        ?: error("Expected the internal K2 scripting compiler environment, got ${state::class}")
    private val annotationFirFiles = linkedSetOf<FirFile>()

    fun compile(script: SourceCode): ResultWithDiagnostics<CompiledScript> =
        compile(script, state.baseScriptCompilationConfiguration)

    fun compile(
        script: SourceCode,
        scriptCompilationConfiguration: ScriptCompilationConfiguration,
    ): ResultWithDiagnostics<CompiledScript> {
        val reporting = ErrorReportingContext(
            state.messageCollector,
            DiagnosticsCollectorImpl(),
            state.compilerContext.environment.configuration.getBoolean(
                CLIConfigurationKeys.RENDER_DIAGNOSTIC_INTERNAL_NAME
            ),
        )
        if (state.compilerContext.environment.configuration.languageVersionSettings.languageVersion.major < 2) {
            return makeFailureResult("This script compiler implementation is not compatible with Kotlin 1.9 and earlier")
        }
        return scriptCompilationConfiguration.refineAll(script).onSuccess {
            compileRefined(script, it, reporting)
        }
    }

    @OptIn(SessionConfiguration::class)
    private fun ScriptCompilationConfiguration.refineAll(
        script: SourceCode,
    ): ResultWithDiagnostics<ScriptCompilationConfiguration> =
        refineAllForK2(script, state.hostConfiguration) { source, configuration ->
            collectAndResolveScriptAnnotationsWithClassLiterals(
                source,
                configuration,
                state.hostConfiguration,
                { _, refinedConfiguration -> annotationResolutionSession(refinedConfiguration) },
                { session, diagnosticsReporter ->
                    convertToFir(session, diagnosticsReporter).also(annotationFirFiles::add)
                },
            )
        }.onSuccess {
            it.with {
                _languageVersion(
                    state.compilerContext.environment.configuration.languageVersionSettings.languageVersion.versionString
                )
            }.asSuccess()
        }

    private fun failure(
        reporting: ErrorReportingContext,
        diagnosticsCollector: BaseDiagnosticsCollector,
        vararg diagnostics: ScriptDiagnostic,
    ): ResultWithDiagnostics.Failure {
        diagnosticsCollector.reportToMessageCollector(reporting.messageCollector, reporting.renderDiagnosticName)
        return ResultWithDiagnostics.Failure(
            *reporting.messageCollector.diagnostics.toTypedArray(),
            *diagnostics,
        )
    }

    @OptIn(LegacyK2CliPipeline::class, DirectDeclarationsAccess::class, SessionConfiguration::class)
    private fun compileRefined(
        script: SourceCode,
        scriptRefinedCompilationConfiguration: ScriptCompilationConfiguration,
        reporting: ErrorReportingContext,
    ): ResultWithDiagnostics<CompiledScript> {
        val compilerConfiguration = state.compilerContext.environment.configuration.copy().apply {
            jvmTarget = selectJvmTarget(scriptRefinedCompilationConfiguration, reporting.messageCollector)
            messageCollector = reporting.messageCollector
            diagnosticsCollector = reporting.diagnosticsCollector
        }

        state.hostConfiguration[ScriptingHostConfiguration.scriptRefinedCompilationConfigurationsCache]
            ?.storeRefinedCompilationConfiguration(script, scriptRefinedCompilationConfiguration.asSuccess())

        val allSourceFiles = mutableListOf(script)
        val (classpath, importedSources, sourceDependencies) =
            collectScriptsCompilationDependenciesRecursively(allSourceFiles) { importedScript ->
                state.hostConfiguration.getOrStoreRefinedCompilationConfiguration(importedScript) { source, baseConfig ->
                    baseConfig.refineAll(source)
                }
            }.valueOr { return it }
        allSourceFiles.addAll(importedSources)
        prepareAnnotationFir()

        val ignoredOptionsReportingState = state.compilerContext.ignoredOptionsReportingState
        val updatedCompilerOptions = allSourceFiles.flatMapTo(mutableListOf()) {
            getRefinedConfiguration(it)[ScriptCompilationConfiguration.compilerOptions] ?: emptyList()
        }
        if (
            updatedCompilerOptions.isNotEmpty() &&
            updatedCompilerOptions != state.baseScriptCompilationConfiguration[ScriptCompilationConfiguration.compilerOptions]
        ) {
            compilerConfiguration.updateWithCompilerOptions(
                updatedCompilerOptions,
                reporting.messageCollector,
                ignoredOptionsReportingState,
                true,
            )
        }

        if (reporting.messageCollector.hasErrors()) return failure(reporting, reporting.diagnosticsCollector)

        configureLibrarySessionIfNeeded(state, compilerConfiguration, classpath)

        val compilerEnvironment = ModuleCompilerEnvironment(state.projectEnvironment, reporting.diagnosticsCollector)
        val renderDiagnosticName = compilerConfiguration.getBoolean(CLIConfigurationKeys.RENDER_DIAGNOSTIC_INTERNAL_NAME)
        val targetId = TargetId(script.name ?: "main", "java-production")
        val moduleData = state.moduleDataProvider.addNewScriptModuleData(Name.special("<script-${script.name ?: "main"}>"))
        val session = createSourceSession(
            moduleData,
            AbstractProjectFileSearchScope.EMPTY,
            createIncrementalCompilationSymbolProviders = { null },
            state.extensionRegistrars,
            compilerConfiguration,
            context = state.sessionFactoryContext,
            needRegisterJavaElementFinder = true,
            isForLeafHmppModule = false,
            init = {},
        )

        session.register(
            FirScriptCompilationComponent::class,
            FirScriptCompilationComponent(
                state.hostConfiguration,
                getSessionForAnnotationResolution = { _, refinedConfiguration ->
                    annotationResolutionSession(refinedConfiguration)
                },
            ),
        )
        state.hostConfiguration[ScriptingHostConfiguration.configureFirSession]?.invoke(session)

        val sourcesToFir = allSourceFiles.associateWith {
            it.convertToFir(session, reporting.diagnosticsCollector)
        }
        if (reporting.diagnosticsCollector.hasErrors) return failure(reporting, reporting.diagnosticsCollector)

        checkKotlinPackageUsageForLightTree(compilerConfiguration, sourcesToFir.values)
        if (reporting.messageCollector.hasErrors()) return failure(reporting, reporting.diagnosticsCollector)

        val outputs = listOf(
            resolveAndCheckFir(session, sourcesToFir.values.toList(), reporting.diagnosticsCollector)
        ).also {
            it.runPlatformCheckers(reporting.diagnosticsCollector)
        }
        val frontendOutput = AllModulesFrontendOutput(outputs)
        if (reporting.diagnosticsCollector.hasErrors) return failure(reporting, reporting.diagnosticsCollector)

        val irInput = convertAnalyzedFirToIr(
            compilerConfiguration,
            targetId,
            frontendOutput,
            compilerEnvironment,
        )
        if (reporting.diagnosticsCollector.hasErrors) return failure(reporting, reporting.diagnosticsCollector)

        val generationState = generateCodeFromIr(irInput, compilerEnvironment)
        reporting.diagnosticsCollector.reportToMessageCollector(reporting.messageCollector, renderDiagnosticName)
        if (reporting.diagnosticsCollector.hasErrors) return failure(reporting, reporting.diagnosticsCollector)

        return makeCompiledScript(
            generationState,
            script,
            { source ->
                sourcesToFir[source]?.declarations?.firstIsInstanceOrNull<FirScript>()
                    ?.let { it.symbol.packageFqName().child(NameUtils.getScriptTargetClassName(it.name)) }
            },
            sourceDependencies,
            ::getRefinedConfiguration,
            extractResultFields(irInput.irModuleFragment),
        ).onSuccess { compiledScript ->
            ResultWithDiagnostics.Success(compiledScript, reporting.messageCollector.diagnostics)
        }
    }

    @OptIn(SessionConfiguration::class)
    private fun annotationResolutionSession(
        scriptCompilationConfiguration: ScriptCompilationConfiguration,
    ): FirSession {
        val dependencies = scriptCompilationConfiguration[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty()
        if (dependencies.isNotEmpty()) {
            configureLibrarySessionIfNeeded(
                state,
                state.compilerContext.environment.configuration,
                dependencies,
            )
        }
        return state.dummySessionForAnnotationResolution ?: createSourceSession(
            state.moduleDataProvider.addNewScriptModuleData(Name.special("<raw-script>")),
            AbstractProjectFileSearchScope.EMPTY,
            createIncrementalCompilationSymbolProviders = { null },
            state.extensionRegistrars,
            state.compilerContext.environment.configuration,
            context = state.sessionFactoryContext,
            needRegisterJavaElementFinder = true,
            isForLeafHmppModule = false,
            init = {},
        ).apply {
            register(
                FirScriptCompilationComponent::class,
                FirScriptCompilationComponent(
                    state.hostConfiguration,
                    getSessionForAnnotationResolution = { _, _ -> this },
                ),
            )
            state.dummySessionForAnnotationResolution = this
        }
    }

    private fun getRefinedConfiguration(script: SourceCode): ScriptCompilationConfiguration =
        state.hostConfiguration.getRefinedOrBaseCompilationConfiguration(script).valueOrThrow()

    private fun prepareAnnotationFir() {
        val session = state.dummySessionForAnnotationResolution ?: return
        if (annotationFirFiles.isEmpty()) return

        for (processor in createAllCompilerResolveProcessors(session)) {
            val phase = processor.phase ?: continue
            if (phase > FirResolvePhase.ANNOTATION_ARGUMENTS) break

            processor.beforePhase()
            try {
                when (processor) {
                    is FirTransformerBasedResolveProcessor -> {
                        for (file in annotationFirFiles) {
                            withFileAnalysisExceptionWrapping(file) {
                                processor.processFile(file)
                            }
                        }
                    }

                    is FirGlobalResolveProcessor -> processor.process(annotationFirFiles)
                }
            } finally {
                processor.afterPhase()
            }
        }
    }

    private class ErrorReportingContext(
        val messageCollector: ScriptDiagnosticsMessageCollector,
        val diagnosticsCollector: BaseDiagnosticsCollector,
        val renderDiagnosticName: Boolean,
    )
}
