package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.mutableStateListOf
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.KoolDispatchers
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.logD
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoableAction
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorState
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.TextSource
import ru.hollowhorizon.hollowengine.client.utils.offset
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import java.util.*

private object AnalysisConfig {
    const val DEBOUNCE_DELAY_MS = 300L
    const val UNDO_DEBOUNCE_MS = 300L
    const val WRITE_DEBOUNCE_MS = 500L
}

class EditorAnalysisState {
    val completions: MutableList<CompletionItem> = mutableStateListOf()
    val diagnostics: MutableList<Diagnostic> = mutableStateListOf()
}

@OptIn(FlowPreview::class)
class CompiledFileProvider(
    private val source: TextSource,
    private val state: EditorState,
    private val analyzer: ScriptingAnalyzer,
    private val autoSave: Boolean = true,
) : TextLineProvider, TextEditorHandler, UndoRedoHandler {

    val analysisState = EditorAnalysisState()

    val font = MsdfFont(ColorTheme.Fonts.MONOCRAFT, 18f)
    val lines = ArrayList<ScriptTextLine>()

    var onTextChanged: ((String) -> Unit)? = null

    var currentText: String = source.text
        .replace("\t", " ".repeat(state.config.indentSize))
        private set(value) {
            field = value
            onTextChanged?.invoke(value)
            if (autoSave) {
                scheduleWrite(value)
            }
        }

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(KoolDispatchers.Frontend + scopeJob)

    private val analysisRequest = MutableSharedFlow<AnalysisParams>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var writeJob: Job? = null

    @Volatile
    private var latestText: String? = null

    private data class AnalysisParams(
        val text: String,
        val line: Int,
        val char: Int,
    )

    init {
        val initialLines = currentText.lines().map {
            ScriptTextLine(listOf(it to TextAttributes(font, Color.WHITE)))
        }
        lines.addAll(initialLines)

        requestAnalysis(0, 0)

        scope.launch {
            analysisRequest
                .debounce(AnalysisConfig.DEBOUNCE_DELAY_MS)
                .collectLatest { params -> processAnalysis(params) }
        }
    }

    fun saveToDisk() {
        writeJob?.cancel()
        writeJob = null
        latestText = null
        source.save(currentText)
    }

    private fun scheduleWrite(text: String) {
        writeJob?.cancel()
        latestText = text
        writeJob = scope.launch {
            delay(AnalysisConfig.WRITE_DEBOUNCE_MS)
            latestText?.let { source.save(it) }
        }
    }

    fun dispose() {
        writeJob?.cancel()
        writeJob = null
        scope.cancel()
    }

    // --- Undo/Redo Stack ---
    private val undoStack = Stack<UndoableAction>()
    private val redoStack = Stack<UndoableAction>()
    private var lastEditTime = 0L


    override val size get() = lines.size

    fun getOrNull(index: Int): ScriptTextLine? {
        return if (index in lines.indices) lines[index] else null
    }

    override fun get(index: Int): ScriptTextLine {
        if (index !in lines.indices) throw IndexOutOfBoundsException("Index $index out of bounds (size: $size)")
        return lines[index]
    }

    override fun insertText(line: Int, caret: Int, insertion: String): Vec2i {
        return replaceText(line, line, caret, caret, insertion)
    }

    override fun replaceText(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        replacement: String,
    ): Vec2i {
        val currentTime = System.currentTimeMillis()

        val safeStartLine = selectionStartLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val safeEndLine = selectionEndLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val startLineText = lines[safeStartLine].text
        val endLineText = lines[safeEndLine].text
        val safeStartChar = selectionStartChar.coerceIn(0, startLineText.length)
        val safeEndChar = selectionEndChar.coerceIn(0, endLineText.length)

        val lineBefore = startLineText.substring(0, safeStartChar)
        val lineAfter = endLineText.substring(safeEndChar)
        val newTextFull = lineBefore + replacement + lineAfter
        val newLinesRaw = newTextFull.split('\n')

        val oldLinesList = (safeStartLine..safeEndLine).map { lines[it] }
        val numLinesToRemove = safeEndLine - safeStartLine + 1

        val newTextLines = newLinesRaw.map {
            ScriptTextLine(listOf(it to TextAttributes(font, Color.WHITE)))
        }

        lines.subList(safeStartLine, safeStartLine + numLinesToRemove).clear()
        lines.addAll(safeStartLine, newTextLines)
        currentText = lines.joinToString("\n") { it.text }.replace("\r\n", "\n")

        val newCaretLine = safeStartLine + newLinesRaw.lastIndex
        val newCaretChar = newLinesRaw.last().length - lineAfter.length

        handleHistoryUpdate(
            safeStartLine, safeStartChar,
            newCaretLine, newCaretChar,
            oldLinesList, newTextLines,
            replacement, currentTime
        )

        requestAnalysis(safeStartLine, safeStartChar)

        return Vec2i(newCaretChar, newCaretLine)
    }

    private fun handleHistoryUpdate(
        startLine: Int, startChar: Int,
        finalCaretLine: Int, finalCaretChar: Int,
        oldLines: List<ScriptTextLine>, newLines: List<ScriptTextLine>,
        replacement: String, currentTime: Long,
    ) {
        val isSingleCharInsert = replacement.length == 1 &&
                oldLines.size == 1 &&
                oldLines[0].text.length + 1 == newLines[0].text.length

        if (undoStack.isNotEmpty() && (currentTime - lastEditTime) < AnalysisConfig.UNDO_DEBOUNCE_MS) {
            val lastAction = undoStack.peek()

            if (lastAction.canMerge && isSingleCharInsert &&
                lastAction.caretLine == startLine &&
                lastAction.caretChar == startChar
            ) {
                lastAction.newLines = newLines
                lastAction.caretLine = finalCaretLine
                lastAction.caretChar = finalCaretChar
                lastEditTime = currentTime
                redoStack.clear()
                return
            }
        }

        val action = UndoableAction(
            startLine = startLine,
            startChar = startChar,
            caretLine = finalCaretLine,
            caretChar = finalCaretChar,
            oldLines = oldLines,
            newLines = newLines,
            canMerge = isSingleCharInsert
        )

        undoStack.push(action)
        redoStack.clear()
        lastEditTime = currentTime
    }

    private fun requestAnalysis(line: Int, char: Int) {
        val textSnapshot = currentText
        val safeLine = line.coerceIn(0, textSnapshot.lines().lastIndex.coerceAtLeast(0))
        val safeChar = runCatching {
            val ln = textSnapshot.lines().getOrNull(safeLine) ?: ""
            char.coerceIn(0, ln.length)
        }.getOrDefault(0)

        scope.launch {
            withContext(Dispatchers.IO) {
                analyzer.highlightCode(textSnapshot, safeLine, safeChar)
            }
            analysisRequest.emit(AnalysisParams(textSnapshot, safeLine, safeChar))
        }
    }

    private suspend fun processAnalysis(params: AnalysisParams) = withContext(Dispatchers.Default) {
        val (txt, line, char) = params
        try {
            val safeLine = line.coerceIn(0, txt.lines().lastIndex.coerceAtLeast(0))
            val safeChar = runCatching {
                val ln = txt.lines().getOrNull(safeLine) ?: ""
                char.coerceIn(0, ln.length)
            }.getOrDefault(0)

            val offset = offset(txt, safeLine, safeChar)
            val completions = analyzer.completions(source.name, txt, offset)
            val diagnostics = analyzer.diagnostic(source.name, txt)

            withContext(KoolDispatchers.Frontend) {
                val currentTextSnapshot = lines.joinToString("\n") { it.text }
                if (currentTextSnapshot != txt) return@withContext

                analysisState.completions.clear()
                val safeLineText = txt.lines().getOrNull(safeLine) ?: ""
                if (safeLineText.isNotBlank() || completions.isEmpty()) {
                    analysisState.completions.addAll(completions)
                }

                analysisState.diagnostics.clear()
                analysisState.diagnostics.addAll(diagnostics)
            }
        } catch (e: Exception) {
            logD { "Analysis failed: ${e.stackTraceToString()}" }
        }
    }

    private suspend fun ScriptingAnalyzer.highlightCode(
        textSnapshot: String,
        selectionStartLine: Int,
        selectionStartChar: Int,
    ) {
        try {
            val off = offset(textSnapshot, selectionStartLine, selectionStartChar)
            val colored = highlight(source.name, textSnapshot, off)

            withContext(KoolDispatchers.Frontend) {
                val currentTextSnapshot = lines.joinToString("\n") { it.text }
                if (currentTextSnapshot != textSnapshot) return@withContext

                if (colored.size == lines.size) {
                    for (i in colored.indices) {
                        lines[i] = colored[i].toKool(font)
                    }
                } else {
                    lines.clear()
                    lines.addAll(colored.map { it.toKool(font) })
                }
            }
        } catch (_: Exception) {
            // Fallback if highlighting crashes
        }
    }

    fun setText(text: String) {
        undoStack.clear()
        redoStack.clear()
        replaceText(0, lines.lastIndex.coerceAtLeast(0), 0, lines.lastOrNull()?.length ?: 0, text)
    }

    private fun applyAction(
        action: UndoableAction,
        isUndo: Boolean,
        onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?,
    ) {
        val linesToRemove = if (isUndo) action.newLines else action.oldLines
        val linesToInsert = if (isUndo) action.oldLines else action.newLines

        val finalCaretLine: Int
        val finalCaretChar: Int
        if (isUndo) {
            finalCaretLine = action.startLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            val lineText = if (finalCaretLine < lines.size) lines[finalCaretLine].text else ""
            finalCaretChar = action.startChar.coerceIn(0, lineText.length)
        } else {
            finalCaretLine = action.caretLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            val lineText = if (finalCaretLine < lines.size) lines[finalCaretLine].text else ""
            finalCaretChar = action.caretChar.coerceIn(0, lineText.length)
        }

        val startLineIndex = action.startLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        if (startLineIndex >= 0 && startLineIndex + linesToRemove.size <= lines.size) {
            lines.subList(startLineIndex, startLineIndex + linesToRemove.size).clear()
        } else {
            lines.clear()
        }
        lines.addAll(startLineIndex, linesToInsert)
        currentText = lines.joinToString("\n") { it.text }

        val actualFinalLine = finalCaretLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val actualLineText = if (actualFinalLine < lines.size) lines[actualFinalLine].text else ""
        val actualFinalChar = finalCaretChar.coerceIn(0, actualLineText.length)

        requestAnalysis(actualFinalLine, actualFinalChar)
        onSelectionChanged?.invoke(actualFinalLine, actualFinalLine, actualFinalChar, actualFinalChar)
    }

    override fun undo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (undoStack.isNotEmpty()) {
            val action = undoStack.pop()
            applyAction(action, isUndo = true, onSelectionChanged)
            redoStack.push(action)
        }
    }

    override fun redo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (redoStack.isNotEmpty()) {
            val action = redoStack.pop()
            applyAction(action, isUndo = false, onSelectionChanged)
            undoStack.push(action)
        }
    }
}
