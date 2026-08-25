package ru.hollowhorizon.hollowengine.common.compiler.caching

import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.compiling.isSharedScript
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.jvm.impl.*
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.use

/**
 * One shared script as it is written out: which classes belong to it and what to stamp its artifact with.
 */
private class SharedScript(
    val id: ScriptId,
    val fingerprint: String,
    val classPrefix: String,
    val classes: Map<String, ByteArray>,
)

fun KJvmCompiledScript.saveScriptToJar(outputJar: File, hash: String) {
    val module = (getCompiledModule() as? KJvmCompiledModuleInMemory)
        ?: throw IllegalArgumentException("Unsupported module type ${getCompiledModule()}")

    val shared = collectSharedScripts(module.compilerOutputFiles)
    shared.forEach(::writeSharedArtifact)
    val ownClasses = module.compilerOutputFiles.filterKeys { path -> shared.none { it.owns(path) } }

    FileOutputStream(outputJar).use {
        val manifest = Manifest().apply {
            mainAttributes.apply {
                putValue("Manifest-Version", "1.0")
                putValue("Created-By", "HollowEngine ScriptingEngine")
                putValue(ScriptCache.HASH_ATTRIBUTE, hash)
                putValue("Main-Class", scriptClassFQName)
                if (shared.isNotEmpty()) {
                    putValue(
                        ScriptCache.SHARED_ATTRIBUTE,
                        shared.joinToString(";") { "${it.id.qualified}=${it.fingerprint}" },
                    )
                }
            }
        }

        JarOutputStream(it, manifest).use { jar ->
            // Write sanitized compiled script metadata
            jar.putNextEntry(JarEntry(scriptMetadataPath(scriptClassFQName)))
            jar.write(copyWithoutModule().apply(::shrinkSerializableScriptData).toBytes())
            jar.closeEntry()

            ownClasses.forEach { (path, bytes) ->
                jar.putNextEntry(JarEntry(path))
                jar.write(bytes)
                jar.closeEntry()
            }

            jar.finish()
            jar.flush()
        }
        it.flush()
    }
}

/**
 * The shared scripts this compilation produced classes for.
 */
private fun KJvmCompiledScript.collectSharedScripts(output: Map<String, ByteArray>): List<SharedScript> {
    val found = LinkedHashMap<String, SharedScript>()

    fun visit(script: CompiledScript) {
        script.otherScripts.forEach(::visit)
        if (script.compilationConfiguration[ScriptCompilationConfiguration.isSharedScript] != true) return
        val jvmScript = script as? KJvmCompiledScript ?: return
        val name = jvmScript.scriptClassFQName
        if (name in found) return

        val source = jvmScript.sourceLocationId?.let(::File)?.takeIf(File::isFile) ?: return
        val id = ScriptRegistry.idOf(source) ?: return
        val fingerprint = runCatching { ScriptFingerprint.compute(id) }.getOrNull() ?: return

        val prefix = name.replace('.', '/')
        val classes = output.filterKeys { it == "$prefix.class" || it.startsWith("$prefix$") }
        if (classes.isEmpty()) return

        found[name] = SharedScript(id, fingerprint, prefix, classes)
    }

    otherScripts.forEach(::visit)
    return found.values.toList()
}

private fun SharedScript.owns(path: String): Boolean =
    path == "$classPrefix.class" || path.startsWith("$classPrefix$")

private fun writeSharedArtifact(script: SharedScript) {
    val output = ScriptCache.sharedArtifact(script.id)
    if (ScriptCache.isValid(output, script.fingerprint)) return

    output.parentFile?.mkdirs()
    val temporary = File(output.parentFile, output.name + ".tmp")
    FileOutputStream(temporary).use { stream ->
        val manifest = Manifest().apply {
            mainAttributes.apply {
                putValue("Manifest-Version", "1.0")
                putValue("Created-By", "HollowEngine ScriptingEngine")
                putValue(ScriptCache.HASH_ATTRIBUTE, script.fingerprint)
            }
        }

        JarOutputStream(stream, manifest).use { jar ->
            script.classes.forEach { (path, bytes) ->
                jar.putNextEntry(JarEntry(path))
                jar.write(bytes)
                jar.closeEntry()
            }
            jar.finish()
            jar.flush()
        }
        stream.flush()
    }
    Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
}

private fun shrinkSerializableScriptData(compiledScript: KJvmCompiledScript) {
    (compiledScript.compilationConfiguration.entries() as? MutableSet<Map.Entry<PropertiesCollection.Key<*>, Any?>>)
        ?.removeIf { it.key == ScriptCompilationConfiguration.dependencies || it.key == ScriptCompilationConfiguration.defaultImports }
}
