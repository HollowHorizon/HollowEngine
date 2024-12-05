package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.editor.ui.backgroundMid
import de.fabmax.kool.editor.ui.hoverBg
import de.fabmax.kool.editor.ui.lineHeight
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.Job
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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.coroutines.cancellation.CancellationException


var currentLine = 0
var currentColumn = 0

class TextFileData(project: IDEGuiV2, name: String, path: String, val code: String) :
    FileData(project, name, path) {
    val lines = mutableStateListOf(*code.lines().map {
        TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 30f), Color.WHITE)))
    }.toTypedArray())
    val editor = DefaultTextEditorHandler(lines)
    val completions = mutableListOf<CompletionVariant>()

    var isChanged = false
    private val updateLines = mutableListOf<TextLine>()
    private val updateCompletions = mutableListOf<CompletionVariant>()

    init {
        EventBus.register(::colorizeText)
        EventBus.register(::onCompletion)
        ActionManager.launchNewAction {
            ScriptingCompiler.compileText<StoryEvent>(code, fileName)
        }
    }

    fun colorizeText(event: OnColorizedEvent) = launchOnMainThread {
        if (event.fileName == fileName) {
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
        SaveFilePacket(filePath, code.toByteArray()).send()
    }

    override fun UiScope.compose() {
        Row {
            Box {
                modifier.margin(horizontal = 10.dp)
                    .padding(sizes.smallGap)
                Column {
                    lines.indices.forEach { i ->
                        Text(i.toString()) {
                            modifier.alignX(AlignmentX.End).font(MsdfFont(HACK_FONT, 30f))
                        }
                    }
                }
            }
            divider()
            TextArea(
                ListTextLineProvider(lines),
                width = FitContent, height = MsdfFont(HACK_FONT, 30f).lineHeight.dp * lines.size + sizes.lineHeight,
            ) {
                modifier.padding(sizes.smallGap)
                installDefaultSelectionHandler()
                modifier.editorHandler(editor)
                val old = modifier.onSelectionChanged
                modifier.onSelectionChanged { startChar, i2, startColumn, i4 ->
                    old?.invoke(startChar, i2, startColumn, i4)
                    ActionManager.launchNewAction {
                        currentLine = startChar
                        currentColumn = startColumn
                        ScriptingCompiler.compileText<StoryEvent>(
                            lines.joinToString("\n") { it.filteredText() },
                            fileName
                        )
                    }
                }
                surface.onEachFrame {
                    if(ActionManager.isDone && isChanged) {
                        lines.clear()
                        lines.addAll(updateLines)
                        completions.clear()
                        completions.addAll(updateCompletions)
                        isChanged = false
                    }
                }

                if (completions.isNotEmpty()) {
                    val font = MsdfFont(HACK_FONT, 30f)
                    val width = lines[currentLine].charIndexToPx(modifier.selectionCaretChar)

                    Popup(
                        uiNode.leftPx + width + uiNode.paddingStartPx,
                        uiNode.topPx + (modifier.selectionStartLine + 1) * font.lineHeight + sizes.gap.px
                    ) {
                        modifier.padding(sizes.smallGap)
                            .height(Dp((sizes.normalText.lineHeight + sizes.smallGap.px * 2) * 10 + sizes.smallGap.px * 2))
                            .width(Grow(1f, max=FitContent))
                            .background(RoundRectBackground(colors.backgroundMid, sizes.gap))
                            .border(RoundRectBorder(colors.hoverBg, sizes.gap, 3.dp))
                            .zLayer(UiSurface.LAYER_POPUP)

                        LazyList(
                            withVerticalScrollbar = true,
                            withHorizontalScrollbar = true,
                            isScrollableHorizontal = true,
                            vScrollbarModifier = { it.width(10.dp).margin(5.dp).zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING) },
                            hScrollbarModifier = { it.height(10.dp).margin(5.dp).zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING) },
                        ) {
                            items(completions) { it() }
                        }
                    }
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
    private val executor = Executors.newSingleThreadExecutor()
    private var futureTask: Future<*>? = null

    val isDone get() = currentJob?.isCompleted == true && futureTask?.isDone == true

    fun launchNewAction(action: suspend () -> Unit) {
        currentJob?.cancel()
        futureTask?.cancel(true)

        currentJob = scopeSync {
            try {
                action()
            } catch (_: CancellationException) {
                HollowCore.LOGGER.info("Code analysis stopped.")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun <T> future(action: () -> T): Future<T> = executor.submit(Callable { action() })
        .also { futureTask = it }
}

fun TextLine.filteredText() = spans.joinToString("") { (str, attribs) -> if (attribs.background == null) str else "" }