package ru.hollowhorizon.hollowengine.common.scripting.core.parser

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.createIsolatedCompilationContext
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import ru.hollowhorizon.hollowengine.common.scripting.ScriptTypes
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler.createCompilationConfiguration
import ru.hollowhorizon.hollowengine.common.scripting.core.host.HollowEngineScriptingHost
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate

object ScriptParser {
    val env: KotlinCoreEnvironment
    val host = HollowEngineScriptingHost()
    val messageCollector = ScriptDiagnosticsMessageCollector(null)

    init {
        System.setProperty("idea.use.native.fs.for.win", "false")
        val disposable = Disposer.newDisposable()

        val context = createIsolatedCompilationContext(
            createCompilationConfiguration<StoryEvent>(host),
            host,
            messageCollector,
            disposable
        ) {
            ScriptTypes.SCRIPTS.values.forEach {
                val config = createCompilationConfigurationFromTemplate(
                    KotlinType(it.kotlin),
                    host,
                    ScriptParser::class
                ) {}
                add(
                    ScriptingConfigurationKeys.SCRIPT_DEFINITIONS,
                    ScriptDefinition.FromConfigurations(host, config, null)
                )
            }
        }


        env = context.environment
    }


    fun parse(code: String, fileName: String): KtFile {
        val virtualFile =
            LightVirtualFile(fileName, KotlinFileType.INSTANCE, code.replace("\r", "")) // Ну спасибо JetBrains...
        return PsiManager.getInstance(env.project).findFile(virtualFile) as KtFile
    }
}