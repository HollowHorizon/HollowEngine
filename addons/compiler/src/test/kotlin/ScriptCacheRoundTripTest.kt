import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptLoader
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.annotations.Import
import ru.hollowhorizon.hollowengine.common.scripting.annotations.SharedScript
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.source.DirectoryScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.util.jar.JarFile
import kotlin.script.experimental.api.constructorArgs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

abstract class CachedScript(val output: MutableList<String>)

/**
 * The point of the cache: a script compiled once keeps running on installations where the compiler
 * addon is absent, and stops being trusted the moment its sources change.
 */
class ScriptCacheRoundTripTest {
    @Test
    fun `a cached script runs again without a compiler and is dropped when its source changes`() {
        val scriptsDirectory = File("build/tmp/script-cache-round-trip").apply {
            deleteRecursively()
            mkdirs()
        }
        scriptsDirectory.resolve("greeting.cached.kts").writeText("""output += "first"""")

        val source = DirectoryScriptSource(
            namespace = NAMESPACE,
            directory = scriptsDirectory,
            classLoader = ScriptCacheRoundTripTest::class.java.classLoader,
            fingerprint = "test",
        )
        val id = ScriptId(NAMESPACE, "greeting.cached.kts")

        try {
            ScriptRegistry.register(source)

            withCompiler {
                assertEquals(listOf("first"), run(id))
            }

            val artifact = ScriptCache.artifact(id)
            assertTrue(artifact.isFile, "compiling a script should leave a cached artifact behind")
            assertEquals(ScriptFingerprint.compute(id)?.code, ScriptCache.hashOf(artifact))

            // No compiler: the artifact is the only thing that can answer, and it does.
            assertEquals(listOf("first"), run(id))

            // An edited source invalidates the artifact, and without a compiler the stale one is still
            // used rather than leaving the pack broken.
            scriptsDirectory.resolve("greeting.cached.kts").writeText("""output += "second"""")
            assertEquals(listOf("first"), run(id))

            // With the compiler back, the edit takes effect and the artifact is rebuilt.
            withCompiler {
                assertEquals(listOf("second"), run(id))
            }
            assertEquals(ScriptFingerprint.compute(id)?.code, ScriptCache.hashOf(ScriptCache.artifact(id)))
        } finally {
            ScriptRegistry.unregister(NAMESPACE)
            ScriptingEnvironment.clear()
            scriptsDirectory.deleteRecursively()
            File("hollowengine").deleteRecursively()
        }
    }

    @Test
    fun `a shipped pack links its shared scripts from a directory it was not compiled in`() {
        val root = File("build/tmp/shared-script-relocated").apply {
            deleteRecursively()
            mkdirs()
        }
        val authored = root.resolve("authored")
        val shipped = root.resolve("shipped")
        authored.mkdirs()
        authored.resolve("registry.kts").writeText(
            """
                @file:SharedScript
                val entries = mutableListOf<String>()
                fun register(entry: String) { entries += entry }
                fun titles() = entries.joinToString(",")
            """.trimIndent(),
        )
        authored.resolve("writer.cached.kts").writeText(
            """
                @file:Import("registry.kts")
                register("written")
                output += titles()
            """.trimIndent(),
        )
        val id = ScriptId(RELOCATED_NAMESPACE, "writer.cached.kts")

        try {
            withSource(authored) {
                withCompiler(sharedScriptTypes()) {
                    assertEquals(listOf("written"), run(id))
                }
            }

            assertTrue(authored.renameTo(shipped), "could not relocate the scripts")
            assertTrue(
                ScriptCache.sharedArtifact(ScriptId(RELOCATED_NAMESPACE, "registry.kts")).isFile,
                "the shared script should have an artifact of its own",
            )
            assertTrue(
                classNamesOf(ScriptCache.artifact(id)).none { it == "Registry.class" || it.startsWith("Registry$") },
                "the importer should not carry a copy of the shared script",
            )

            withSource(shipped) {
                assertEquals(listOf("written,written"), run(id))
            }
        } finally {
            ScriptRegistry.unregister(RELOCATED_NAMESPACE)
            ScriptingEnvironment.clear()
            root.deleteRecursively()
            File("hollowengine").deleteRecursively()
        }
    }

    private fun run(id: ScriptId): List<String> {
        val output = mutableListOf<String>()
        val script = ScriptLoader.execute<CachedScript>(id) {
            constructorArgs(output as Any)
        }
        assertNotNull(script.getOrNull(), "failed to run $id: ${script.exceptionOrNull()}")
        return output
    }

    private fun withSource(directory: File, block: () -> Unit) {
        val source = DirectoryScriptSource(
            namespace = RELOCATED_NAMESPACE,
            directory = directory,
            classLoader = ScriptCacheRoundTripTest::class.java.classLoader,
            fingerprint = "test",
        )
        ScriptRegistry.register(source)
        try {
            block()
        } finally {
            ScriptRegistry.unregister(RELOCATED_NAMESPACE)
        }
    }

    private fun classNamesOf(jar: File): Set<String> = JarFile(jar).use { archive ->
        archive.entries().asSequence().map { it.name }.filter { it.endsWith(".class") }.toSet()
    }

    private fun sharedScriptTypes(): List<ScriptClassProvider> {
        val defaultImports = listOf(Import::class.qualifiedName!!, SharedScript::class.qualifiedName!!)
        return listOf(
            ScriptClassProvider("kts", "kotlin.Any", defaultImports),
            ScriptClassProvider(".cached.kts", CachedScript::class.qualifiedName!!, defaultImports),
        )
    }

    private fun withCompiler(
        scriptTypes: List<ScriptClassProvider> = listOf(
            ScriptClassProvider(extension = ".cached.kts", baseClass = CachedScript::class.qualifiedName!!),
        ),
        block: () -> Unit,
    ) {
        val environment = ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = testClasspath(),
            scriptTypes = scriptTypes,
            mappings = Mappings.EMPTY,
        )
        try {
            ScriptingEnvironment.INSTANCE = environment
            block()
        } finally {
            ScriptingEnvironment.clear()
        }
    }

    private fun testClasspath(): List<File> = System.getProperty("java.class.path")
        .split(File.pathSeparator)
        .asSequence()
        .filter(String::isNotBlank)
        .map(::File)
        .filter(File::exists)
        .distinctBy { it.absoluteFile.normalize() }
        .toList()

    private companion object {
        const val NAMESPACE = "cache-round-trip"
        const val RELOCATED_NAMESPACE = "shared-script-relocated"
    }
}
