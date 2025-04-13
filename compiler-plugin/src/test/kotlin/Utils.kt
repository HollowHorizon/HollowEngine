@file:OptIn(ExperimentalCompilerApi::class)

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import ru.hollowhorizon.hollowengine.compiler.HollowEngineCompilerRegistrar
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvmhost.JvmScriptCompiler

fun ClassLoader.loadCoroutine(name: String) = loadClass("$name\$SerializableCoroutine")
    .getConstructor().newInstance()

val json = Json {
    prettyPrint = true
}

fun compile(
    sourceFiles: List<SourceFile>,
    plugin: CompilerPluginRegistrar = HollowEngineCompilerRegistrar(),
): JvmCompilationResult {
    return KotlinCompilation().apply {
        sources = sourceFiles
        compilerPluginRegistrars = listOf(plugin, SerializationComponentRegistrar())
        inheritClassPath = true
    }.compile()
}

inline fun <reified T : Any> compileScript(text: String) = runBlocking {
    val hostConfiguration = TestingScriptingHost()
    val compilationConfiguration = createCompilationConfiguration<T>(hostConfiguration)

    val compiler = JvmScriptCompiler(hostConfiguration)
    compiler(StringScriptSource(text), compilationConfiguration).apply {
        logErrors(this)
    }
}

fun logErrors(result: ResultWithDiagnostics<*>) {
    result.errors().forEach(::println)
    result.errors().mapNotNull { it.exception }.distinct().forEach {
        println(it.stackTraceToString())
    }
}

data class ScriptError(
    val severity: Severity,
    val message: String,
    val source: String,
    val line: Int,
    val column: Int,
    val exception: Throwable?,
) {

    enum class Severity { DEBUG, INFO, WARNING, ERROR, FATAL }

    override fun toString() = "$message at $source:$line:$column"
}

private fun ResultWithDiagnostics<*>.errors() = reports.map {
    ScriptError(
        ScriptError.Severity.entries[it.severity.ordinal],
        it.message,
        it.sourcePath ?: "",
        it.location?.start?.line ?: 0,
        it.location?.start?.col ?: 0,
        it.exception
    )
}

@KotlinScript(
    displayName = "Story Event",
    fileExtension = "story.kts",
    compilationConfiguration = Configuration::class
)
abstract class StoryEvent {
    val hello = "world"

    abstract fun tick(): Any?
}

class Configuration : ScriptCompilationConfiguration({
    jvm {
        compilerOptions(
            "-opt-in=kotlin.time.ExperimentalTime,kotlin.ExperimentalStdlibApi",
            "-jvm-target=17",
            "-Xadd-modules=ALL-MODULE-PATH" // Loading kotlin from shadowed jar
        )

        dependenciesFromCurrentContext(wholeClasspath = true)
    }

    ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
})

class TestingScriptingHost : ScriptingHostConfiguration({
    getScriptingClass(JvmGetScriptingClass())
    classpathFromClassloader(Thread.currentThread().contextClassLoader)
})

inline fun <reified T : Any> createCompilationConfiguration(hostConfiguration: TestingScriptingHost) =
    createCompilationConfigurationFromTemplate(
        KotlinType(T::class),
        hostConfiguration,
        TestingScriptingHost::class
    ) {}

fun compile(
    sourceFile: SourceFile,
    plugin: CompilerPluginRegistrar = HollowEngineCompilerRegistrar(),
): JvmCompilationResult = compile(listOf(sourceFile), plugin)