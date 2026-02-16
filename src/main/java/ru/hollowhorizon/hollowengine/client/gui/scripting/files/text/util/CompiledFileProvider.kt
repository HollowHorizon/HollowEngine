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
    var currentText: String = file.readText().replace("\t", " ".repeat(TextAreaConfig.INDENT_SIZE))
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

        // Проверка границ для безопасности
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
                lastAction.caretLine = finalCaretLine
                lastAction.caretChar = finalCaretChar
                // startLine/startChar remain the same (start of the group)

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
                // Проверяем, что текст не изменился с момента запроса анализа
                val currentTextSnapshot = lines.joinToString("\n") { it.text }
                if (currentTextSnapshot != txt) {
                    // Текст изменился, пропускаем обновление автодополнений
                    return@withContext
                }
                
                analysisState.completions.clear()
                // Не добавляем автодополнения для пустых строк
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
            val colored = highlight(file.name, textSnapshot, off)

            withContext(KoolDispatchers.Frontend) {
                // Проверяем, что текст не изменился с момента запроса анализа
                val currentTextSnapshot = lines.joinToString("\n") { it.text }
                if (currentTextSnapshot != textSnapshot) {
                    // Текст изменился, пропускаем обновление подсветки
                    return@withContext
                }
                
                if (colored.size == lines.size) {
                    // Обновляем только стили, сохраняя текст
                    for (i in colored.indices) {
                        val oldLine = lines[i]
                        val newLine = colored[i].toKool(font)
                        // Сохраняем текст из старой строки, если он совпадает по длине
                        if (oldLine.text == newLine.text || oldLine.text.length == newLine.text.length) {
                            lines[i] = newLine
                        } else {
                            // Текст изменился, используем новый
                            lines[i] = newLine
                        }
                    }
                } else {
                    // Количество строк изменилось - полная замена
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
            finalCaretLine = action.startLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            val lineText = if (finalCaretLine < lines.size) lines[finalCaretLine].text else ""
            finalCaretChar = action.startChar.coerceIn(0, lineText.length)
        } else {
            // Go to where we ended up after the edit
            finalCaretLine = action.caretLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
            val lineText = if (finalCaretLine < lines.size) lines[finalCaretLine].text else ""
            finalCaretChar = action.caretChar.coerceIn(0, lineText.length)
        }

        val startLineIndex = action.startLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))

        // Safe removal
        if (startLineIndex >= 0 && startLineIndex + linesToRemove.size <= lines.size) {
            lines.subList(startLineIndex, startLineIndex + linesToRemove.size).clear()
        } else {
            lines.clear() // Fallback panic
        }

        lines.addAll(startLineIndex, linesToInsert)
        currentText = lines.joinToString("\n") { it.text }

        // Обновляем позицию каретки с учётом новых строк после undo/redo
        val actualFinalLine = finalCaretLine.coerceIn(0, lines.lastIndex.coerceAtLeast(0))
        val actualLineText = if (actualFinalLine < lines.size) lines[actualFinalLine].text else ""
        val actualFinalChar = finalCaretChar.coerceIn(0, actualLineText.length)

        // Update syntax highlight after undo/redo
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
