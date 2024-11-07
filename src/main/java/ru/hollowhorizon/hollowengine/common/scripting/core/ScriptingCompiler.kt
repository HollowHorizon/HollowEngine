package ru.hollowhorizon.hollowengine.common.scripting.core

import net.minecraft.ChatFormatting
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.LOGGER
import ru.hollowhorizon.hc.client.utils.colored
import ru.hollowhorizon.hc.client.utils.currentServer
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.plus
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.common.scripting.core.host.HollowEngineScriptingHost
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.util.jar.JarEntry
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.StringScriptSource
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.jvm.impl.*
import kotlin.script.experimental.jvm.util.isError
import kotlin.script.experimental.jvmhost.JvmScriptCompiler
import kotlin.script.experimental.util.PropertiesCollection

object ScriptingCompiler {

    suspend inline fun <reified T : Any> compileText(text: String): CompiledScript {
        val hostConfiguration = HollowEngineScriptingHost()
        val compilationConfiguration = createCompilationConfiguration<T>(hostConfiguration)

        val compiler = JvmScriptCompiler(hostConfiguration)
        val result = compiler(StringScriptSource(text), compilationConfiguration)

        logErrors(result)

        return processResult(result, "script.kts")
    }

    suspend inline fun <reified T : Any> compileFile(script: File): CompiledScript {
        val hostConfiguration = HollowEngineScriptingHost()
        val compilationConfiguration = createCompilationConfiguration<T>(hostConfiguration)

        val compiledJar = script.resolveCompiledJar()
        val hashcode = script.readText().hashCode().toString()

        if (compiledJar.exists() && compiledJar.loadScriptHashCode() == hashcode) return loadCompiledScript(
            script,
            compiledJar,
            hashcode
        )

        val compiler = JvmScriptCompiler(hostConfiguration)
        val result = compiler(FileScriptSource(script), compilationConfiguration)

        logErrors(result)

        return processResult(result, script.name, script, compiledJar)
    }

    suspend fun processResult(
        result: ResultWithDiagnostics<*>,
        scriptName: String,
        scriptFile: File? = null,
        compiledJar: File? = null,
    ): CompiledScript {
        val hash = scriptFile?.readText()?.hashCode()?.toString() ?: ""
        val compiledScript = result.valueOrNull() as? KJvmCompiledScript
        return CompiledScript(
            scriptName,
            hash,
            compiledScript?.obfuscate(scriptName, hash),
            scriptFile ?: File(scriptName)
        ).apply {
            if (result.isError()) {
                handleErrors(result, scriptFile)
            } else {
                compiledJar?.let {
                    save(it)
                }
                ScriptCompiledEvent(scriptFile ?: error("Script file not found")).post()
            }
        }
    }

    inline fun <reified T : Any> createCompilationConfiguration(hostConfiguration: HollowEngineScriptingHost) =
        createCompilationConfigurationFromTemplate(
            KotlinType(T::class),
            hostConfiguration,
            HollowCore::class
        ) {}

    private fun CompiledScript.handleErrors(result: ResultWithDiagnostics<*>, scriptFile: File?) {
        val errors = result.errors()
        val event = ScriptErrorEvent(scriptFile, ErrorType.COMPILATION_ERROR, errors)
        event.post()
        if (!event.isCanceled) {
            this.errors = errors.map(ScriptError::toString)
        }
    }

    fun logErrors(result: ResultWithDiagnostics<*>) {
        result.errors().forEach { error ->
            LOGGER.warn(error)
            try {
                currentServer.playerList.players
                    .filter { it.hasPermissions(PlayerPermissions.GAMEMASTER) }
                    .forEach {
                        it.sendSystemMessage("Script Error: ".literal.colored(ChatFormatting.DARK_RED) + error.toString().literal)
                    }
            } catch (e: Exception) {
                //TODO Make there better check...
                HollowCore.LOGGER.error("Server is not loaded yet")
            }
        }
        result.errors().mapNotNull { it.exception }.distinct().forEach {
            LOGGER.error(it.stackTraceToString())
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
                    putValue("Created-By", "HollowCore ScriptingEngine")
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