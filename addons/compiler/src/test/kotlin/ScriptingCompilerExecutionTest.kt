
import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.ScriptLoader
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.annotations.Import
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.source.DirectoryScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.implicitReceivers
import kotlin.test.Test
import kotlin.test.assertEquals

abstract class HelloWorldScript(val output: MutableList<String>)

abstract class ImportingScript(val output: MutableList<String>)

data class ImportReceiver(val prefix: String)

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

    @Test
    fun `plain imported scripts do not receive root implicit receivers`() {
        val scriptsDirectory = File("build/tmp/script-import-execution").apply {
            deleteRecursively()
            mkdirs()
        }
        scriptsDirectory.resolve("models.kts").writeText(
            "data class ImportedModel(val value: Int = 42)",
        )
        scriptsDirectory.resolve("main.importing.kts").writeText(
            """
                @file:Import("models.kts")
                output += prefix + ":" + ImportedModel().value
            """.trimIndent(),
        )

        val defaultImports = listOf(Import::class.qualifiedName!!)
        val environment = ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = testClasspath(),
            scriptTypes = listOf(
                ScriptClassProvider(
                    extension = "kts",
                    baseClass = "kotlin.Any",
                    defaultImports = defaultImports,
                ),
                ScriptClassProvider(
                    extension = ".importing.kts",
                    baseClass = ImportingScript::class.qualifiedName!!,
                    defaultImports = defaultImports,
                    implicitReceivers = listOf(ImportReceiver::class),
                ),
            ),
            mappings = Mappings.EMPTY,
        )
        val namespace = "script-import-execution"
        val source = DirectoryScriptSource(
            namespace = namespace,
            directory = scriptsDirectory,
            classLoader = ScriptingCompilerExecutionTest::class.java.classLoader,
            fingerprint = "test",
        )

        try {
            ScriptRegistry.register(source)
            ScriptingEnvironment.INSTANCE = environment
            val id = ScriptId(namespace, "main.importing.kts")
            val compiled = ScriptLoader.compile(id).getOrThrow()
            assertEquals(1, compiled.implicitReceiverCount)
            assertEquals(3, compiled.type.java.constructors.single().parameterCount)

            val cached = ScriptLoader.compile(id).getOrThrow()
            assertEquals(1, cached.implicitReceiverCount)

            val output = mutableListOf<String>()
            ScriptLoader.execute<ImportingScript>(id) {
                constructorArgs(output as Any)
                implicitReceivers(ImportReceiver("model"))
            }.getOrThrow()

            assertEquals(listOf("model:42"), output)
        } finally {
            ScriptRegistry.unregister(namespace)
            ScriptingEnvironment.clear()
            environment.close()
            scriptsDirectory.deleteRecursively()
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
