package ru.hollowhorizon.hollowengine.common.scripting.core.configuration

import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LightVirtualFileBase
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import ru.hollowhorizon.hollowengine.common.scripting.core.Import
import java.io.File
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.FileBasedScriptSource
import kotlin.script.experimental.host.FileScriptSource

class HollowScriptConfigurator : RefineScriptCompilationConfigurationHandler {
    override operator fun invoke(context: ScriptConfigurationRefinementContext) = processAnnotations(context)

    private fun processAnnotations(context: ScriptConfigurationRefinementContext): ResultWithDiagnostics<ScriptCompilationConfiguration> {
        val annotations = context.collectedData?.get(ScriptCollectedData.foundAnnotations)?.takeIf { it.isNotEmpty() }
            ?: return context.compilationConfiguration.asSuccess()

        val scriptBaseDir = context.script.let {
            when (it) {
                // Виртуальный путь начинается с `/`, а мне не нужно, чтобы он искал скрипт в корневой директории
                is VirtualFileScriptSource if (it.virtualFile as? LightVirtualFile)?.originalFile != null -> File((it.virtualFile as LightVirtualFileBase).originalFile.path.substring(1)).parentFile
                is FileBasedScriptSource -> it.file.parentFile
                else -> null
            }
        }

        val importedSources = annotations.flatMap {
            (it as? Import)?.files?.mapNotNull { sourceName ->
                (scriptBaseDir?.resolve(sourceName) ?: File(sourceName)).takeIf(File::exists)
                    ?.let(::FileScriptSource)
            } ?: emptyList()
        }

        if(importedSources.isEmpty()) return context.compilationConfiguration.asSuccess()

        return ScriptCompilationConfiguration(context.compilationConfiguration) {
            importScripts.append(importedSources)
        }.asSuccess()
    }
}