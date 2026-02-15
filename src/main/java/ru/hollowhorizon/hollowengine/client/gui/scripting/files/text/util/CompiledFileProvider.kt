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
import ru.hollowhorizon.hollowengine.client.utils.offset
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import java.io.File
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
    val file: File,
    private val analyzer: ScriptingAnalyzer,
) : TextLineProvider, TextEditorHandler, UndoRedoHandler {

    constructor(file: File) : this(file, ScriptingEnvironment.INSTANCE.analyzer)

    val analysisState = EditorAnalysisState()

    val font = MsdfFont(ColorTheme.Fonts.MONOCRAFT, 18f)
    val lines = ArrayList<ScriptTextLine>()
    var currentText: String = file.readText()
        private set(value) {
            field = value
            scheduleWrite(value)
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
        val char: Int
    )

    init {
        // Initial setup
        val initialLines = currentText.lines().map {
            // Simple initial highlight, will be overwritten by analyzer shortly
            ScriptTextLine(listOf(it to TextAttributes(font, Color.WHITE)))
        }
        lines.addAll(initialLines)

        // Trigger initial analysis
        requestAnalysis(0, 0)

        // Start the processing loop
        scope.launch {
            analysisRequest
                .debounce(AnalysisConfig.DEBOUNCE_DELAY_MS)
                .collectLatest { params ->
                    processAnalysis(params)
                }
        }
    }

    fun saveToDisk() {
        writeJob?.cancel()
        writeJob = null
        latestText = null
        file.writeText(currentText)
    }

    private fun scheduleWrite(text: String) {
        writeJob?.cancel()
        latestText = text
        writeJob = scope.launch {
            delay(AnalysisConfig.WRITE_DEBOUNCE_MS)
            latestText?.let { file.writeText(it) }
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

        val lineBefore = lines[selectionStartLine].text.substring(0, selectionStartChar)
        val lineAfter = lines[selectionEndLine].text.substring(selectionEndChar)
        val newTextFull = lineBefore + replacement + lineAfter
        val newLinesRaw = newTextFull.split('\n')

        val oldLinesList = (selectionStartLine..selectionEndLine).map { lines[it] }
        val numLinesToRemove = selectionEndLine - selectionStartLine + 1

        val newTextLines = newLinesRaw.map {
            ScriptTextLine(listOf(it to TextAttributes(font, Color.WHITE)))
        }

        lines.subList(selectionStartLine, selectionStartLine + numLinesToRemove).clear()
        lines.addAll(selectionStartLine, newTextLines)
        currentText = lines.joinToString("\n") { it.text }.replace("\r\n", "\n")

        val newCaretLine = selectionStartLine + newLinesRaw.lastIndex
        val newCaretChar = newLinesRaw.last().length - lineAfter.length

        handleHistoryUpdate(
            selectionStartLine, selectionStartChar,
            oldLinesList, newTextLines,
            replacement, currentTime
        )

        requestAnalysis(selectionStartLine, selectionStartChar)

        return Vec2i(newCaretChar, newCaretLine)
    }

    private fun handleHistoryUpdate(
        startLine: Int, startChar: Int,
        oldLines: List<ScriptTextLine>, newLines: List<ScriptTextLine>,
        replacement: String, currentTime: Long
    ) {
        val isSingleCharInsert = replacement.length == 1 && oldLines.size == 1 && oldLines[0].text.length + 1 == newLines[0].text.length

        if (undoStack.isNotEmpty() && (currentTime - lastEditTime) < AnalysisConfig.UNDO_DEBOUNCE_MS) {
            val lastAction = undoStack.peek()

            // Logic for merging: If we are just appending characters on the same line
            if (lastAction.canMerge && isSingleCharInsert &&
                lastAction.caretLine == startLine &&
                lastAction.caretChar == startChar) {

                // Update the existing action instead of pushing a new one
                lastAction.newLines = newLines // Update result
                lastAction.caretChar += 1 // Move result caret
                // startLine/startChar remain the same (start of the group)

                lastEditTime = currentTime
                redoStack.clear()
                return
            }
        }

        val action = UndoableAction(
            startLine = startLine,
            startChar = startChar,
            caretLine = startLine + newLines.size - 1, // Approximation, refined by replace logic
            caretChar = if(newLines.size == 1) startChar + replacement.length else newLines.last().length,
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
            // 1. Completions
            val safeLine = line.coerceIn(0, txt.lines().lastIndex.coerceAtLeast(0))
            val safeChar = runCatching {
                val ln = txt.lines().getOrNull(safeLine) ?: ""
                char.coerceIn(0, ln.length)
            }.getOrDefault(0)

            val offset = offset(txt, safeLine, safeChar)
            val completions = analyzer.completions(file.name, txt, offset)

            // 2. Diagnostics
            val diagnostics = analyzer.diagnostic(file.name, txt)

            withContext(KoolDispatchers.Frontend) {
                analysisState.completions.clear()
                analysisState.completions.addAll(completions)

                analysisState.diagnostics.clear()
                analysisState.diagnostics.addAll(diagnostics)
            }
        } catch (e: Exception) {
            logD { "Analysis failed: ${e.message}" }
        }
    }

    private suspend fun ScriptingAnalyzer.highlightCode(
        textSnapshot: String,
        selectionStartLine: Int,
        selectionStartChar: Int,
    ) {
        try {
            val off = offset(textSnapshot, selectionStartLine, selectionStartChar)
            val colored = highlight(file.name, textSnapshot, off)

            withContext(KoolDispatchers.Frontend) {
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

    private fun applyAction(action: UndoableAction, isUndo: Boolean, onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        val linesToRemove = if (isUndo) action.newLines else action.oldLines
        val linesToInsert = if (isUndo) action.oldLines else action.newLines

        // Calculate Caret Position AFTER operation
        val finalCaretLine: Int
        val finalCaretChar: Int

        if (isUndo) {
            // Go back to where we started before the edit
            finalCaretLine = action.startLine
            finalCaretChar = action.startChar
        } else {
            // Go to where we ended up after the edit
            finalCaretLine = action.caretLine
            finalCaretChar = action.caretChar
        }

        val startLineIndex = action.startLine

        // Safe removal
        if (startLineIndex >= 0 && startLineIndex + linesToRemove.size <= lines.size) {
            lines.subList(startLineIndex, startLineIndex + linesToRemove.size).clear()
        } else {
            lines.clear() // Fallback panic
        }

        lines.addAll(startLineIndex, linesToInsert)
        currentText = lines.joinToString("\n") { it.text }

        // Update syntax highlight after undo/redo
        requestAnalysis(finalCaretLine, finalCaretChar)

        onSelectionChanged?.invoke(finalCaretLine, finalCaretLine, finalCaretChar, finalCaretChar)
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
