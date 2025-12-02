package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.KoolDispatchers
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.logD
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoableAction
import ru.hollowhorizon.hollowengine.client.utils.offset
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import java.io.File
import java.util.*

class CompiledFileProvider(
    val file: File,
    val onDiagnose: (List<Diagnostic>) -> Unit,
    val onCompletions: (List<CompletionItem>) -> Unit,
) : TextLineProvider, TextEditorHandler, UndoRedoHandler {

    val font = MsdfFont(HACK_FONT, 18f)
    val lines = ArrayList<ScriptTextLine>()
    var currentText: String = file.readText()
        private set(value) {
            field = value
            file.writeText(value)
        }

    // --- Async & Debounce Setup ---
    private val scope = CoroutineScope(KoolDispatchers.Frontend + SupervisorJob())
    private val analysisRequest = MutableSharedFlow<AnalysisParams>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

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
                .debounce(300L) // 300ms debounce
                .collectLatest { params ->
                    processAnalysis(params)
                }
        }
    }

    fun dispose() {
        scope.cancel()
    }

    // --- Undo/Redo Stack ---
    private val undoStack = Stack<UndoableAction>()
    private val redoStack = Stack<UndoableAction>()
    private var lastEditTime = 0L

    override val size get() = lines.size

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

        // 1. Prepare Text Update
        val lineBefore = lines[selectionStartLine].text.substring(0, selectionStartChar)
        val lineAfter = lines[selectionEndLine].text.substring(selectionEndChar)
        val newTextFull = lineBefore + replacement + lineAfter
        val newLinesRaw = newTextFull.split('\n')

        // 2. Prepare Undo Action Data
        val oldLinesList = (selectionStartLine..selectionEndLine).map { lines[it] }
        val numLinesToRemove = selectionEndLine - selectionStartLine + 1

        // Create new visual lines (temporarily plain white until highlighted)
        val newTextLines = newLinesRaw.map {
            ScriptTextLine(listOf(it to TextAttributes(font, Color.WHITE)))
        }

        // 3. Apply to Data Structure
        lines.subList(selectionStartLine, selectionStartLine + numLinesToRemove).clear()
        lines.addAll(selectionStartLine, newTextLines)
        currentText = lines.joinToString("\n") { it.text }.replace("\r\n", "\n")

        // 4. Calculate New Caret
        val newCaretLine = selectionStartLine + newLinesRaw.lastIndex
        val newCaretChar = newLinesRaw.last().length - lineAfter.length

        // 5. Handle History (Smart Merge/Debounce)
        handleHistoryUpdate(
            selectionStartLine, selectionStartChar,
            oldLinesList, newTextLines,
            replacement, currentTime
        )

        // 6. Trigger Analysis (Async)
        // We explicitly call highlightCode synchronously for immediate syntax coloring feedback if possible,
        // but usually, full analysis goes to background.
        // Here we do a quick highlight update on the main thread for responsiveness,
        // and let the heavy analyzer run in background.
        requestAnalysis(selectionStartLine, selectionStartChar)

        return Vec2i(newCaretChar, newCaretLine)
    }

    private fun handleHistoryUpdate(
        startLine: Int, startChar: Int,
        oldLines: List<ScriptTextLine>, newLines: List<ScriptTextLine>,
        replacement: String, currentTime: Long
    ) {
        val isSingleCharInsert = replacement.length == 1 && oldLines.size == 1 && oldLines[0].text.length + 1 == newLines[0].text.length

        // Try to merge with previous action if:
        // 1. Not too much time passed (300ms)
        // 2. Previous action exists
        // 3. Previous action was also a simple typing (not a big paste/cut)
        // 4. We are typing consecutively
        if (undoStack.isNotEmpty() && (currentTime - lastEditTime) < 300) {
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
        // Highlight immediately locally (fast regex or lexical check could go here)
        ScriptingEnvironment.INSTANCE.analyzer.highlightCode(line, char)
        // Queue heavy analysis
        scope.launch {
            analysisRequest.emit(AnalysisParams(currentText, line, char))
        }
    }

    private suspend fun processAnalysis(params: AnalysisParams) = withContext(Dispatchers.Default) {
        val (txt, line, char) = params
        try {
            // 1. Completions
            val offset = offset(txt, line, char)
            val completions = ScriptingEnvironment.INSTANCE.analyzer.completions(file.name, txt, offset)

            // 2. Diagnostics
            val diagnostics = ScriptingEnvironment.INSTANCE.analyzer.diagnostic(file.name, txt)

            withContext(KoolDispatchers.Frontend) {
                onCompletions(completions)
                onDiagnose(diagnostics)
            }
        } catch (e: Exception) {
            logD { "Analysis failed: ${e.message}" }
        }
    }

    private fun ScriptingAnalyzer.highlightCode(selectionStartLine: Int, selectionStartChar: Int) {
        // This runs on Main Thread, should be fast.
        // If 'highlight' is slow, it should also move to background,
        // but usually syntax highlighting needs to be synchronous to avoid "flashing".
        try {
            val offset = offset(currentText, selectionStartLine, selectionStartChar)
            val colored = highlight(file.name, currentText, offset)

            // Update lines in place efficiently
            // Note: 'colored' size must match 'lines' size usually.
            if (colored.size == lines.size) {
                for (i in colored.indices) {
                    // Replace line content without triggering generic list change events if possible,
                    // but here we just swap the object or update internal attributes.
                    lines[i] = colored[i].toKool(font)
                }
            } else {
                lines.clear()
                lines.addAll(colored.map { it.toKool(font) })
            }
        } catch (e: Exception) {
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
        ScriptingEnvironment.INSTANCE.analyzer.highlightCode(finalCaretLine, finalCaretChar)

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