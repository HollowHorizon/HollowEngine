@file:OptIn(ExperimentalCompilerApi::class)

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import ru.hollowhorizon.hollowengine.compiler.HollowEngineCompilerRegistrar

class PluginTester {
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
                    fun main() {
                        println(debug())
                    }
                    @Suspendable
                    fun debug() = "Hello, World!"
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)
    }
}

fun compile(
    sourceFiles: List<SourceFile>,
    plugin: CompilerPluginRegistrar = HollowEngineCompilerRegistrar(),
): JvmCompilationResult {
    return KotlinCompilation().apply {
        sources = sourceFiles
        compilerPluginRegistrars = listOf(plugin)
        inheritClassPath = true
    }.compile()
}

fun compile(
    sourceFile: SourceFile,
    plugin: CompilerPluginRegistrar = HollowEngineCompilerRegistrar(),
): JvmCompilationResult {
    return compile(listOf(sourceFile), plugin)
}