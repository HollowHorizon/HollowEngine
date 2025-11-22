package ru.hollowhorizon.hollowengine.common.compiler

import kotlinx.coroutines.yield
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus
import ru.hollowhorizon.hollowengine.client.gui.overlay.UpdateStatusPacket
import ru.hollowhorizon.hollowengine.common.compiler.host.HollowEngineScriptingHost
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent
import ru.hollowhorizon.hollowengine.logE
import ru.hollowhorizon.hollowengine.logW
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.inputStream
import kotlin.io.readText
import kotlin.io.resolve
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvm.impl.*
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.use

object ScriptingCompiler {

    suspend inline fun <reified T : Any> compileText(
        text: String,
        name: String = "script",
        logErrors: Boolean = true,
    ): CompiledScript {
        val hostConfiguration = HollowEngineScriptingHost()
        val compilationConfiguration = createCompilationConfiguration<T>(hostConfiguration)

        val compiler = JvmScriptCompiler(hostConfiguration)
        val result = compiler(StringScriptSource(text, name), compilationConfiguration)

        if (logErrors) logErrors(result)
        yield()

        return processResult(result, name)
    }

    suspend inline fun <reified T : Any> compileFile(script: File, logErrors: Boolean = true): CompiledScript {
        val hostConfiguration = HollowEngineScriptingHost()
        val compilationConfiguration = createCompilationConfiguration<T>(hostConfiguration)

        val compiledJar = script.resolveCompiledJar()
        val hashcode = script.readText().hashCode().toString()

        if (compiledJar.exists() && compiledJar.loadScriptHashCode() == hashcode) return loadCompiledScript(
            script,
            compiledJar,
            hashcode
        )

        UpdateStatusPacket(script.name, CompilationStatus.Status.PARSE).sendToOperators()

        val compiler = JvmScriptCompiler(hostConfiguration)
        val result = compiler(FileScriptSource(script), compilationConfiguration)

        if (logErrors) logErrors(result)

        UpdateStatusPacket(script.name, null).sendToOperators()

        yield()

        return processResult(result, script.name, script, compiledJar)
    }

    fun processResult(
        result: ResultWithDiagnostics<*>,
        scriptName: String,
        scriptFile: File? = null,
        compiledJar: File? = null,
    ): CompiledScript {
        val hash = scriptFile?.readText()?.hashCode()?.toString() ?: "000000"
        val compiledScript = result.valueOrNull() as? KJvmCompiledScript
        return CompiledScript(
            scriptName,
            hash,
            compiledScript,
            scriptFile ?: File(scriptName)
        ).apply {
            compiledJar?.takeIf { !result.isError() }?.let {
                save(it)
            }
        }
    }

    inline fun <reified T : Any> createCompilationConfiguration(hostConfiguration: HollowEngineScriptingHost) =
        createCompilationConfigurationFromTemplate(
            KotlinType(T::class),
            hostConfiguration,
            HollowCore::class
        ) {}

    fun logErrors(result: ResultWithDiagnostics<*>) {
        result.errors()
            .filter { it.severity == ScriptError.Severity.ERROR || it.severity == ScriptError.Severity.FATAL }
            .forEach { error ->
                logW(error.toString())
            }

        result.errors().mapNotNull { it.exception }.distinct().forEach {
            logE(it.stackTraceToString())
        }
    }

    private fun ResultWithDiagnostics<*>.errors() = reports.map {
        ScriptError(
            ScriptError.Severity.entries[it.severity.ordinal],
            it.message,
            it.sourcePath ?: "",
            it.location?.start?.line ?: 0,
            it.location?.start?.col ?: 0,
            it.exception
        )
    }

    fun File.resolveCompiledJar() = absoluteFile.parentFile.resolve("$name.jar")
    fun loadCompiledScript(script: File, jar: File, hashcode: String) =
        CompiledScript(script.name, hashcode, jar.loadScriptFromJar(), script)

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

    fun File.loadScriptHashCode() = inputStream().use { istream ->
        JarInputStream(istream).use {
            it.manifest.mainAttributes.getValue("Script-Hashcode")
                ?: throw IllegalArgumentException("No Script-Hashcode manifest attribute")
        }
    }
}

var isServerStarted = false

@SubscribeEvent
fun onServerStart(event: ServerEvent.Starting) {
    isServerStarted = true
}