import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.ScriptLoader
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.annotations.Import
import ru.hollowhorizon.hollowengine.common.scripting.annotations.SharedScript
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.compiling.ScriptCompilationContext
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.source.AddonScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.DirectoryScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.script.experimental.api.constructorArgs
import kotlin.test.*

class AddonScriptArtifactsTest {
    @Test
    fun `an addon ships the classes of its shared scripts and links them without a compiler`() {
        val root = File("build/tmp/addon-shared-artifacts").apply {
            deleteRecursively()
            mkdirs()
        }
        val scripts = root.resolve("scripts").apply { mkdirs() }
        val artifacts = root.resolve("artifacts")
        scripts.resolve("registry.kts").writeText(
            """
                @file:SharedScript
                val entries = mutableListOf<String>()
                fun register(entry: String) { entries += entry }
                fun titles() = entries.joinToString(",")
            """.trimIndent(),
        )
        scripts.resolve("writer.cached.kts").writeText(
            """
                @file:Import("registry.kts")
                register("shipped")
                output += titles()
            """.trimIndent(),
        )
        val rootId = ScriptId(NAMESPACE, "writer.cached.kts")
        val sharedId = ScriptId(NAMESPACE, "registry.kts")

        try {
            build(scripts, artifacts, rootId)

            assertTrue(ScriptCache.artifactIn(artifacts, rootId).isFile, "the build produced no artifact")
            assertTrue(
                ScriptCache.sharedArtifactIn(artifacts, sharedId).isFile,
                "the build has to collect the shared script's classes next to the artifacts importing it",
            )

            val addon = root.resolve("addon.jar")
            packAddon(addon, scripts, artifacts)
            ScriptRegistry.register(
                AddonScriptSource(
                    namespace = NAMESPACE,
                    archive = addon,
                    classLoader = AddonScriptArtifactsTest::class.java.classLoader,
                    classpath = emptyList(),
                    dependencies = emptyList(),
                    fingerprint = VERSION,
                    storageKey = "test",
                ),
            )

            assertFalse(
                ScriptCache.sharedArtifact(sharedId).isFile,
                "the game's own cache must be empty for this test to mean anything",
            )

            val output = mutableListOf<String>()
            ScriptLoader.execute<CachedScript>(rootId) { constructorArgs(output as Any) }.getOrThrow()
            assertEquals(listOf("shipped"), output)
        } finally {
            ScriptRegistry.unregister(NAMESPACE)
            ScriptingEnvironment.clear()
            root.deleteRecursively()
            File("hollowengine").deleteRecursively()
        }
    }

    private fun build(scripts: File, artifacts: File, id: ScriptId) {
        val defaultImports = listOf(Import::class.qualifiedName!!, SharedScript::class.qualifiedName!!)
        val environment = ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = testClasspath(),
            scriptTypes = listOf(
                ScriptClassProvider("kts", "kotlin.Any", defaultImports),
                ScriptClassProvider(".cached.kts", CachedScript::class.qualifiedName!!, defaultImports),
            ),
            mappings = Mappings.EMPTY,
        )
        try {
            ScriptRegistry.register(
                DirectoryScriptSource(
                    namespace = NAMESPACE,
                    directory = scripts,
                    classLoader = AddonScriptArtifactsTest::class.java.classLoader,
                    fingerprint = VERSION,
                ),
            )
            ScriptingEnvironment.INSTANCE = environment

            val fingerprint = assertNotNull(ScriptFingerprint.compute(id))
            environment.compiler.compile(
                scripts.resolve(id.path),
                ScriptCompilationContext(
                    cacheOutput = ScriptCache.artifactIn(artifacts, id),
                    cacheFingerprint = fingerprint,
                    sharedCacheOutput = artifacts,
                ),
            ).getOrThrow()
        } finally {
            ScriptingEnvironment.clear()
            environment.close()
            ScriptRegistry.unregister(NAMESPACE)
        }
    }

    private fun packAddon(addon: File, scripts: File, artifacts: File) {
        JarOutputStream(addon.outputStream().buffered()).use { jar ->
            copyInto(jar, scripts, AddonScriptSource.SOURCE_PREFIX)
            copyInto(jar, artifacts, AddonScriptSource.COMPILED_PREFIX)
        }
    }

    private fun copyInto(jar: JarOutputStream, directory: File, prefix: String) {
        val base = directory.toPath()
        directory.walkTopDown().filter(File::isFile).forEach { file ->
            jar.putNextEntry(JarEntry(prefix + base.relativize(file.toPath()).toString().replace('\\', '/')))
            jar.write(file.readBytes())
            jar.closeEntry()
        }
    }

    private fun testClasspath(): List<File> =
        System.getProperty("java.class.path").split(File.pathSeparator).asSequence().filter(String::isNotBlank)
            .map(::File).filter(File::exists).distinctBy { it.absoluteFile.normalize() }.toList()

    private companion object {
        const val NAMESPACE = "addon-shared-artifacts"
        const val VERSION = "1.0.0"
    }
}
