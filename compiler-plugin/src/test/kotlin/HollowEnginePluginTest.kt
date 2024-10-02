@file:OptIn(ExperimentalCompilerApi::class)

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationComponentRegistrar
import org.junit.Test
import ru.hollowhorizon.hollowengine.compiler.HollowEngineCompilerRegistrar

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
                            
                            while (time < 4) println("AAAA: "+data)
                        }
                        println(time+2)
                        return "Hello, World!"+time
                    }

                    fun main() {
                        val launcher = SuspendLauncher { 
                            debug(10)
                        }
                        
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

fun compile(
    sourceFile: SourceFile,
    plugin: CompilerPluginRegistrar = HollowEngineCompilerRegistrar(),
): JvmCompilationResult = compile(listOf(sourceFile), plugin)