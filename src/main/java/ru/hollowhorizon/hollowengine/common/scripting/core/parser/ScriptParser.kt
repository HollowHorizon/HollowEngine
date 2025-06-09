package ru.hollowhorizon.hollowengine.common.scripting.core.parser

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.createIsolatedCompilationContext
import ru.hollowhorizon.hollowengine.common.scripting.ScriptTypes
import ru.hollowhorizon.hollowengine.common.scripting.core.host.HollowEngineScriptingHost
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate

object ScriptParser {
    lateinit var env: KotlinCoreEnvironment
    val disposable = Disposer.newDisposable()
    val host = HollowEngineScriptingHost()
    val messageCollector = ScriptDiagnosticsMessageCollector(null)

    init {
        System.setProperty("idea.use.native.fs.for.win", "false")
    }


    fun parse(code: String, fileName: String): KtFile {
        val type = ScriptTypes.SCRIPTS.keys.find { fileName.substringAfter('.') == it.fileExtension }
            ?.let { ScriptTypes.SCRIPTS[it] } ?: error("Script type not found: $fileName")
        val context = createIsolatedCompilationContext(
            createCompilationConfigurationFromTemplate(KotlinType(type.kotlin), host, ScriptParser::class),
            host,
            messageCollector,
            disposable
        )
        env = context.environment

        val virtualFile =
            LightVirtualFile(fileName, KotlinFileType.INSTANCE, code.replace("\r", "")) // Ну спасибо JetBrains...
        return PsiManager.getInstance(env.project).findFile(virtualFile) as KtFile
    }
}