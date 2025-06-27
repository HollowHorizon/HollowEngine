package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.*
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.jvm.compiler.messageCollector
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.languageVersionSettings
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import ru.hollowhorizon.hc.common.coroutines.scopeAsync
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.ScriptTextEditorHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ListTextLineProvider
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionProvider
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.OnCompletionsEvent
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.ScriptColorizer
import ru.hollowhorizon.hollowengine.common.scripting.core.parser.ScriptParser
import ru.hollowhorizon.hollowengine.common.scripting.core.parser.ScriptingContext
import java.util.concurrent.Future
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.script.experimental.api.isError

var currentLine = 0
var currentColumn = 0

class TextFileData(project: IdeContent, name: String, path: String, code: String) :
    FileData(project, name, path) {
    private val lines = MutableStateList(code.lines().map {
        TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 18f), Color.WHITE)))
    }.toMutableList())
    private val editorHandler = ScriptTextEditorHandler(lines)
    private var textHash = code.hashCode()
    private var bindingContext = BindingContext.EMPTY

    var context: ScriptingContext? = null
    lateinit var modifier: ScriptTextAreaModifier

    init {
        EventBus.register(::onCompletionsEvent)
        scopeAsync { compileText(code) }
    }

    fun setText(text: String) {
        lines.clear()
        lines.addAll(mutableStateListOf(*text.lines().map {
            TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 18f), Color.WHITE)))
        }.toTypedArray()))
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
        modifier.backgroundColor(colors.backgroundVariant)

        ScriptTextArea(
            ListTextLineProvider(lines),
            vScrollbarModifier = { it.width(sizes.smallGap) },
            hScrollbarModifier = { it.height(sizes.smallGap) },
        ) {
            modifier.margin(vertical = sizes.smallGap)
            this@TextFileData.modifier = modifier
            installSelectionHandler(lines) { startLine, caretLine, startChar, caretChar ->
                modifier.completions.clear()

                currentLine = modifier.selectionStartLine
                currentColumn = modifier.selectionStartChar
                val text = lines.joinToString("\n") { it.text }
                val newHash = text.hashCode()
                if (textHash != newHash || context?.isDisposed == true) {
                    textHash = newHash
                    ActionManager.launch {
                        compileText(text)
                        save()
                    }
                } else {
                    context?.let { colorizeText(it) }
                }
            }

            modifier.editorHandler(editorHandler)
        }
    }

    private suspend fun compileText(text: String) {
        context?.close()
        val context = ScriptParser.parse(text, fileName).also { context = it }
        coroutineContext[ActionManager.ScriptContext.Key]?.context = context

        val files = mutableListOf(context.file)
        val completionProvider = CompletionProvider(files, fileName, currentLine, currentColumn)

        val (result, completions) = completionProvider.getResult(context.environment)
        bindingContext = result.bindingContext
        yield()

        // Если выделен текст, то подсказки не нужны
        if (modifier.selectionStartChar == modifier.selectionStartChar) {
            onCompletionsEvent(OnCompletionsEvent(fileName, completions, text.hashCode()))
        }

        AnalyzerWithCompilerReport(
            context.environment.messageCollector,
            context.environment.configuration.languageVersionSettings,
            false
        ).analyzeAndReport(files) { result }
        yield()

        colorizeText(context)
        context.messageCollector.diagnostics
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
        context.messageCollector.clear()

    }

    private fun colorizeText(context: ScriptingContext) {
        if (textHash == 0) return

        val newLines = ScriptColorizer.colorize(context.file, bindingContext, expressionAtCaret)

        val text = newLines.joinToString("\n") { it.text }

        if (text.hashCode() != textHash) return

        lines.clear()
        lines.addAll(newLines)
        surface.triggerUpdate()
    }

    private fun getOffsetFromLineAndChar(file: KtFile, line: Int, charNumber: Int): Int {
        if (line >= file.viewProvider.document.lineCount) return -1
        val lineStart = file.viewProvider.document.getLineStartOffset(line)
        return lineStart + charNumber
    }

    private val expressionAtCaret: PsiElement?
        get() {
            if (modifier.selectionStartChar != modifier.selectionCaretChar || modifier.selectionStartLine != modifier.selectionCaretLine) return null
            if (modifier.selectionStartLine == -1) return null
            val context = context ?: return null

            val caretPositionOffset =
                getOffsetFromLineAndChar(context.file, modifier.selectionCaretLine, modifier.selectionCaretChar)
            if (caretPositionOffset == -1) return null
            var element = context.file.findElementAt(caretPositionOffset)
            if (element == null || element !is KtElement) element = context.file.findElementAt(caretPositionOffset - 1)
            return element
        }

    override fun close() {
        EventBus.unregister(::onCompletionsEvent)
    }
}

object ActionManager {
    private var currentJob: Job? = null
    private var futureTask: Future<*>? = null
    private val scope = CoroutineScope(Dispatchers.Default) + ScriptContext()

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
                coroutineContext[ScriptContext.Key]?.context?.close()
            }
        }
    }

    class ScriptContext(var context: ScriptingContext? = null) : CoroutineContext.Element {
        companion object Key : CoroutineContext.Key<ScriptContext>

        override val key get() = Key
    }
}

private fun ScriptTextAreaScope.installSelectionHandler(
    lines: MutableStateList<TextLine>,
    onChange: (startLine: Int, caretLine: Int, startChar: Int, caretChar: Int) -> Unit,
) {
    val selStartLine = remember(-1)
    val selCaretLine = remember(-1)
    val selStartChar = remember(0)
    val selCaretChar = remember(0)

    modifier.onSelectionChanged = handler@{ startLine, caretLine, startChar, caretChar ->
        if (startLine >= lines.size || startLine < 0) return@handler
        if (caretLine >= lines.size) return@handler
        val start = lines[startLine]
        if (startChar > start.length) return@handler
        val caret = lines[caretLine]
        if (caretChar > caret.length) return@handler

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