
import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class HelloWorldScript(val output: MutableList<String>)

class ScriptingCompilerExecutionTest {
    @Test
    fun `hello world script compiles and executes`() {
        val environment = ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = testClasspath(),
            scriptTypes = listOf(
                ScriptClassProvider(
                    extension = ".hello.kts",
                    baseClass = HelloWorldScript::class.qualifiedName!!,
                )
            ),
            mappings = Mappings.EMPTY,
        )

        try {
            ScriptingEnvironment.INSTANCE = environment
            val output = mutableListOf<String>()
            val script = environment.compiler.compile(
                "hello.hello.kts",
                """output += "Hello, World!"""",
            ).getOrThrow()

            script.execute<Any> {
                constructorArgs(output as Any)
            }.getOrThrow()

            assertEquals(listOf("Hello, World!"), output)
        } finally {
            ScriptingEnvironment.clear()
            environment.close()
            File("hollowengine").deleteRecursively()
        }
    }

    private fun testClasspath(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .asSequence()
            .filter(String::isNotBlank)
            .map(::File)
            .filter(File::exists)
            .distinctBy { it.absoluteFile.normalize() }
            .toList()
    }
}
