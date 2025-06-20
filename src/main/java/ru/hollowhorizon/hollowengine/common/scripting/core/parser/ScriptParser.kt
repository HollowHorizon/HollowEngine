package ru.hollowhorizon.hollowengine.common.scripting.core.parser

import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.createIsolatedCompilationContext
import ru.hollowhorizon.hollowengine.common.scripting.ScriptTypes
import ru.hollowhorizon.hollowengine.common.scripting.core.host.HollowEngineScriptingHost
import java.io.Closeable
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate

object ScriptParser {

    init {
        System.setProperty("idea.use.native.fs.for.win", "false")
    }

    private val host = HollowEngineScriptingHost()

    fun parse(code: String, fileName: String): ScriptingContext {
        val type = ScriptTypes.SCRIPTS.keys.find { fileName.substringAfter('.') == it.fileExtension }
            ?.let { ScriptTypes.SCRIPTS[it] } ?: error("Script type not found: $fileName")
        val messageCollector = ScriptDiagnosticsMessageCollector(null)
        val disposable = Disposer.newDisposable()
        val context = createIsolatedCompilationContext(
            createCompilationConfigurationFromTemplate(KotlinType(type.kotlin), host, ScriptParser::class),
            host,
            messageCollector,
            disposable
        )

        val virtualFile =
            LightVirtualFile(fileName, KotlinFileType.INSTANCE, code.replace("\r", "")) // Ну спасибо JetBrains...
        val file = PsiManager.getInstance(context.environment.project).findFile(virtualFile) as KtFile
        return ScriptingContext(context.environment, messageCollector, file, disposable)
    }
}

data class ScriptingContext(
    val environment: KotlinCoreEnvironment,
    val messageCollector: ScriptDiagnosticsMessageCollector,
    val file: KtFile,
    val disposable: Disposable,
) : Closeable {
    var isDisposed = false
        private set

    override fun close() {
        Disposer.dispose(disposable)
        isDisposed = true
    }
}