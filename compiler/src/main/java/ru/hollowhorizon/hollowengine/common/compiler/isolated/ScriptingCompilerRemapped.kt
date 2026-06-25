package ru.hollowhorizon.hollowengine.common.compiler.isolated

import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptCompilerProxy
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptJvmK2CompilerIsolated
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.deobf.NeoForgeEnvironmentSetup
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.remapClass
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Serializable
import java.security.ProtectionDomain
import kotlin.script.experimental.api.CompiledScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.impl.KJvmCompiledModuleInMemory
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

class ScriptJvmCompilerRemapped(
    hostConfiguration: kotlin.script.experimental.host.ScriptingHostConfiguration,
) : ScriptCompilerProxy {
    private val delegate = ScriptJvmK2CompilerIsolated(hostConfiguration)

    override fun compile(
        script: SourceCode,
        scriptCompilationConfiguration: ScriptCompilationConfiguration,
    ): ResultWithDiagnostics<CompiledScript> {
        return when (val result = delegate.compile(script, scriptCompilationConfiguration)) {
            is ResultWithDiagnostics.Success -> {
                ResultWithDiagnostics.Success(remapCompiledScript(result.value), result.reports)
            }

            is ResultWithDiagnostics.Failure -> result
        }
    }

    private fun remapCompiledScript(script: CompiledScript): CompiledScript {
        val jvmScript = script as? KJvmCompiledScript ?: return script
        val module = jvmScript.getCompiledModule() as? KJvmCompiledModuleInMemory ?: return jvmScript
        val outputFiles = module.compilerOutputFiles
        val remappedModule = RemappedCompiledModule(
            outputFiles.mapValues { (path, bytes) ->
                remapScriptClass(path, outputFiles, bytes)
            }
        )
        return KJvmCompiledScript(
            jvmScript.sourceLocationId,
            jvmScript.compilationConfiguration,
            jvmScript.scriptClassFQName,
            jvmScript.resultField,
            jvmScript.otherScripts.map(::remapCompiledScript),
            remappedModule,
        )
    }
}

private fun remapScriptClass(path: String, classes: Map<String, ByteArray>, bytes: ByteArray): ByteArray {
    if (!path.endsWith(".class") || !isProduction || NeoForgeEnvironmentSetup.isAvailable()) return bytes
    val environment = ScriptingEnvironment.INSTANCE
    return remapClass(
        bytes,
        loader = { name -> classes["$name.class"] },
        classpath = environment.classpath,
        mappings = environment.mappings,
    )
}

private class RemappedCompiledModule(
    override val compilerOutputFiles: Map<String, ByteArray>,
) : KJvmCompiledModuleInMemory, Serializable {
    override fun createClassLoader(baseClassLoader: ClassLoader?): ClassLoader {
        return RemappedCompiledScriptClassLoader(compilerOutputFiles, HollowEngine::class.java.classLoader)
    }
}

private class RemappedCompiledScriptClassLoader(
    private val files: Map<String, ByteArray>,
    parent: ClassLoader?,
) : ClassLoader(parent) {
    override fun findClass(name: String): Class<*> {
        val path = name.replace('.', '/') + ".class"
        val bytes = files[path] ?: throw ClassNotFoundException(name)
        val protectionDomain = ProtectionDomain(null, null)
        return defineClass(name, bytes, 0, bytes.size, protectionDomain)
    }

    override fun getResourceAsStream(name: String): InputStream? {
        return files[name]?.let(::ByteArrayInputStream)
    }
}
