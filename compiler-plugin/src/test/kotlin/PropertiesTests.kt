@file:OptIn(ExperimentalCompilerApi::class)

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test


class PropertiesTests {
    @Test
    fun `Local properties migration to coroutine`() {
        val result = compile(
            SourceFile.kotlin(
                "main.kt", """
                    import ru.hollowhorizon.hollowengine.scripting.Suspendable                    
                    
                    @Suspendable
                    fun main() {
                        val a = 3
                        val b = "Hello"
                        val c = b+", user"
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

        val coroutine = result.classLoader.loadClass("MainSerializableCoroutine")
        val instance = coroutine.getConstructor().newInstance()
        coroutine.getDeclaredMethod("tick").invoke(instance)
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
                fun main() {
                    val a = 1
                    val b = @Suspendable { a + 1 }
                    println(b())
                }
            """.trimIndent()
            )
        )
        
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        val coroutine = result.classLoader.loadClass("main\$SerializableCoroutine")
        val instance = coroutine.getConstructor().newInstance()
        coroutine.getDeclaredMethod("tick").invoke(instance)
    }
}