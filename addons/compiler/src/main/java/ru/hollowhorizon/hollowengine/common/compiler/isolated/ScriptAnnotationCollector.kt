/*
 * Based on k2ScriptAnnotationResolution.kt from the Kotlin project.
 * Copyright 2010-2025 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package ru.hollowhorizon.hollowengine.common.compiler.isolated

import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.cli.common.fir.reportToMessageCollector
import org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.withFileAnalysisExceptionWrapping
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirScript
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotationCall
import org.jetbrains.kotlin.fir.expressions.FirCollectionLiteral
import org.jetbrains.kotlin.fir.expressions.FirErrorExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.UnresolvedExpressionTypeAccess
import org.jetbrains.kotlin.fir.resolve.ResolutionMode
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.transformers.FirImportResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirAbstractBodyResolveTransformerDispatcher
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirDeclarationsResolveTransformer
import org.jetbrains.kotlin.fir.resolve.transformers.body.resolve.FirExpressionsResolveTransformer
import org.jetbrains.kotlin.fir.scopes.createImportingScopes
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirErrorTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.scriptRefinedCompilationConfigurationsCache
import org.jetbrains.kotlin.scripting.compiler.plugin.fir.FirScriptCompilationComponent
import org.jetbrains.kotlin.scripting.compiler.plugin.fir.scriptCompilationConfiguration
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.failure
import org.jetbrains.kotlin.utils.tryCreateCallableMappingFromNamedArgs
import java.io.File
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptSourceAnnotation
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asDiagnostics
import kotlin.script.experimental.api.asErrorDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.collectedAnnotations
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.makeFailureResult
import kotlin.script.experimental.api.mapNotNullSuccess
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.api.refineConfigurationOnAnnotations
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.host.with
import kotlin.script.experimental.host.withDefaultsFrom
import kotlin.script.experimental.jvm.GetScriptingClassByClassLoader
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.toClassPathOrEmpty
import kotlin.script.experimental.jvm.util.toSourceCodePosition

/**
 * Kotlin 2.4's K2 script annotation collector does not yet support class literals (KT-83500).
 * This keeps the upstream collection flow intact and adds the missing [FirGetClassCall] conversion.
 */
@OptIn(SessionConfiguration::class, DirectDeclarationsAccess::class)
internal fun collectAndResolveScriptAnnotationsWithClassLiterals(
    script: SourceCode,
    compilationConfiguration: ScriptCompilationConfiguration,
    baseHostConfiguration: ScriptingHostConfiguration,
    getSessionForAnnotationResolution: (SourceCode, ScriptCompilationConfiguration) -> FirSession,
    convertToFir: SourceCode.(FirSession, BaseDiagnosticsCollector) -> FirFile,
): ResultWithDiagnostics<ScriptCollectedData> {
    val hostConfiguration =
        compilationConfiguration[ScriptCompilationConfiguration.hostConfiguration].withDefaultsFrom(baseHostConfiguration)
    val contextClassLoader = hostConfiguration[ScriptingHostConfiguration.jvm.baseClassLoader]
    val compilationClasspath = compilationConfiguration[ScriptCompilationConfiguration.dependencies].toClassPathOrEmpty()
    val getScriptingClass = hostConfiguration[ScriptingHostConfiguration.getScriptingClass]
    val jvmGetScriptingClass = getScriptingClass as? GetScriptingClassByClassLoader
        ?: error(
            "Expected a GetScriptingClassByClassLoader in the scripting host configuration, " +
                    "got $getScriptingClass",
        )
    val messageCollector = ScriptDiagnosticsMessageCollector(null)
    val acceptedAnnotations = compilationConfiguration[
        ScriptCompilationConfiguration.refineConfigurationOnAnnotations
    ]?.flatMapTo(LinkedHashSet()) { handler ->
        handler.annotations.mapNotNull { annotationType ->
            try {
                @Suppress("UNCHECKED_CAST")
                jvmGetScriptingClass(annotationType, contextClassLoader, hostConfiguration) as? KClass<Annotation>
            } catch (exception: Throwable) {
                messageCollector.report(
                    exception.asDiagnostics(customMessage = "Failed to load annotation class ${annotationType.typeName}")
                )
                null
            }
        }
    }?.takeIf { it.isNotEmpty() } ?: return ScriptCollectedData(emptyMap()).asSuccess()

    if (messageCollector.hasErrors()) return failure(messageCollector)

    val session = getSessionForAnnotationResolution(script, compilationConfiguration)
    session.register(
        FirScriptCompilationComponent::class,
        FirScriptCompilationComponent(
            hostConfiguration.with { reset(scriptRefinedCompilationConfigurationsCache) },
            getSessionForAnnotationResolution = { _, _ -> error("Recursive refinement attempted") },
        ),
    )

    val diagnosticsCollector = DiagnosticsCollectorImpl()
    val firFile = script.convertToFir(session, diagnosticsCollector)
    // Script annotation refinement precedes the regular IMPORTS phase, but class literals need those scopes resolved.
    FirImportResolveTransformer(session).transformFile(firFile, null)
    firFile.declarations.forEach { declaration ->
        if (declaration is FirScript) declaration.scriptCompilationConfiguration = compilationConfiguration
    }
    if (diagnosticsCollector.hasErrors) {
        diagnosticsCollector.reportToMessageCollector(messageCollector, renderDiagnosticName = false)
        return failure(messageCollector)
    }

    fun loadAnnotation(annotation: FirAnnotation): ResultWithDiagnostics<ScriptSourceAnnotation<Annotation>?> =
        (annotation as? FirAnnotationCall)
            ?.toAnnotationObjectIfMatches(
                acceptedAnnotations.toList(),
                session,
                firFile,
                loadClass = { classId -> loadClassLiteral(classId, compilationClasspath, contextClassLoader) },
            )
            ?.onSuccess { resolved ->
                val location = script.locationId
                val startPosition = annotation.source?.startOffset?.toSourceCodePosition(script)
                val endPosition = annotation.source?.endOffset?.toSourceCodePosition(script)
                ScriptSourceAnnotation(
                    resolved,
                    if (location != null && startPosition != null) {
                        SourceCode.LocationWithId(location, SourceCode.Location(startPosition, endPosition))
                    } else {
                        null
                    },
                ).asSuccess()
            } ?: ResultWithDiagnostics.Success(null)

    return firFile.annotations.mapNotNullSuccess(::loadAnnotation).onSuccess { annotations ->
        ScriptCollectedData(mapOf(ScriptCollectedData.collectedAnnotations to annotations)).asSuccess()
    }
}

private fun FirAnnotationCall.toAnnotationObjectIfMatches(
    expectedAnnotationClasses: List<KClass<out Annotation>>,
    session: FirSession,
    firFile: FirFile,
    loadClass: (ClassId) -> KClass<*>,
): ResultWithDiagnostics<Annotation>? {
    val shortName = when (val typeRef = annotationTypeRef) {
        is FirResolvedTypeRef -> typeRef.coneType.classId?.shortClassName ?: return null
        is FirUserTypeRef -> typeRef.qualifier.last().name
        else -> return null
    }.asString()
    val annotationClass = expectedAnnotationClasses.firstOrNull { it.simpleName == shortName } ?: return null
    val constructor = annotationClass.constructors.firstOrNull() ?: return null
    val evaluatedArguments = evaluateArguments(session, firFile).orEmpty()
    val errors = mutableListOf<ScriptDiagnostic>()

    fun ConeKotlinType?.isString(): Boolean =
        this?.classId?.asFqNameString() == StandardNames.FqNames.string.asString()

    fun ConeKotlinType?.isArray(): Boolean =
        this?.classId?.asFqNameString() == StandardNames.FqNames.array.asString()

    fun FirElement.reportError(message: String) {
        errors += message.asErrorDiagnostics(path = firFile.name, location = null)
    }

    @OptIn(UnresolvedExpressionTypeAccess::class)
    fun FirElement.toArgument(argumentName: String): Any? {
        fun FirExpression.convertAsCollection(arguments: List<FirExpression>): Any? {
            val collectionType = coneTypeOrNull
            if (!collectionType.isArray()) {
                reportError("Only arrays are supported as annotation collections, but $collectionType was passed")
                return null
            }
            val elementType = collectionType?.typeArguments?.first()?.type
            return when {
                elementType.isString() -> Array(arguments.size) { index ->
                    arguments[index].toArgument("element of $argumentName") as? String
                }

                else -> {
                    reportError("Only strings are supported as annotation collection elements, but $elementType was passed")
                    null
                }
            }
        }

        return when (this) {
            is FirErrorExpression -> {
                reportError("Error resolving annotation argument: ${diagnostic.reason}")
                null
            }

            is FirGetClassCall -> {
                val classId = argument.coneTypeOrNull?.classId
                if (classId == null) {
                    reportError("Unable to resolve the class literal passed as $argumentName")
                    null
                } else {
                    runCatching { loadClass(classId) }.getOrElse { exception ->
                        reportError("Unable to load class ${classId.asSingleFqName()}: ${exception.message}")
                        null
                    }
                }
            }

            is FirLiteralExpression -> value
            is FirVarargArgumentsExpression -> convertAsCollection(arguments)
            is FirCollectionLiteral -> convertAsCollection(argumentList.arguments)
            else -> {
                reportError("Unsupported annotation argument type: ${this::class.simpleName}")
                null
            }
        }
    }

    (annotationTypeRef as? FirErrorTypeRef)?.let { typeRef ->
        return makeFailureResult(typeRef.diagnostic.reason)
    }

    val runtimeArguments = evaluatedArguments.map { (name, result) ->
        val argumentName = name.asString()
        argumentName to result.toArgument(argumentName)
    }
    val mapping = tryCreateCallableMappingFromNamedArgs(constructor, runtimeArguments)
        ?: mapResolvedClassLiteralArguments(constructor, runtimeArguments)
    if (mapping == null) {
        errors += "Unable to map annotation arguments".asErrorDiagnostics(path = firFile.name, location = null)
    }

    return when {
        errors.isNotEmpty() -> makeFailureResult(errors)
        else -> try {
            constructor.callBy(mapping!!).asSuccess()
        } catch (error: Error) {
            makeFailureResult(error.asDiagnostics())
        }
    }
}

/**
 * Kotlin's generic mapper compares KClass implementations through kotlin-reflect. Script annotations may be loaded
 * by a child class loader, so that comparison can reject an already FIR-validated class literal. In that case names
 * are enough to bind the resolved values to the annotation constructor.
 */
private fun mapResolvedClassLiteralArguments(
    constructor: KFunction<Annotation>,
    arguments: List<Pair<String?, Any?>>,
): Map<KParameter, Any?>? {
    if (arguments.none { (_, value) -> value is KClass<*> }) return null

    val unboundParameters = constructor.parameters.toMutableList()
    val mapping = LinkedHashMap<KParameter, Any?>(arguments.size)
    arguments.forEach { (name, value) ->
        val index = if (name == null) 0 else unboundParameters.indexOfFirst { it.name == name }
        if (index < 0 || index >= unboundParameters.size) return null
        mapping[unboundParameters.removeAt(index)] = value
    }
    if (unboundParameters.any { !it.isOptional && !it.isVararg }) return null
    return mapping
}

private fun FirAnnotationCall.evaluateArguments(session: FirSession, firFile: FirFile): Map<Name, FirExpression> {
    val scopeSession = ScopeSession()
    createImportingScopes(firFile, session, scopeSession)
    val dispatcher = object : FirAbstractBodyResolveTransformerDispatcher(
        session,
        FirResolvePhase.COMPILER_REQUIRED_ANNOTATIONS,
        scopeSession = scopeSession,
        implicitTypeOnly = false,
        expandTypeAliases = false,
        outerBodyResolveContext = null,
    ) {
        override val expressionsTransformer = FirExpressionsResolveTransformer(this)
        override val declarationsTransformer: FirDeclarationsResolveTransformer? = null
    }
    val transformer = dispatcher.expressionsTransformer
    val resolvedAnnotation = transformer.context.withFile(firFile, transformer.components) {
        withFileAnalysisExceptionWrapping(firFile) {
            transformer.transformAnnotationCall(this, ResolutionMode.ContextDependent) as FirAnnotationCall
        }
    }
    return resolvedAnnotation.argumentMapping.mapping
}

private fun ClassId.jvmBinaryName(): String {
    val relativeName = relativeClassName.asString().replace('.', '$')
    val packageName = packageFqName.asString()
    return if (packageName.isEmpty()) relativeName else "$packageName.$relativeName"
}

private fun loadClassLiteral(
    classId: ClassId,
    classpath: List<File>,
    contextClassLoader: ClassLoader?,
): KClass<*> {
    val className = classId.jvmBinaryName()
    val parent = contextClassLoader
        ?: Thread.currentThread().contextClassLoader
        ?: AnnotationFirScriptJvmCompiler::class.java.classLoader
    runCatching { Class.forName(className, false, parent).kotlin }
        .getOrNull()
        ?.let { return it }
    return URLClassLoader(
        classpath.map { file -> file.toURI().toURL() }.toTypedArray(),
        parent,
    ).use { loader -> Class.forName(className, false, loader).kotlin }
}
