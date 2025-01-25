package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.*
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.OnColorizedEvent
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.OnCompletionsEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent

var currentLine = 0
var currentColumn = 0

class TextFileData(project: IDEGuiV2, name: String, path: String, private val code: String) :
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

    fun onColorizedEvent(event: OnColorizedEvent) = launchOnMainThread {
        if (event.fileName != fileName || event.hashCode != textHash) return@launchOnMainThread

        lines.clear()
        lines.addAll(event.text)
    }

    fun onCompletionsEvent(event: OnCompletionsEvent) = launchOnMainThread {
        if (event.fileName != fileName || event.hashCode != textHash) return@launchOnMainThread

        modifier.completions.clear()
        modifier.completions.addAll(event.completions)
    }

    fun onErrorsEvent(errors: List<ScriptError>) {
        modifier.errors.clear()
        modifier.errors.addAll(errors)
    }

    override fun save() {
        if (filePath.startsWith("%")) return
        SaveFilePacket(filePath, lines.joinToString("\n") { it.text }.toByteArray()).send()
    }

    override fun UiScope.compose() {
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

    private suspend fun compileText(text: String) {
        val script = ScriptingCompiler.compileText<StoryEvent>(text, fileName, logErrors = false)
        script.errors?.ifNotEmpty(::onErrorsEvent) ?: run { modifier.errors.clear() }
    }

    override fun close() {
        EventBus.unregister(::onColorizedEvent)
        EventBus.unregister(::onCompletionsEvent)
    }
}

object ActionManager {
    private var currentJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    fun launch(action: suspend () -> Unit) {
        currentJob?.cancel()
        currentJob = scope.launch debounce@ {
            delay(300)
            if(!isActive) return@debounce

            action()
        }
    }
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