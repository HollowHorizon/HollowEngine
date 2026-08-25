
import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.ScriptLoader
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.annotations.Import
import ru.hollowhorizon.hollowengine.common.scripting.annotations.SharedScript
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.source.DirectoryScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.implicitReceivers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

abstract class HelloWorldScript(val output: MutableList<String>)

abstract class ImportingScript(val output: MutableList<String>)

data class ImportReceiver(val prefix: String)

class ScriptingCompilerExecutionTest {
    @Test
    fun `class literal attachment resolves default imports explicit imports and aliases`() {
        val environment = ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = testClasspath(),
            scriptTypes = listOf(
                ScriptClassProvider(
                    extension = ".hello.kts",
                    baseClass = HelloWorldScript::class.qualifiedName!!,
                    defaultImports = listOf(File::class.qualifiedName!!, "java.nio.file.*"),
                )
            ),
            mappings = Mappings.EMPTY,
        )

        try {
            ScriptingEnvironment.INSTANCE = environment
            val output = mutableListOf<String>()
            val scripts = listOf(
                Triple(
                    "default-class.hello.kts",
                    """
                        @file:Attach(File::class)

                        output += name
                    """.trimIndent(),
                    File("attached-host"),
                ),
                Triple(
                    "default-imported.hello.kts",
                    """
                        @file:Attach(Path::class)

                        output += fileName.toString()
                    """.trimIndent(),
                    Path.of("folder", "attached-host"),
                ),
                Triple(
                    "imported.hello.kts",
                    """
                        @file:Attach(URI::class)

                        import java.net.URI

                        output += scheme
                    """.trimIndent(),
                    URI.create("https://example.com"),
                ),
                Triple(
                    "aliased.hello.kts",
                    """
                        @file:Attach(AttachedUri::class)

                        import java.net.URI as AttachedUri

                        output += scheme
                    """.trimIndent(),
                    URI.create("https://example.com"),
                ),
            )

            scripts.forEach { (name, source, receiver) ->
                val script = environment.compiler.compile(name, source).getOrThrow()

                assertEquals(1, script.implicitReceiverCount)
                script.execute<Any> {
                    constructorArgs(output as Any)
                    implicitReceivers(receiver)
                }.getOrThrow()
            }

            assertEquals(listOf("attached-host", "attached-host", "https", "https"), output)
        } finally {
            ScriptingEnvironment.clear()
            environment.close()
            File("hollowengine").deleteRecursively()
        }
    }

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
    fun `plain imported scripts expose typed declarations without root implicit receivers`() {
        val scriptsDirectory = File("build/tmp/script-import-execution").apply {
            deleteRecursively()
            mkdirs()
        }
        scriptsDirectory.resolve("models.kts").writeText(
            """
                data class ImportedModel(val value: Int = 42)
                fun hasOnline(players: Int): Boolean = players > 0
            """.trimIndent(),
        )
        scriptsDirectory.resolve("main.importing.kts").writeText(
            """
                @file:Import("models.kts")
                output += prefix + ":" + ImportedModel().value + ":" + hasOnline(15)
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

            assertEquals(listOf("model:42:true"), output)
        } finally {
            ScriptRegistry.unregister(namespace)
            ScriptingEnvironment.clear()
            environment.close()
            scriptsDirectory.deleteRecursively()
            File("hollowengine").deleteRecursively()
        }
    }

    @Test
    fun `a function taking a type of its own imported script is not ambiguous`() {
        val scriptsDirectory = File("build/tmp/script-import-declarations").apply {
            deleteRecursively()
            mkdirs()
        }
        scriptsDirectory.resolve("core.kts").writeText(
            """
                class Faction(val title: String)
                val factions = mutableListOf<String>()
                fun register(faction: Faction) { factions += faction.title }
            """.trimIndent(),
        )
        scriptsDirectory.resolve("main.importing.kts").writeText(
            """
                @file:Import("core.kts")
                register(Faction("science"))
                output += factions.joinToString(",")
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
                ),
            ),
            mappings = Mappings.EMPTY,
        )
        val namespace = "script-import-declarations"
        val source = DirectoryScriptSource(
            namespace = namespace,
            directory = scriptsDirectory,
            classLoader = ScriptingCompilerExecutionTest::class.java.classLoader,
            fingerprint = "test",
        )

        try {
            ScriptRegistry.register(source)
            ScriptingEnvironment.INSTANCE = environment
            val output = mutableListOf<String>()

            ScriptLoader.execute<ImportingScript>(ScriptId(namespace, "main.importing.kts")) {
                constructorArgs(output as Any)
            }.getOrThrow()

            assertEquals(listOf("science"), output)
        } finally {
            ScriptRegistry.unregister(namespace)
            ScriptingEnvironment.clear()
            environment.close()
            scriptsDirectory.deleteRecursively()
            File("hollowengine").deleteRecursively()
        }
    }

    @Test
    fun `a script reached both directly and through a neighbour is declared once`() {
        val scriptsDirectory = File("build/tmp/script-import-diamond").apply {
            deleteRecursively()
            mkdirs()
        }
        scriptsDirectory.resolve("core.kts").writeText(
            """
                @file:SharedScript
                class Faction(val title: String)
                val factions = mutableListOf<String>()
                fun register(faction: Faction) { factions += faction.title }
            """.trimIndent(),
        )
        // core.kts достаётся сюда напрямую, а до main - ещё раз через этот скрипт.
        scriptsDirectory.resolve("content.kts").writeText(
            """
                @file:SharedScript
                @file:Import("core.kts")
                register(Faction("blog"))
            """.trimIndent(),
        )
        scriptsDirectory.resolve("main.importing.kts").writeText(
            """
                @file:Import("core.kts", "content.kts")
                register(Faction("science"))
                output += factions.joinToString(",")
            """.trimIndent(),
        )

        val defaultImports = listOf(
            Import::class.qualifiedName!!,
            SharedScript::class.qualifiedName!!,
        )
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
                ),
            ),
            mappings = Mappings.EMPTY,
        )
        val namespace = "script-import-diamond"
        val source = DirectoryScriptSource(
            namespace = namespace,
            directory = scriptsDirectory,
            classLoader = ScriptingCompilerExecutionTest::class.java.classLoader,
            fingerprint = "test",
        )

        try {
            ScriptRegistry.register(source)
            ScriptingEnvironment.INSTANCE = environment
            val output = mutableListOf<String>()

            ScriptLoader.execute<ImportingScript>(ScriptId(namespace, "main.importing.kts")) {
                constructorArgs(output as Any)
            }.getOrThrow()

            // Один список на оба пути: core.kts выполнился ровно один раз.
            assertEquals(listOf("blog,science"), output)
        } finally {
            ScriptRegistry.unregister(namespace)
            ScriptingEnvironment.clear()
            environment.close()
            scriptsDirectory.deleteRecursively()
            File("hollowengine").deleteRecursively()
        }
    }

    @Test
    fun `an imported script evaluation error reaches the caller`() {
        val scriptsDirectory = File("build/tmp/script-import-error").apply {
            deleteRecursively()
            mkdirs()
        }
        scriptsDirectory.resolve("failing.kts").writeText(
            """
                @file:SharedScript
                error("import failed")
            """.trimIndent(),
        )
        scriptsDirectory.resolve("main.importing.kts").writeText(
            """
                @file:Import("failing.kts")
                output += "unreachable"
            """.trimIndent(),
        )

        val defaultImports = listOf(
            Import::class.qualifiedName!!,
            SharedScript::class.qualifiedName!!,
        )
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
                ),
            ),
            mappings = Mappings.EMPTY,
        )
        val namespace = "script-import-error"
        val source = DirectoryScriptSource(
            namespace = namespace,
            directory = scriptsDirectory,
            classLoader = ScriptingCompilerExecutionTest::class.java.classLoader,
            fingerprint = "test",
        )

        try {
            ScriptRegistry.register(source)
            ScriptingEnvironment.INSTANCE = environment
            val output = mutableListOf<String>()

            val failure = ScriptLoader.execute<ImportingScript>(ScriptId(namespace, "main.importing.kts")) {
                constructorArgs(output as Any)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException, "Expected the imported script failure, got $failure")
            assertEquals("import failed", failure.message)
            assertTrue(output.isEmpty())
        } finally {
            ScriptRegistry.unregister(namespace)
            ScriptingEnvironment.clear()
            environment.close()
            scriptsDirectory.deleteRecursively()
            File("hollowengine").deleteRecursively()
        }
    }

    @Test
    fun `only shared imported scripts reuse their instance`() {
        assertImportedValues(shared = false, expected = "1:1")
        assertImportedValues(shared = true, expected = "1:2")
    }

    private fun assertImportedValues(shared: Boolean, expected: String) {
        val scriptsDirectory = File("build/tmp/shared-script-${if (shared) "enabled" else "disabled"}").apply {
            deleteRecursively()
            mkdirs()
        }
        scriptsDirectory.resolve("state.kts").writeText(
            """
                ${if (shared) "@file:SharedScript" else ""}
                var invocationCount = 0
                fun nextInvocation() = ++invocationCount
            """.trimIndent(),
        )
        scriptsDirectory.resolve("first.kts").writeText(
            """
                @file:Import("state.kts")
                val firstValue = nextInvocation()
            """.trimIndent(),
        )
        scriptsDirectory.resolve("second.kts").writeText(
            """
                @file:Import("state.kts")
                val secondValue = nextInvocation()
            """.trimIndent(),
        )
        scriptsDirectory.resolve("main.importing.kts").writeText(
            """
                @file:Import("first.kts", "second.kts")
                output += "${'$'}firstValue:${'$'}secondValue"
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
                ),
            ),
            mappings = Mappings.EMPTY,
        )
        val namespace = "shared-script-${if (shared) "enabled" else "disabled"}"
        val source = DirectoryScriptSource(
            namespace = namespace,
            directory = scriptsDirectory,
            classLoader = ScriptingCompilerExecutionTest::class.java.classLoader,
            fingerprint = "test",
        )

        try {
            ScriptRegistry.register(source)
            ScriptingEnvironment.INSTANCE = environment
            val output = mutableListOf<String>()

            val id = ScriptId(namespace, "main.importing.kts")
            ScriptLoader.execute<ImportingScript>(id) {
                constructorArgs(output as Any)
            }.getOrThrow()

            assertEquals(listOf(expected), output)

            if (shared) {
                assertTrue(ScriptCache.artifact(id).isFile)
                ScriptingEnvironment.clear()
                output.clear()

                ScriptLoader.execute<ImportingScript>(id) {
                    constructorArgs(output as Any)
                }.getOrThrow()

                assertEquals(listOf(expected), output)
            }
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
