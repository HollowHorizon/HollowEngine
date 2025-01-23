package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
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
    private val lines = mutableStateListOf(*code.lines().map {
        TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 30f), Color.WHITE)))
    }.toTypedArray())
    val editor = DefaultTextEditorHandler(lines)

    private var textVersion = 0 // Версия текста
    private var currentVersion = 0 // Версия в момент обработки событий
    private val updateLines = mutableListOf<TextLine>()
    private val updateCompletions = mutableListOf<CompletionVariant>()
    private val updateErrors = mutableListOf<ScriptError>()
    lateinit var modifier: ScriptTextAreaModifier

    init {
        EventBus.register(::onColorizedEvent)
        EventBus.register(::onCompletionsEvent)
        ActionManager.launchNewAction { compileText() }
    }

    fun onColorizedEvent(event: OnColorizedEvent) = launchOnMainThread {
        // Привязка к текущей версии текста
        if (event.fileName == fileName) {
            synchronized(this) {
                if (currentVersion == textVersion) { // Обрабатываем только для текущей версии
                    updateLines.clear()
                    updateLines.addAll(event.text)
                }
            }
        }
    }

    fun onCompletionsEvent(event: OnCompletionsEvent) = launchOnMainThread {
        // Аналогичная проверка для автодополнений
        if (event.fileName == fileName) {
            synchronized(this) {
                if (currentVersion == textVersion) {
                    updateCompletions.clear()
                    updateCompletions.addAll(event.completions)
                }
            }
        }
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
            installDefaultSelectionHandler()

            modifier.padding(sizes.smallGap).editorHandler(editor)
            modifier.onCharTyped = {
                currentLine = modifier.selectionStartLine
                currentColumn = modifier.selectionStartChar
                synchronized(this@TextFileData) {
                    textVersion++ // Увеличиваем версию текста
                }
                ActionManager.launchNewAction {
                    compileText()
                    save()
                }
            }

            surface.onEachFrame {
                if (ActionManager.isDone) {
                    synchronized(this@TextFileData) {
                        // Обновление подсветки
                        updateLines.ifNotEmpty {
                            lines.clear()
                            lines.addAll(this)
                            clear()
                        }

                        // Обновление автодополнений
                        updateCompletions.ifNotEmpty {
                            modifier.completions.clear()
                            modifier.completions.addAll(this)
                            clear()
                        }

                        // Обновление ошибок
                        updateErrors.ifNotEmpty {
                            modifier.errors.clear()
                            modifier.errors.addAll(this)
                            clear()
                        }
                    }
                }
            }
        }
    }

    private suspend fun compileText() {
        synchronized(this) {
            currentVersion = textVersion // Привязываем анализ к текущей версии
        }
        val script = ScriptingCompiler.compileText<StoryEvent>(
            lines.joinToString("\n") { it.text },
            fileName
        )
        synchronized(this) {
            if (currentVersion == textVersion) { // Проверка перед применением
                updateErrors.clear()
                updateErrors.addAll(script.errors ?: emptyList())
            }
        }
    }

    override fun close() {
        EventBus.unregister(::onColorizedEvent)
        EventBus.unregister(::onCompletionsEvent)
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