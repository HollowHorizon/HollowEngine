package ru.hollowhorizon.hollowengine.common.scripting.compiling

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptEvaluationException
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Position
import ru.hollowhorizon.hollowengine.common.scripting.ide.Range
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import java.io.File
import java.io.InputStream
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.security.ProtectionDomain
import java.util.Collections
import java.util.WeakHashMap
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import kotlin.io.inputStream
import kotlin.io.readBytes
import kotlin.reflect.KClass
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.ScriptEvaluator
import kotlin.script.experimental.api.ScriptExecutionWrapper
import kotlin.script.experimental.api.asDiagnostics
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.compilationConfiguration
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.mapSuccess
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.api.previousSnippets
import kotlin.script.experimental.api.providedProperties
import kotlin.script.experimental.api.refineBeforeEvaluation
import kotlin.script.experimental.api.resultField
import kotlin.script.experimental.api.scriptExecutionWrapper
import kotlin.script.experimental.api.scriptsInstancesSharing
import kotlin.script.experimental.api.valueOr
import kotlin.script.experimental.api.with
import kotlin.script.experimental.impl._languageVersion
import kotlin.script.experimental.jvm.JvmScriptEvaluationConfigurationKeys
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.impl.createScriptFromClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.script.experimental.api.CompiledScript as KotlinCompiledScript

fun File.loadKotlinCompiledScriptFromJar(): CompiledScript {
    val className = inputStream().use { input ->
        JarInputStream(input).use { jar ->
            jar.manifest.mainAttributes.getValue("Main-Class")
                ?: throw IllegalArgumentException("No Main-Class manifest attribute")
        }
    }
    val script = KJvmCompiledScriptFromJar(className, this)
    return KotlinCompiledScriptJar(nameWithoutExtension, script, ScriptEvaluationConfiguration())
}

internal class KotlinCompiledScriptJar(
    override val name: String,
    private val script: KotlinCompiledScript,
    private val evaluationConfiguration: ScriptEvaluationConfiguration,
) : CompiledScript {
    private val evaluator = KotlinCompiledScriptEvaluator()
    private val scriptClass = lazy {
        runBlocking {
            (script.getClass(evaluationConfiguration) as ResultWithDiagnostics.Success).value
        }
    }

    override val type: KClass<*>
        get() = scriptClass.value

    override fun <T> execute(vararg constructorArgs: Any?): Result<T> {
        val result = runBlocking {
            evaluator(script, evaluationConfiguration.with {
                constructorArgs(*constructorArgs)
            })
        }

        return if (result is ResultWithDiagnostics.Success) {
            @Suppress("UNCHECKED_CAST")
            Result.success(result.value.returnValue.scriptInstance as T)
        } else {
            Result.failure(ScriptEvaluationException(name, result.reports.map { it.convert() }))
        }
    }
}

internal val JvmScriptEvaluationConfigurationKeys.actualClassLoader by PropertiesCollection.key<ClassLoader?>(
    isTransient = true
)

internal val JvmScriptEvaluationConfigurationKeys.scriptsInstancesSharingMap by PropertiesCollection.key<MutableMap<KClass<*>, EvaluationResult>>(
    isTransient = true
)

class KotlinCompiledScriptEvaluator : ScriptEvaluator {
    companion object {
        private val constructorCache: MutableMap<Class<*>, MethodHandle> =
            Collections.synchronizedMap(WeakHashMap())
    }

    override suspend operator fun invoke(
        compiledScript: KotlinCompiledScript,
        scriptEvaluationConfiguration: ScriptEvaluationConfiguration,
    ): ResultWithDiagnostics<EvaluationResult> = try {
        compiledScript.getClass(scriptEvaluationConfiguration).onSuccess { scriptClass ->
            val sharedConfiguration = scriptEvaluationConfiguration.getOrPrepareShared(scriptClass.java.classLoader)
            val configurationForOtherScripts by lazy { sharedConfiguration }
            val sharedScripts = sharedConfiguration[ScriptEvaluationConfiguration.jvm.scriptsInstancesSharingMap]

            sharedScripts?.get(scriptClass)?.asSuccess()
                ?: compiledScript.otherScripts.mapSuccess {
                    invoke(it, configurationForOtherScripts)
                }.onSuccess { importedScriptsEvalResults ->
                    val refinedEvaluationConfiguration =
                        sharedConfiguration.with {
                            compilationConfiguration(compiledScript.compilationConfiguration)
                        }.refineBeforeEvaluation(compiledScript).valueOr {
                            return@invoke ResultWithDiagnostics.Failure(it.reports)
                        }

                    val resultValue = try {
                        val instance = scriptClass.evalWithConfigAndOtherScriptsResults(
                            refinedEvaluationConfiguration,
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
                    } catch (error: InvocationTargetException) {
                        ResultValue.Error(error.targetException ?: error, error, scriptClass)
                    } catch (error: Throwable) {
                        ResultValue.Error(error, error, scriptClass)
                    }

                    EvaluationResult(resultValue, refinedEvaluationConfiguration).let {
                        sharedScripts?.put(scriptClass, it)
                        ResultWithDiagnostics.Success(it)
                    }
                }
        }
    } catch (error: Throwable) {
        ResultWithDiagnostics.Failure(
            error.asDiagnostics(path = compiledScript.sourceLocationId)
        )
    }

    private fun KClass<*>.evalWithConfigAndOtherScriptsResults(
        refinedEvaluationConfiguration: ScriptEvaluationConfiguration,
        importedScriptsEvalResults: List<EvaluationResult>,
    ): Any {
        val isCompiledWithK2 =
            refinedEvaluationConfiguration[ScriptEvaluationConfiguration.compilationConfiguration]
                ?.get(ScriptCompilationConfiguration._languageVersion)
                ?.let { it.substringBefore('.').toIntOrNull()?.let { version -> version >= 2 } } == true

        val providedProps = refinedEvaluationConfiguration[ScriptEvaluationConfiguration.providedProperties]
        val implicitReceivers = refinedEvaluationConfiguration[ScriptEvaluationConfiguration.implicitReceivers]
        val ctorArgs = refinedEvaluationConfiguration[ScriptEvaluationConfiguration.constructorArgs]
        val previousSnippets = refinedEvaluationConfiguration[ScriptEvaluationConfiguration.previousSnippets]

        var estimatedSize = importedScriptsEvalResults.size
        if (previousSnippets != null) estimatedSize++
        if (ctorArgs != null) estimatedSize += ctorArgs.size
        if (implicitReceivers != null) estimatedSize += implicitReceivers.size
        if (providedProps != null) estimatedSize += providedProps.size * (if (isCompiledWithK2) 2 else 1)

        val args = ArrayList<Any?>(estimatedSize)

        previousSnippets?.let { args.add(it.toTypedArray()) }
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
        val wrapper = refinedEvaluationConfiguration[ScriptEvaluationConfiguration.scriptExecutionWrapper]
            as ScriptExecutionWrapper<Any>?

        val previousClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = java.classLoader
        return try {
            val constructorHandle = constructorCache.computeIfAbsent(java) { type ->
                val constructor = type.constructors.single()
                constructor.isAccessible = true
                MethodHandles.publicLookup().unreflectConstructor(constructor)
            }

            wrapper?.invoke { constructorHandle.invokeWithArguments(args) }
                ?: constructorHandle.invokeWithArguments(args)
        } finally {
            Thread.currentThread().contextClassLoader = previousClassLoader
        }
    }
}

private fun ScriptEvaluationConfiguration.getOrPrepareShared(classLoader: ClassLoader): ScriptEvaluationConfiguration =
    if (this[ScriptEvaluationConfiguration.jvm.actualClassLoader] != null) {
        this
    } else {
        with {
            ScriptEvaluationConfiguration.jvm.actualClassLoader(classLoader)
            if (this[ScriptEvaluationConfiguration.scriptsInstancesSharing] == true) {
                ScriptEvaluationConfiguration.jvm.scriptsInstancesSharingMap(mutableMapOf())
            }
        }
    }

internal class KJvmCompiledScriptFromJar(
    private val scriptClassName: String,
    private val file: File,
) : KotlinCompiledScript {
    private var loadedScript: KJvmCompiledScript? = null

    private fun getScriptOrFail(): KJvmCompiledScript = loadedScript
        ?: throw IllegalStateException("Compiled script is not loaded yet")

    override suspend fun getClass(scriptEvaluationConfiguration: ScriptEvaluationConfiguration?): ResultWithDiagnostics<KClass<*>> {
        if (loadedScript != null) return getScriptOrFail().getClass(scriptEvaluationConfiguration)

        val actualEvaluationConfiguration = scriptEvaluationConfiguration ?: ScriptEvaluationConfiguration()
        val baseClassLoader = actualEvaluationConfiguration[ScriptEvaluationConfiguration.jvm.baseClassLoader]
            ?: Thread.currentThread().contextClassLoader
        val classLoader = createScriptMemoryClassLoader(baseClassLoader)
        loadedScript = createScriptFromClassLoader(scriptClassName, classLoader)

        return getScriptOrFail().getClass(scriptEvaluationConfiguration)
    }

    override val compilationConfiguration: ScriptCompilationConfiguration
        get() = getScriptOrFail().compilationConfiguration

    override val sourceLocationId: String?
        get() = loadedScript?.sourceLocationId

    override val otherScripts: List<KotlinCompiledScript>
        get() = getScriptOrFail().otherScripts

    override val resultField
        get() = getScriptOrFail().resultField

    private fun createScriptMemoryClassLoader(parent: ClassLoader?): ClassLoader {
        val jarFile = JarFile(file)
        val entries = jarFile.use { jar ->
            jar.entries().asSequence().associate { it.name to jar.getInputStream(it).readBytes() }
        }
        return MemoryClassLoader(entries, parent)
    }
}

internal class MemoryClassLoader(
    private val resources: Map<String, ByteArray>,
    parent: ClassLoader?,
) : ClassLoader(parent) {
    override fun findClass(name: String): Class<*> {
        val resource = name.replace('.', '/') + ".class"

        return resources[resource]?.let { bytes ->
            val protectionDomain = ProtectionDomain(null, null)
            defineClass(name, bytes, 0, bytes.size, protectionDomain)
        } ?: throw ClassNotFoundException(name)
    }

    override fun getResourceAsStream(name: String): InputStream? = resources[name]?.inputStream()
}

fun ScriptDiagnostic.convert(): Diagnostic {
    return Diagnostic(
        location?.let {
            Range(
                Position(it.start.line, it.start.col),
                Position(it.end?.line ?: it.start.line, it.end?.col ?: it.start.col)
            )
        } ?: Range(Position(-1, -1), Position(-1, -1)),
        when (severity) {
            ScriptDiagnostic.Severity.DEBUG -> Severity.DEBUG
            ScriptDiagnostic.Severity.INFO -> Severity.INFO
            ScriptDiagnostic.Severity.WARNING -> Severity.WARNING
            ScriptDiagnostic.Severity.ERROR -> Severity.ERROR
            ScriptDiagnostic.Severity.FATAL -> Severity.FATAL
        },
        message
    )
}
