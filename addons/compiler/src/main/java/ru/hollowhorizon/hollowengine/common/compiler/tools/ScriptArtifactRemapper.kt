package ru.hollowhorizon.hollowengine.common.compiler.tools

import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.ClassRemappingSession
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.MappingsLoader
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.RemappingClasspath
import ru.hollowhorizon.hollowengine.common.scripting.source.DirectoryScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.isCompilableScriptFile
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.system.exitProcess

/** Creates mapping-specific script artifacts from already compiled named artifacts. */
object ScriptArtifactRemapper {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options.parse(args)
        val failures = remap(options)
        if (failures.isNotEmpty()) {
            failures.forEach { (path, error) -> System.err.println("Failed to remap $path: ${error.message}") }
            exitProcess(1)
        }
    }

    private fun remap(options: Options): Map<String, Throwable> {
        ScriptFingerprint.runtimeIdentity = options.identity
        val mappings = lazy { options.mappings.inputStream().use(MappingsLoader::loadMappings) }
        val classpath = lazy { RemappingClasspath(currentClasspath()) }
        val source = DirectoryScriptSource(
            namespace = options.namespace,
            directory = options.scripts,
            classLoader = ScriptArtifactRemapper::class.java.classLoader,
            dependencies = options.dependencies,
            fingerprint = options.fingerprint,
        )

        return try {
            ScriptRegistry.register(source)
            val failures = LinkedHashMap<String, Throwable>()
            val outputs = source.list().filter { isCompilableScriptFile(it.path) }
                .associateWith { id -> ScriptCache.artifactIn(options.output, id) }
            outputs.forEach { (id, output) ->
                val fingerprint = ScriptFingerprint.compute(id) ?: run {
                    failures[id.path] = IllegalStateException("Cannot fingerprint the script")
                    return@forEach
                }
                if (ScriptCache.isComplete(output, fingerprint, options.output)) return@forEach

                val input = ScriptCache.artifactIn(options.input, id)
                if (!input.isFile) {
                    failures[id.path] = IllegalStateException("Missing named artifact '$input'")
                    return@forEach
                }
                remapArtifact(input, output, fingerprint, mappings.value, classpath.value)
                    .onFailure { failures[id.path] = it }

                val sharedInput = ScriptCache.sharedArtifactIn(options.input, id)
                if (sharedInput.isFile) {
                    val sharedOutput = ScriptCache.sharedArtifactIn(options.output, id)
                    remapArtifact(sharedInput, sharedOutput, fingerprint, mappings.value, classpath.value)
                        .onFailure { failures[id.path] = it }
                }
            }
            pruneScriptArtifacts(options.output, expectedArtifacts(options.output, outputs.keys))
            failures
        } finally {
            if (classpath.isInitialized()) classpath.value.close()
            ScriptRegistry.unregister(options.namespace)
            ScriptFingerprint.runtimeIdentity = null
        }
    }

    private fun remapArtifact(
        input: File,
        output: File,
        fingerprint: ScriptFingerprint.Fingerprint,
        mappings: Mappings,
        classpath: RemappingClasspath,
    ): Result<Unit> = runCatching {
        val (manifest, entries) = JarFile(input).use { jar ->
            val manifest = Manifest(jar.manifest ?: Manifest())
            val entries = jar.entries().asSequence()
                .filter { !it.isDirectory && !it.name.equals(JarFile.MANIFEST_NAME, ignoreCase = true) }
                .associateTo(LinkedHashMap()) { entry ->
                    entry.name to jar.getInputStream(entry).use { it.readBytes() }
                }
            manifest to entries
        }
        manifest.mainAttributes.putValue("Manifest-Version", "1.0")
        manifest.mainAttributes.putValue(ScriptCache.HASH_ATTRIBUTE, fingerprint.code)
        manifest.mainAttributes.putValue(ScriptCache.LAYOUT_ATTRIBUTE, fingerprint.layout)

        val session = ClassRemappingSession(
            mappings,
            classpath,
            loader = { name -> entries["$name.class"] },
        )
        output.parentFile.mkdirs()
        val temporary = File(output.parentFile, output.name + ".tmp")
        try {
            JarOutputStream(temporary.outputStream().buffered(), manifest).use { jar ->
                entries.forEach { (name, bytes) ->
                    jar.putNextEntry(JarEntry(name))
                    jar.write(if (name.endsWith(".class")) session.remap(bytes) else bytes)
                    jar.closeEntry()
                }
            }
            Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private data class Options(
        val scripts: File,
        val input: File,
        val output: File,
        val namespace: String,
        val fingerprint: String,
        val identity: String,
        val mappings: File,
        val dependencies: List<String>,
    ) {
        companion object {
            fun parse(args: Array<String>): Options {
                val values = ScriptToolArguments(args)
                return Options(
                    scripts = File(values.required("scripts")),
                    input = File(values.required("input")),
                    output = File(values.required("output")),
                    namespace = values.required("namespace"),
                    fingerprint = values.required("fingerprint"),
                    identity = values.required("identity"),
                    mappings = File(values.required("mappings")),
                    dependencies = values.list("dependsOn"),
                )
            }
        }
    }
}
