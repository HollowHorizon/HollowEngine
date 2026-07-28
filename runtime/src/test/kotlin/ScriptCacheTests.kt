import org.junit.jupiter.api.io.TempDir
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.source.DirectoryScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptCacheTests {
    @Test
    fun `a stamped artifact reports the fingerprint it was built for`(@TempDir directory: File) {
        val jar = directory.resolve("script.jar")
        writeStampedJar(jar, "abc123")

        assertEquals("abc123", ScriptCache.hashOf(jar))
        assertTrue(ScriptCache.isValid(jar, "abc123"))
        assertFalse(ScriptCache.isValid(jar, "other"))
        assertFalse(ScriptCache.isValid(jar, null))
    }

    @Test
    fun `an unstamped or missing artifact is never valid`(@TempDir directory: File) {
        val unstamped = directory.resolve("plain.jar")
        JarOutputStream(unstamped.outputStream(), Manifest()).use { }

        assertNull(ScriptCache.hashOf(unstamped))
        assertNull(ScriptCache.hashOf(directory.resolve("absent.jar")))
        assertFalse(ScriptCache.isValid(unstamped, "abc123"))
    }

    @Test
    fun `pruning removes orphans but leaves namespaces it was not told about`(@TempDir root: File) {
        val known = ScriptId("prune-known", "kept.node.kts")
        val orphan = ScriptId("prune-known", "gone.node.kts")
        val untouched = ScriptId("prune-other", "elsewhere.node.kts")
        listOf(known, orphan, untouched).forEach { id ->
            val file = ScriptCache.artifact(id)
            file.parentFile.mkdirs()
            writeStampedJar(file, "hash")
        }

        try {
            ScriptCache.prune(listOf(known))

            assertTrue(ScriptCache.artifact(known).isFile)
            assertFalse(ScriptCache.artifact(orphan).isFile)
            assertTrue(ScriptCache.artifact(untouched).isFile)
        } finally {
            DirectoryManager.SCRIPT_CACHE.resolve("prune-known").deleteRecursively()
            DirectoryManager.SCRIPT_CACHE.resolve("prune-other").deleteRecursively()
        }
    }

    @Test
    fun `the fingerprint follows the script and everything it imports`(@TempDir root: File) {
        root.resolve("main.node.kts").writeText("@file:Import(\"library.kts\")\n// body")
        root.resolve("library.kts").writeText("// library")

        val source = DirectoryScriptSource(
            namespace = "fingerprint-demo",
            directory = root,
            classLoader = ScriptCacheTests::class.java.classLoader,
        )
        ScriptRegistry.register(source)
        try {
            val id = ScriptId("fingerprint-demo", "main.node.kts")
            val original = ScriptFingerprint.compute(id)
            assertNotNull(original)
            assertEquals(original, ScriptFingerprint.compute(id))

            root.resolve("library.kts").writeText("// library, edited")
            assertNotEquals(original, ScriptFingerprint.compute(id))
        } finally {
            ScriptRegistry.unregister(source.namespace)
        }
    }

    @Test
    fun `the fingerprint names the runtime the artifact was built for`(@TempDir root: File) {
        root.resolve("main.node.kts").writeText("// body")

        val source = DirectoryScriptSource(
            namespace = "identity-demo",
            directory = root,
            classLoader = ScriptCacheTests::class.java.classLoader,
        )
        ScriptRegistry.register(source)
        try {
            val id = ScriptId("identity-demo", "main.node.kts")
            ScriptFingerprint.runtimeIdentity = "fabric/intermediary/production"
            val fabric = ScriptFingerprint.compute(id)
            ScriptFingerprint.runtimeIdentity = "neoforge/official/production"
            val neoforge = ScriptFingerprint.compute(id)

            assertNotNull(fabric)
            assertNotEquals(fabric, neoforge)
        } finally {
            ScriptFingerprint.runtimeIdentity = null
            ScriptRegistry.unregister(source.namespace)
        }
    }

    @Test
    fun `a script without sources cannot be fingerprinted`() {
        assertNull(ScriptFingerprint.compute(ScriptId("missing-namespace", "nothing.node.kts")))
    }

    private fun writeStampedJar(file: File, hash: String) {
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            mainAttributes.putValue(ScriptCache.HASH_ATTRIBUTE, hash)
        }
        file.parentFile?.mkdirs()
        JarOutputStream(file.outputStream(), manifest).use { }
    }
}
