package ru.hollowhorizon.hollowengine.common.compiler

import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass
import kotlin.script.experimental.api.*
import kotlin.script.experimental.api.CompiledScript
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

    override suspend operator fun invoke(
        compiledScript: CompiledScript,
        scriptEvaluationConfiguration: ScriptEvaluationConfiguration,
    ): ResultWithDiagnostics<EvaluationResult> = try {
        compiledScript.getClass(scriptEvaluationConfiguration).onSuccess { scriptClass ->

            // configuration shared between all module scripts
            val sharedConfiguration = scriptEvaluationConfiguration.getOrPrepareShared(scriptClass.java.classLoader)
            val configurationForOtherScripts by lazy {
                sharedConfiguration.with {
                    reset(ScriptEvaluationConfiguration.previousSnippets)
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
                        // in the future, when (if) we'll stop to compile everything into constructor
                        // run as SAM
                        // return res

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
                ?.let {
                    it.substringBefore('.').toIntOrNull()?.let { it >= 2 }
                } == true

        val args = ArrayList<Any?>()

        refinedEvalConfiguration[ScriptEvaluationConfiguration.previousSnippets]?.let {
            args.add(it.toTypedArray())
        }

        refinedEvalConfiguration[ScriptEvaluationConfiguration.constructorArgs]?.let {
            args.addAll(it)
        }

        if (isCompiledWithK2) {
            refinedEvalConfiguration[ScriptEvaluationConfiguration.providedProperties]?.forEach {
                args.add(it.value)
            }
        }

        importedScriptsEvalResults.forEach {
            args.add(it.returnValue.scriptInstance)
        }

        refinedEvalConfiguration[ScriptEvaluationConfiguration.implicitReceivers]?.let {
            args.addAll(it)
        }

        if (!isCompiledWithK2) {
            refinedEvalConfiguration[ScriptEvaluationConfiguration.providedProperties]?.forEach {
                args.add(it.value)
            }
        }

        val ctor = java.constructors.single()

        @Suppress("UNCHECKED_CAST")
        val wrapper: ScriptExecutionWrapper<Any>? =
            refinedEvalConfiguration[ScriptEvaluationConfiguration.scriptExecutionWrapper] as ScriptExecutionWrapper<Any>?

        val saveClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this.java.classLoader
        return try {
            if (wrapper == null) {
                ctor.newInstance(*args.toArray())
            } else wrapper.invoke {
                ctor.newInstance(*args.toArray())
            }
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
