@file:OptIn(ExperimentalCompilerApi::class)

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import junit.framework.TestCase.assertEquals
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.benf.cfr.reader.api.CfrDriver
import org.benf.cfr.reader.api.OutputSinkFactory
import org.benf.cfr.reader.api.SinkReturns
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.junit.Test
import java.util.*
import java.util.concurrent.ConcurrentHashMap


@Suppress("UNCHECKED_CAST")
class PropertiesTests {
    @OptIn(UnsafeCastFunction::class)
    @Test
    fun `Local properties migration to coroutine`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                    import ru.hollowhorizon.hollowengine.scripting.Suspendable                    
                    
                    @Suspendable
                    fun String.main(name: Int) {
                        val a = name
                        val b = "Hello"
                        val c = b + ", user: " + this
                        val system = System.out
                        var printer: (String) -> Unit = { system.println(it) }
                        printer(c)
                        printer = { system.println("Ответ: "+ it) }
                        printer(c)
                        val data = mapOf(c to 5f)
                    }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val coroutine = result.classLoader.loadClass("main\$SerializableCoroutine")
        val instance = coroutine.getConstructor().newInstance() as String.(Int) -> Any?
        "Халва".instance(10)
        val serializer = coroutine.getDeclaredField("<serializer>").get(instance) as KSerializer<Any>

        val json = Json.encodeToString(serializer, instance as Any)
        println(json)
        //coroutine.getDeclaredMethod("invoke", String::class.java).invoke(instance, "Халва")
    }

    @Test
    fun `Properties for suspend calls`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                    import ru.hollowhorizon.hollowengine.scripting.Suspendable                    
                    @Suspendable
                    fun example() = 0
                    @Suspendable
                    fun main() {
                        while(example() * example() * example() * example() * example() > 0) {
                            println("Hello")
                        }
                    }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val coroutine = result.classLoader.loadClass("MainSerializableCoroutine")
        val instance = coroutine.getConstructor().newInstance()
        coroutine.getDeclaredMethod("tick").invoke(instance)
    }

    @Test
    fun `Lambda expressions`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                import ru.hollowhorizon.hollowengine.scripting.Suspendable
                @Suspendable
                fun example() = 0
                @Suspendable
                fun main() {
                    var a = 1
                    var b = @Suspendable { a-- }
                    example()
                    b = @Suspendable { a++ }
                    println(b() + b())
                }
            """.trimIndent()
            )
        )

        val decompiledCode = ConcurrentHashMap<String, String>()

        println("Decompiled data: ")
        val sinkFactory = object : OutputSinkFactory {
            override fun getSupportedSinks(
                sinkType: OutputSinkFactory.SinkType,
                collection: MutableCollection<OutputSinkFactory.SinkClass>,
            ): MutableList<OutputSinkFactory.SinkClass> {
                return if (sinkType == OutputSinkFactory.SinkType.JAVA && OutputSinkFactory.SinkClass.DECOMPILED in collection) {
                    mutableListOf(
                        OutputSinkFactory.SinkClass.DECOMPILED,
                        OutputSinkFactory.SinkClass.STRING
                    )
                } else {
                    Collections.singletonList(OutputSinkFactory.SinkClass.STRING)
                }
            }

            override fun <T : Any?> getSink(
                p0: OutputSinkFactory.SinkType?,
                p1: OutputSinkFactory.SinkClass?,
            ): OutputSinkFactory.Sink<T> = OutputSinkFactory.Sink {
                (it as? SinkReturns.Decompiled)?.run {
                    decompiledCode[className] = java
                }
            }

        }

        val cfrDriver = CfrDriver.Builder()
            .withOutputSink(sinkFactory)
            .withOptions(mapOf())
            .build()
        cfrDriver.analyse(result.generatedFiles.map { it.absolutePath })

        decompiledCode.forEach { (file, code) ->
            println(file)
            println(code)
        }

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val coroutine = result.classLoader.loadClass("main\$SerializableCoroutine")
        val instance = coroutine.getConstructor().newInstance() as Function0<Any?>
        println(instance())
    }
}