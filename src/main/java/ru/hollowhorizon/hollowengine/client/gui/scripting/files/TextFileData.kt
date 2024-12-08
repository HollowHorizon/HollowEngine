package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.editor.ui.lineHeight
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.*
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionVariant
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.OnColorizedEvent
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.OnCompletionsEvent
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent
import java.util.concurrent.atomic.AtomicReference


var currentLine = 0
var currentColumn = 0

class TextFileData(project: IDEGuiV2, name: String, path: String, val code: String) :
    FileData(project, name, path) {
    val lines = mutableStateListOf(*code.lines().map {
        TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 30f), Color.WHITE)))
    }.toTypedArray())
    val editor = DefaultTextEditorHandler(lines)

    var isChanged = false
    private val updateLines = mutableListOf<TextLine>()
    private val updateCompletions = mutableListOf<CompletionVariant>()

    lateinit var modifier: ScriptTextAreaModifier

    init {
        EventBus.register(::colorizeText)
        EventBus.register(::onCompletion)
        ActionManager.launchNewAction {
            ScriptingCompiler.compileText<StoryEvent>(code, fileName)
        }
    }

    fun colorizeText(event: OnColorizedEvent) = launchOnMainThread {
        if (event.fileName == fileName && event.text.isNotEmpty()) {
            updateLines.clear()
            updateLines.addAll(event.text)
            isChanged = true
        }
    }

    fun onCompletion(event: OnCompletionsEvent) = launchOnMainThread {
        if (event.fileName == fileName) {
            updateCompletions.clear()
            updateCompletions.addAll(event.completions)
            isChanged = true
        }
    }

    override fun save() {
        if (filePath.startsWith("%")) return
        SaveFilePacket(filePath, lines.joinToString("\n") { it.filteredText() }.toByteArray()).send()
    }

    override fun UiScope.compose() {
        ScriptTextArea(
            ListTextLineProvider(lines),
            width = Grow.Std,
            height = Grow.Std //MsdfFont(HACK_FONT, 30f).lineHeight.dp * lines.size.coerceAtMost(39) + sizes.lineHeight,
        ) {
            this@TextFileData.modifier = modifier
            installDefaultSelectionHandler()

            modifier.padding(sizes.smallGap).editorHandler(editor)
            modifier.onCharTyped = {
                currentLine = modifier.selectionStartLine
                currentColumn = modifier.selectionStartChar
                ActionManager.launchNewAction {
                    ScriptingCompiler.compileText<StoryEvent>(
                        lines.joinToString("\n") { it.filteredText() },
                        fileName
                    )
                    save()
                }
            }

            surface.onEachFrame {
                if (ActionManager.isDone && isChanged) {
                    updateLines.ifNotEmpty {
                        lines.clear()
                        lines.addAll(this)
                        clear()
                    }

                    updateCompletions.ifNotEmpty {
                        modifier.completions.clear()
                        modifier.completions.addAll(this)
                        clear()
                    }
                    isChanged = false
                }
            }
        }
    }

    override fun close() {
        EventBus.unregister(::colorizeText)
    }
}

object ActionManager {
    private var currentJob: Job? = null
    val scope = CoroutineScope(Dispatchers.Default)
    val lastJob = AtomicReference<Job?>(null)

    val isDone get() = currentJob?.isCompleted == true && lastJob.get()?.isCompleted == true

    fun launchNewAction(action: suspend () -> Unit) {
        currentJob = scope.launch {
            val currentJob = scope.launch {
                action()
            }

            // Заменяем последнюю задачу на текущую
            lastJob.getAndSet(currentJob)?.join()

            currentJob.join()
        }
    }
}

fun TextLine.filteredText() = spans.joinToString("") { (str, attribs) -> if (attribs.background == null) str else "" }