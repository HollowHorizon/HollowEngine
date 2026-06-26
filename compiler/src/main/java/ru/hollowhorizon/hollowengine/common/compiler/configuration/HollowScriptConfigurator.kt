package ru.hollowhorizon.hollowengine.common.compiler.configuration

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource

class HollowScriptConfigurator : RefineScriptCompilationConfigurationHandler {
    override operator fun invoke(context: ScriptConfigurationRefinementContext) = processAnnotations(context)

    private fun processAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.collectedAnnotations)?.map { it.annotation }
            ?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        val scriptBaseDir = (context.script as? FileBasedScriptSource)?.let {
            val localPath = it.file.path.replace(File.separatorChar, '/').removePrefix("/").removePrefix("hollowengine/")
            DirectoryManager.HOLLOW_ENGINE.resolve(localPath).parent.toFile()
        }

        val importedSources = annotations
            .flatMap {
                val files = runCatching {
                    it.javaClass.getDeclaredMethod("file").invoke(annotations[0]) as Array<String>
                }.getOrNull()
                files?.mapNotNull { sourceName ->
                    (scriptBaseDir?.resolve(sourceName) ?: File(sourceName)).takeIf(File::exists)
                        ?.let(::FileScriptSource)
                } ?: emptyList()
            }

        if (importedSources.isEmpty()) return context.compilationConfiguration.asSuccess()

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            importScripts.append(importedSources)
        }.asSuccess()
    }
}