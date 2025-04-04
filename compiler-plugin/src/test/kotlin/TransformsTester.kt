import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

class TransformsTester {

    @OptIn(ExperimentalCompilerApi::class)
    @TestFactory
    fun dynamicFileTests(): List<DynamicNode> {
        val filePaths = File("src/test/transforms/").walk()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { SourceFile.kotlin(it.name, it.readText()) to it.name }

        return filePaths.map { (file, name) ->
            DynamicTest.dynamicTest("Test for file: $name") {
                val result = compile(file)

                if (result.exitCode != KotlinCompilation.ExitCode.OK) {
                    CfrHelper.decompile(result)
                }

                assertEquals(result.exitCode, KotlinCompilation.ExitCode.OK)
            }
        }.toList()
    }
}