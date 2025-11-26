package ru.hollowhorizon.hollowengine.common.compiler.caching

import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.jvm.impl.*
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.use

fun KJvmCompiledScript.saveScriptToJar(outputJar: File, hash: String) {
    val module = (getCompiledModule() as? KJvmCompiledModuleInMemory)
        ?: throw IllegalArgumentException("Unsupported module type ${getCompiledModule()}")

    return FileOutputStream(outputJar).use {
        val manifest = Manifest().apply {
            mainAttributes.apply {
                putValue("Manifest-Version", "1.0")
                putValue("Created-By", "HollowEngine ScriptingEngine")
                putValue("Script-Hashcode", hash)
                putValue("Main-Class", scriptClassFQName)
            }
        }

        JarOutputStream(it, manifest).use { jar ->
            // Write sanitized compiled script metadata
            jar.putNextEntry(JarEntry(scriptMetadataPath(scriptClassFQName)))
            jar.write(copyWithoutModule().apply(::shrinkSerializableScriptData).toBytes())
            jar.closeEntry()

            // Write each output file
            module.compilerOutputFiles.forEach { (path, bytes) ->
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

private fun shrinkSerializableScriptData(compiledScript: KJvmCompiledScript) {
    (compiledScript.compilationConfiguration.entries() as? MutableSet<Map.Entry<PropertiesCollection.Key<*>, Any?>>)
        ?.removeIf { it.key == ScriptCompilationConfiguration.dependencies || it.key == ScriptCompilationConfiguration.defaultImports }
}