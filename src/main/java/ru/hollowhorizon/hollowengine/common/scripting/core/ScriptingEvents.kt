package ru.hollowhorizon.hollowengine.common.scripting.core

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.SharedScriptCompilationContext
import ru.hollowhorizon.hc.common.events.Cancelable
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.overlay.CompilationStatus
import ru.hollowhorizon.hollowengine.client.gui.overlay.UpdateStatusPacket
import java.io.File
import kotlin.script.experimental.api.SourceCode

open class ScriptEvent(val file: File?) : Event

@OptIn(ExperimentalCompilerApi::class)
class ScriptingCompilerPluginEvent(private val registrar: (CompilerPluginRegistrar) -> Unit) : Event {
    fun addExtension(extension: CompilerPluginRegistrar) {
        registrar(extension)
    }
}

class ScriptErrorEvent(file: File?, val type: ErrorType, val error: List<ScriptError>) : ScriptEvent(file), Cancelable {
    override var isCanceled = false
}

class ScriptCompiledEvent(file: File) : ScriptEvent(file)
class ScriptStartedEvent(file: File?) : ScriptEvent(file)

class AfterCodeAnalysisEvent(
    val context: SharedScriptCompilationContext,
    val script: SourceCode,
    val sources: List<KtFile>
): Event

@SubscribeEvent
fun onCodeParsed(event: AfterCodeAnalysisEvent) {
    UpdateStatusPacket(event.sources.first().name, CompilationStatus.Status.COMPILATION).sendToOperators()
}