package ru.hollowhorizon.hollowengine.common.compiler.tools

import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.DefaultScriptDefinitions
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptCache
import ru.hollowhorizon.hollowengine.common.scripting.cache.ScriptFingerprint
import ru.hollowhorizon.hollowengine.common.scripting.compiling.ScriptCompilationContext
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.MappingsLoader
import ru.hollowhorizon.hollowengine.common.scripting.source.DirectoryScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import java.io.File
import kotlin.system.exitProcess

/**
 * Compiles a namespace's scripts during the Gradle build, producing exactly the artifacts the game
 * would have produced at runtime.
 *
 * Invoked as a JavaExec task; every dependency the scripts compile against is simply the classpath of
 * that process.
 */
object ScriptPrecompiler {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = Options.parse(args)
        val failures = compile(options)
        if (failures.isNotEmpty()) {
            failures.forEach { (path, error) -> System.err.println("Failed to compile $path: ${error.message}") }
            exitProcess(1)
        }
    }

    private fun compile(options: Options): Map<String, Throwable> {
        // The remapping step keys off the same flag the game uses; the named variant needs no remapping
        // because Mojang names and NeoForge's official names are the same.
        isProduction = options.remap
        ScriptFingerprint.runtimeIdentity = options.identity

        val mappings = options.mappings?.let { file ->
            file.inputStream().use(MappingsLoader::loadMappings)
        } ?: Mappings.EMPTY

        val environment = ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = currentClasspath(),
            scriptTypes = DefaultScriptDefinitions.providers(),
            mappings = mappings,
        )

        val source = DirectoryScriptSource(
            namespace = options.namespace,
            directory = options.scripts,
            classLoader = ScriptPrecompiler::class.java.classLoader,
            dependencies = options.dependencies,
            fingerprint = options.fingerprint,
        )

        return try {
            ScriptingEnvironment.INSTANCE = environment
            ScriptRegistry.register(source)

            val failures = LinkedHashMap<String, Throwable>()
            val outputs = source.list().associateWith { id -> options.output.resolve(id.path + ".jar") }
            outputs.forEach { (id, output) ->
                val artifacts = ScriptRegistry.artifacts(id) ?: return@forEach
                val sourceFile = artifacts.sourceFile ?: return@forEach
                val hash = ScriptFingerprint.compute(id) ?: run {
                    failures[id.path] = IllegalStateException("Cannot fingerprint the script")
                    return@forEach
                }
                if (ScriptCache.isValid(output, hash)) return@forEach
                environment.compiler.compile(
                    sourceFile,
                    ScriptCompilationContext(cacheOutput = output, cacheHash = hash),
                ).onFailure { failures[id.path] = it }
                if (id.path !in failures && !output.isFile) {
                    failures[id.path] = IllegalStateException("The compiler produced no artifact")
                }
            }
            pruneScriptArtifacts(options.output, outputs.values)
            failures
        } finally {
            ScriptRegistry.unregister(options.namespace)
            ScriptingEnvironment.clear()
            ScriptFingerprint.runtimeIdentity = null
        }
    }

    private data class Options(
        val scripts: File,
        val output: File,
        val namespace: String,
        val fingerprint: String,
        val identity: String,
        val remap: Boolean,
        val mappings: File?,
        val dependencies: List<String>,
    ) {
        companion object {
            fun parse(args: Array<String>): Options {
                val values = ScriptToolArguments(args)

                return Options(
                    scripts = File(values.required("scripts")),
                    output = File(values.required("output")),
                    namespace = values.required("namespace"),
                    fingerprint = values.required("fingerprint"),
                    identity = values.required("identity"),
                    remap = values.optional("remap")?.toBooleanStrict() ?: false,
                    mappings = values.optional("mappings")?.let(::File)?.takeIf(File::isFile),
                    dependencies = values.list("dependsOn"),
                )
            }
        }
    }
}
