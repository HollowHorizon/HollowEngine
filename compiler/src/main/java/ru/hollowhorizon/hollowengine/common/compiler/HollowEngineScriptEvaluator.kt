package ru.hollowhorizon.hollowengine.common.compiler

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.util.*
import kotlin.reflect.KClass
import kotlin.script.experimental.api.*
import kotlin.script.experimental.impl._languageVersion
import kotlin.script.experimental.jvm.JvmScriptEvaluationConfigurationKeys
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection

internal val JvmScriptEvaluationConfigurationKeys.actualClassLoader by PropertiesCollection.key<ClassLoader?>(
    isTransient = true
)
internal val JvmScriptEvaluationConfigurationKeys.scriptsInstancesSharingMap by PropertiesCollection.key<MutableMap<KClass<*>, EvaluationResult>>(
    isTransient = true
)

open class HollowEngineScriptEvaluator : ScriptEvaluator {
    companion object {
        private val constructorCache: MutableMap<Class<*>, MethodHandle> =
            Collections.synchronizedMap(WeakHashMap())
    }

    override suspend operator fun invoke(
        compiledScript: CompiledScript,
        scriptEvaluationConfiguration: ScriptEvaluationConfiguration,
    ): ResultWithDiagnostics<EvaluationResult> = try {
        compiledScript.getClass(scriptEvaluationConfiguration).onSuccess { scriptClass ->
            val sharedConfiguration = scriptEvaluationConfiguration.getOrPrepareShared(scriptClass.java.classLoader)
            val configurationForOtherScripts by lazy {
                sharedConfiguration.with {
                    reset(ScriptEvaluationConfiguration.previousSnippets)
                    reset(ScriptEvaluationConfiguration.constructorArgs)
                }
            }
            val sharedScripts = sharedConfiguration[ScriptEvaluationConfiguration.jvm.scriptsInstancesSharingMap]

            sharedScripts?.get(scriptClass)?.asSuccess()
                ?: compiledScript.otherScripts.mapSuccess {
                    invoke(it, configurationForOtherScripts)
                }.onSuccess { importedScriptsEvalResults ->

                    val refinedEvalConfiguration =
                        sharedConfiguration.with {
                            compilationConfiguration(compiledScript.compilationConfiguration)
                        }.refineBeforeEvaluation(compiledScript).valueOr {
                            return@invoke ResultWithDiagnostics.Failure(it.reports)
                        }

                    val resultValue = try {
                        val instance =
                            scriptClass.evalWithConfigAndOtherScriptsResults(
                                refinedEvalConfiguration,
                                importedScriptsEvalResults
                            )

                        compiledScript.resultField?.let { (resultFieldName, resultType) ->
                            scriptClass.java.declaredFields.find { it.name == resultFieldName }?.let {
                                it.isAccessible = true
                                ResultValue.Value(
                                    resultFieldName,
                                    it.get(instance),
                                    resultType.typeName,
                                    scriptClass,
                                    instance
                                )
                            } ?: ResultValue.Unit(scriptClass, instance)
                        } ?: ResultValue.Unit(scriptClass, instance)

                    } catch (e: InvocationTargetException) {
                        ResultValue.Error(e.targetException ?: e, e, scriptClass)
                    } catch (e: Throwable) {
                        // Ловим исключения от MethodHandle
                        ResultValue.Error(e, e, scriptClass)
                    }

                    EvaluationResult(resultValue, refinedEvalConfiguration).let {
                        sharedScripts?.put(scriptClass, it)
                        ResultWithDiagnostics.Success(it)
                    }
                }
        }
    } catch (e: Throwable) {
        ResultWithDiagnostics.Failure(
            e.asDiagnostics(path = compiledScript.sourceLocationId)
        )
    }

    private fun KClass<*>.evalWithConfigAndOtherScriptsResults(
        refinedEvalConfiguration: ScriptEvaluationConfiguration,
        importedScriptsEvalResults: List<EvaluationResult>,
    ): Any {
        val isCompiledWithK2 =
            refinedEvalConfiguration[ScriptEvaluationConfiguration.compilationConfiguration]
                ?.get(ScriptCompilationConfiguration._languageVersion)
                ?.let { it.substringBefore('.').toIntOrNull()?.let { ver -> ver >= 2 } } == true

        val providedProps = refinedEvalConfiguration[ScriptEvaluationConfiguration.providedProperties]
        val implicitReceivers = refinedEvalConfiguration[ScriptEvaluationConfiguration.implicitReceivers]
        val ctorArgs = refinedEvalConfiguration[ScriptEvaluationConfiguration.constructorArgs]
        val prevSnippets = refinedEvalConfiguration[ScriptEvaluationConfiguration.previousSnippets]

        var estimatedSize = importedScriptsEvalResults.size
        if (prevSnippets != null) estimatedSize++
        if (ctorArgs != null) estimatedSize += ctorArgs.size
        if (implicitReceivers != null) estimatedSize += implicitReceivers.size
        if (providedProps != null) estimatedSize += providedProps.size * (if (isCompiledWithK2) 2 else 1)

        val args = ArrayList<Any?>(estimatedSize)

        prevSnippets?.let { args.add(it.toTypedArray()) }
        ctorArgs?.let { args.addAll(it) }

        if (isCompiledWithK2) {
            providedProps?.forEach { args.add(it.value) }
        }

        importedScriptsEvalResults.forEach {
            args.add(it.returnValue.scriptInstance)
        }

        implicitReceivers?.let { args.addAll(it) }

        if (!isCompiledWithK2) {
            providedProps?.forEach { args.add(it.value) }
        }

        @Suppress("UNCHECKED_CAST")
        val wrapper: ScriptExecutionWrapper<Any>? =
            refinedEvalConfiguration[ScriptEvaluationConfiguration.scriptExecutionWrapper] as ScriptExecutionWrapper<Any>?

        val saveClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this.java.classLoader
        return try {
            val constructorHandle = constructorCache.computeIfAbsent(this.java) { clazz ->
                val ctor = clazz.constructors.single()
                ctor.isAccessible = true
                MethodHandles.publicLookup().unreflectConstructor(ctor)
            }

            wrapper?.invoke { constructorHandle.invokeWithArguments(args) }
                ?: constructorHandle.invokeWithArguments(args)
        } finally {
            Thread.currentThread().contextClassLoader = saveClassLoader
        }
    }
}

private fun ScriptEvaluationConfiguration.getOrPrepareShared(classLoader: ClassLoader): ScriptEvaluationConfiguration =
    if (this[ScriptEvaluationConfiguration.jvm.actualClassLoader] != null)
        this
    else
        with {
            ScriptEvaluationConfiguration.jvm.actualClassLoader(classLoader)
            if (this[ScriptEvaluationConfiguration.scriptsInstancesSharing] == true) {
                ScriptEvaluationConfiguration.jvm.scriptsInstancesSharingMap(mutableMapOf())
            }
        }
