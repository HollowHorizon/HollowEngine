package ru.hollowhorizon.hollowengine.common.compiler.isolated

import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScript
import org.jetbrains.kotlin.scripting.compiler.plugin.dependencies.ScriptsCompilationDependencies
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl
import org.jetbrains.kotlin.scripting.compiler.plugin.irLowerings.ScriptResultFieldData
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.remapClass
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import java.util.*
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript

fun makeCompiledModule(generationState: GenerationState): KJvmCompiledModuleInMemoryImpl {
    val classes = generationState.factory.asList().associate { it.relativePath to it.asByteArray() }
    return KJvmCompiledModuleInMemoryImpl(
        generationState.factory.asList()
            .associateTo(sortedMapOf<String, ByteArray>()) { it.relativePath to remapScriptClass(it.relativePath, classes, it.asByteArray()) }
    )
}

fun remapScriptClass(path: String, classes: Map<String, ByteArray>, bytes: ByteArray): ByteArray {
    if (!path.endsWith(".class") || !isProduction) return bytes
    val mappings = ScriptingEnvironment.INSTANCE.mappings
    val classpath = ScriptingEnvironment.INSTANCE.classpath
    return remapClass(bytes, classes::get, classpath, mappings)
}

fun makeCompiledScript(
    generationState: GenerationState,
    script: SourceCode,
    ktFile: KtFile,
    sourceDependencies: List<ScriptsCompilationDependencies.SourceDependencies>,
    getScriptConfiguration: (KtFile) -> ScriptCompilationConfiguration,
    resultFields: Map<FqName, ScriptResultFieldData>,
): ResultWithDiagnostics<KJvmCompiledScript> {
    val scriptDependenciesStack = ArrayDeque<KtScript>()
    val ktScript = ktFile.declarations.firstIsInstanceOrNull<KtScript>()
        ?: throw IllegalStateException("Expecting script file: KtScript is not found in ${ktFile.name}")

    fun makeOtherScripts(script: KtScript): ResultWithDiagnostics<List<KJvmCompiledScript>> {

        // TODO: ensure that it is caught earlier (as well) since it would be more economical
        if (scriptDependenciesStack.contains(script)) return ResultWithDiagnostics.Failure(
            ScriptDiagnostic(
                ScriptDiagnostic.unspecifiedError,
                "Unable to handle recursive script dependencies",
                sourcePath = script.containingFile.virtualFile?.path
            )
        )
        scriptDependenciesStack.push(script)

        val containingKtFile = script.containingKtFile
        val otherScripts =
            sourceDependencies.find { it.scriptFile == containingKtFile }?.sourceDependencies?.valueOrThrow()
                ?.mapNotNullSuccess { sourceFile ->
                    sourceFile.declarations.firstIsInstanceOrNull<KtScript>()?.let { ktScript ->
                        makeOtherScripts(ktScript).onSuccess { otherScripts ->
                            KJvmCompiledScript(
                                sourceFile.virtualFilePath,
                                getScriptConfiguration(sourceFile),
                                ktScript.fqName.asString(),
                                null,
                                otherScripts,
                                null
                            ).asSuccess()
                        }
                    } ?: null.asSuccess()
                } ?: emptyList<KJvmCompiledScript>().asSuccess()

        scriptDependenciesStack.pop()
        return otherScripts
    }

    val module = makeCompiledModule(generationState)

    val scriptClassFqName = ktScript.fqName

    val resultField = resultFields[scriptClassFqName]?.let {
        it.fieldName.asString() to KotlinType(it.fieldTypeName)
    }

    return makeOtherScripts(ktScript).onSuccess { otherScripts ->
        KJvmCompiledScript(
            script.locationId,
            getScriptConfiguration(ktScript.containingKtFile),
            scriptClassFqName.asString(),
            resultField,
            otherScripts,
            module
        ).asSuccess()
    }
}