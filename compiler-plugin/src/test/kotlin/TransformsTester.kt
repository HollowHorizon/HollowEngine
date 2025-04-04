import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.Test
import java.io.File

class TransformsTester {
    @OptIn(ExperimentalCompilerApi::class)
    @Test
    fun test() {
        File("src/test/transforms/").walk()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { SourceFile.kotlin(it.name, it.readText()) to it.name }
            .forEach { (file, name) ->
                val result = compile(file)

                if(result.exitCode != KotlinCompilation.ExitCode.OK) {
                    CfrHelper.decompile(result)
                }
                assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

                println("Тест [$name] прошёл.")
            }
    }
}