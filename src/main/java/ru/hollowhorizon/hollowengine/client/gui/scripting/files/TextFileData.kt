package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.*
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.ScriptTextArea
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.ScriptTextAreaModifier
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.ScriptTextEditorHandler
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionProvider
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.OnColorizedEvent
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.OnCompletionsEvent
import ru.hollowhorizon.hollowengine.common.scripting.core.parser.ScriptParser
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.script.experimental.api.isError

var currentLine = 0
var currentColumn = 0

class TextFileData(project: IDEGuiV2, name: String, path: String, code: String) :
    FileData(project, name, path) {
    private val lines = mutableStateListOf(*code.lines().map {
        TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 30f), Color.WHITE)))
    }.toTypedArray())
    private val editor = ScriptTextEditorHandler(lines)

    private var textHash = code.hashCode()
    lateinit var modifier: ScriptTextAreaModifier

    init {
        EventBus.register(::onColorizedEvent)
        EventBus.register(::onCompletionsEvent)
        ActionManager.launch { compileText(code) }
    }

    fun setText(text: String) {
        lines.clear()
        lines.addAll(mutableStateListOf(*text.lines().map {
            TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 30f), Color.WHITE)))
        }.toTypedArray()))
        surface.triggerUpdate()
    }

    fun onColorizedEvent(event: OnColorizedEvent) = launchOnMainThread {
        if (event.fileName != fileName || event.hashCode != textHash) return@launchOnMainThread

        lines.clear()
        lines.addAll(event.text)
        surface.triggerUpdate()
    }

    fun onCompletionsEvent(event: OnCompletionsEvent) = launchOnMainThread {
        if (event.fileName != fileName || event.hashCode != textHash) return@launchOnMainThread

        modifier.completions.clear()
        modifier.completions.addAll(event.completions)
        surface.triggerUpdate()

    }

    fun onErrorsEvent(errors: List<ScriptError>) {
        modifier.errors.clear()
        modifier.errors.addAll(errors)
        surface.triggerUpdate()

    }

    override fun save() {
        if (filePath.startsWith("%")) return
        SaveFilePacket(filePath, lines.joinToString("\n") { it.text }.toByteArray()).send()
    }

    override fun UiScope.compose() {
        modifier.backgroundColor(colors.backgroundMid)

        ScriptTextArea(
            ListTextLineProvider(lines),
            width = Grow.Std,
            height = Grow.Std
        ) {
            this@TextFileData.modifier = modifier
            installSelectionHandler { startLine, caretLine, startChar, caretChar ->
                modifier.completions.clear()
            }

            modifier.padding(sizes.smallGap).editorHandler(editor)
            modifier.onCharTyped = {
                currentLine = modifier.selectionStartLine
                currentColumn = modifier.selectionStartChar
                val text = lines.joinToString("\n") { it.text }
                textHash = text.hashCode()
                ActionManager.launch {
                    compileText(text)
                    save()
                }
            }
        }
    }

    private fun compileText(text: String) {
        val script = ScriptParser.parse(text, fileName)

        val files = mutableListOf(script)
        val completionProvider = CompletionProvider(files, fileName, currentLine, currentColumn)

        val (result, completions) = completionProvider.getResult(ScriptParser.env)
        onCompletionsEvent(OnCompletionsEvent(fileName, completions, text.hashCode()))

        val reporter = AnalyzerWithCompilerReport(ScriptParser.messageCollector, ScriptParser.env.configuration.languageVersionSettings, false)
        reporter.analyzeAndReport(files) { result }
        ScriptParser.messageCollector.diagnostics
            .filter { it.isError() }
            .map {
                ScriptError(
                    ScriptError.Severity.entries[it.severity.ordinal],
                    it.message,
                    it.sourcePath ?: "",
                    it.location?.start?.line ?: 0,
                    it.location?.start?.col ?: 0,
                    it.exception
                )
            }.apply { onErrorsEvent(this) }
        ScriptParser.messageCollector.clear()
    }

    override fun close() {
        EventBus.unregister(::onColorizedEvent)
        EventBus.unregister(::onCompletionsEvent)
    }
}

object ActionManager {
    private val executor = Executors.newSingleThreadExecutor()
    private var currentJob: Job? = null
    private var futureTask: Future<*>? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun launch(action: suspend () -> Unit) {
        currentJob?.cancel()
        futureTask?.cancel(true)
        currentJob = scope.launch debounce@{
            delay(300)
            if (!isActive) return@debounce

            try {
                action()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun <T> future(action: () -> T): Future<*>? = executor.submit(Callable { action() })
        .also { futureTask = it }
}

private fun TextAreaScope.installSelectionHandler(onChange: (startLine: Int, caretLine: Int, startChar: Int, caretChar: Int) -> Unit) {
    val selStartLine = remember(-1)
    val selCaretLine = remember(-1)
    val selStartChar = remember(0)
    val selCaretChar = remember(0)

    modifier.onSelectionChanged = { startLine, caretLine, startChar, caretChar ->
        selStartLine.set(startLine)
        selCaretLine.set(caretLine)
        selStartChar.set(startChar)
        selCaretChar.set(caretChar)
        onChange(startLine, caretLine, startChar, caretChar)
    }
    modifier.selectionStartLine = selStartLine.use()
    modifier.selectionCaretLine = selCaretLine.use()
    modifier.selectionStartChar = selStartChar.use()
    modifier.selectionCaretChar = selCaretChar.use()
}