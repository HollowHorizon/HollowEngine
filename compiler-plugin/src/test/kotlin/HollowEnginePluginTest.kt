@file:OptIn(ExperimentalCompilerApi::class)

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.junit.Test
import ru.hollowhorizon.hollowengine.compiler.HollowEngineCompilerRegistrar
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.util.classpathFromClassloader
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.jvmhost.saveToJar
import kotlin.test.assertFalse

class PluginTester {
    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun `Scripting compiler test`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                    fun main() {
                        println(debug())
                    }
                    fun debug() = "Hello, World!"
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }

    @Test
    fun `Suspendable test`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                    import ru.hollowhorizon.hollowengine.scripting.Suspendable
                    import ru.hollowhorizon.hollowengine.compiler.suspendable.*

                    @Suspendable
                    fun debug(time: Int): String {
                        println(time)
                        var data = 2
                        await(time > 5)
                        val dataConst = 1
                        while(time<5) {
                            data++
                            var aaa = 0
                            aaa+=10
                            println(time+aaa)                            
                            do {
                                println("aaa")
                            } while(time > 3)
                            while (time < 4) println("AAAA: "+data)
                        }
                        println(time+2)
                        return "Hello, World!"+time
                    }

                    fun main() {
                        val launcher = SuspendLauncher { 
                            //debug(10)
                        }
                        
                        launcher.tick()
                        if(launcher.isEnd) println(launcher.result)
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val type = result.classLoader.loadClass("MainKt")

        type.declaredMethods
            .first { it.name == "main" && it.parameterCount != 0 }
            .invoke(null, null)
    }

    @Test
    fun `Multi-Suspendable test`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                    import ru.hollowhorizon.hollowengine.scripting.Suspendable
                    import ru.hollowhorizon.hollowengine.scripting.Ignore
                    import ru.hollowhorizon.hollowengine.compiler.suspendable.*

                    @Suspendable
                    fun test(time: Int): Int {
                        println(time)
                        val data = 2
                        for(i in 1..10) println(i)
                        if(data > 3) {
                            test(1242)
                            val r = test(1242)
                            test(r)
                        }
                        return data
                    }
                    @Suspendable
                    fun debug(time: Int): Int {
                        println(time)
                        test(time+1)
                        println(time)
                        test(test(test(time+1)))
                        println(time)
                        test(time+1)
                        println(time)
                        test(time+1)
                        println(time)
                        return test(time+1)
                    }

                    fun main() {
                        val launcher = SuspendLauncher {}
                        
                        launcher.tick()
                        if(launcher.isEnd) println(launcher.result)
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        result.classLoader.loadClass("MainKt").declaredMethods
            .first { it.name == "main" && it.parameterCount != 0 }
            .invoke(null, null)
    }

    @Test
    fun `Loop Suspendable test`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                    import ru.hollowhorizon.hollowengine.scripting.*
                    import ru.hollowhorizon.hollowengine.compiler.suspendable.*

                    @Suspendable
                    fun debug(time: Int): Int {
                        println("Hmm")
                        val test = 1
                        val r = async { 
                            // Тут могут быть вызваны suspendable функции
                            time + test
                        }
                        println("1")
                        r.start()
                        println("2")
                        r.join()
                        println("3")
                        r.stop()
                        return 0
                    }
                    

                    fun main() {
                        val launcher = SuspendLauncher {}
                        
                        launcher.tick()
                        if(launcher.isEnd) println(launcher.result)
                    }
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        result.classLoader.loadClass("MainKt").declaredMethods
            .first { it.name == "main" && it.parameterCount != 0 }
            .invoke(null, null)
    }

    // Я без понятия почему, но при запуске общих тестов он как будто обрабатывает этот код несколько раз
    // При запуске конкретно этого теста проблемы нет.
    @Test
    fun `Script Test`() {
        val result = compileScript<StoryEvent>(
            """
            import ru.hollowhorizon.hollowengine.scripting.*
            import ru.hollowhorizon.hollowengine.compiler.suspendable.*
            
            fun test() {println("helloworld")}
            println("Hello")
            var data = 1
            println(data)
            println(hello)
            
            val async = async {
                await(data>10)
                println("Data is more than 10!")
            }

            async.start()

            data = 50
            async.join()
            println(message = "aaa")
            println({"hello"+data})
            data += 2
            """.trimIndent()
        )

        assertFalse(result.isError())

        (result.valueOrThrow() as? KJvmCompiledScript)
            ?.saveToJar(File("script.jar"))

        val r = runBlocking { BasicJvmScriptEvaluator().invoke(result.valueOrThrow(), ScriptEvaluationConfiguration()) }

        (r.valueOrThrow().returnValue.scriptInstance as StoryEvent).tick(SuspendContext())

        println(r)
    }
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

    abstract fun tick(context: SuspendContext): Any?
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