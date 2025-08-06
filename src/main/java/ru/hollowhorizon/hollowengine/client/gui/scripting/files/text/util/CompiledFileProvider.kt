package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.logE
import org.eclipse.lsp4j.*
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoableAction
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.KotlinLanguageServer
import ru.hollowhorizon.hollowengine.common.project.kt.completion.completions
import ru.hollowhorizon.hollowengine.common.project.kt.position.offset
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.ScriptColorizer
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CompiledFileProvider(
    val file: File,
    val completionProvider: (CompletionList, String) -> Unit,
    val errorsProvider: (Diagnostics) -> Unit,
) : TextLineProvider, TextEditorHandler, UndoRedoHandler {
    private val sp = KotlinLanguageServer.sourcePath
    private var lock = ReentrantLock()
    val font = MsdfFont(HACK_FONT, 18f)
    val lines = ArrayList<TextLine>()
    private var version = 0
    var isRecompiling = false
        private set
    private val undoStack = ArrayDeque<UndoableAction>()
    private val redoStack = ArrayDeque<UndoableAction>()

    init {
        KotlinLanguageServer.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(
                    file.path, "kotlin", 0, sp.contentProvider.contentOf(file)
                )
            )
        )
    }

    private var compiledFile: CompiledFile = recover(Position(0, 0), Recompile.NEVER).first

    init {
        colorizeAsync(0, 0, 0, 0, 0, false)
    }

    enum class Recompile {
        ALWAYS, AFTER_DOT, NEVER
    }

    fun recover(position: Position, recompile: Recompile = Recompile.NEVER): Pair<CompiledFile, Int> {
        val content = sp.content(file)
        val offset = offset(content, position.line, position.character)
        val shouldRecompile = when (recompile) {
            Recompile.ALWAYS -> true
            Recompile.AFTER_DOT -> offset > 0 && content[offset - 1] == '.'
            Recompile.NEVER -> false
        }
        val compiled = if (shouldRecompile) sp.currentVersion(file) else sp.latestCompiledVersion(file)
        return Pair(compiled, offset)
    }

    override val size get() = lines.size

    override fun get(index: Int): TextLine {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index is out of bounds for CompiledFileProvider with size $size.")
        }
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
        try {
            val oldText = getTextFromRange(selectionStartLine, selectionStartChar, selectionEndLine, selectionEndChar)
            val replacementLines = replacement.lines()
            val M = replacementLines.size
            val endLineAfter = if (M == 1) selectionStartLine else selectionStartLine + M - 1
            val endCharAfter = if (M == 1) selectionStartChar + replacement.length else replacementLines.last().length

            val action = UndoableAction(
                startLine = selectionStartLine,
                caretLine = endLineAfter,
                startChar = selectionStartChar,
                caretChar = endCharAfter,
                numOldLines = 0, // Не используется
                oldLines = listOf(TextLine(listOf(oldText to TextAttributes(font, Color.WHITE)))),
                newLines = listOf(TextLine(listOf(replacement to TextAttributes(font, Color.WHITE))))
            )
            undoStack.addLast(action)
            redoStack.clear()

            val textVersion = ++version
            KotlinLanguageServer.textDocumentService.didChange(
                DidChangeTextDocumentParams(
                    VersionedTextDocumentIdentifier(file.path, textVersion), listOf(
                        TextDocumentContentChangeEvent(
                            Range(
                                Position(selectionStartLine, selectionStartChar),
                                Position(selectionEndLine, selectionEndChar)
                            ), replacement
                        )
                    )
                )
            )

            colorizeAsync(
                endLineAfter,
                endCharAfter,
                endLineAfter,
                endCharAfter,
                textVersion,
                showCompletions = replacement.length == 1 && (replacement[0].isLetterOrDigit() || replacement[0] == '.')
            )

            return when {
                replacement.isEmpty() -> Vec2i(selectionStartChar, selectionStartLine)
                !replacement.contains('\n') -> Vec2i(selectionStartChar + replacement.length, selectionStartLine)
                else -> {
                    val lines = replacement.lines()
                    Vec2i(lines.last().length, selectionStartLine + lines.size - 1)
                }
            }
        } catch (e: Exception) {
            logE { e.stackTraceToString() }
            return Vec2i(selectionStartChar, selectionStartLine)
        }
    }

    private fun getTextFromRange(startLine: Int, startChar: Int, endLine: Int, endChar: Int): String {
        if (startLine < 0 || startLine >= lines.size || endLine < 0 || endLine >= lines.size || startChar < 0 || endChar < 0) {
            throw IndexOutOfBoundsException("Invalid range: lines $startLine to $endLine, chars $startChar to $endChar")
        }
        if (startLine == endLine) {
            val line = lines[startLine].text
            if (line.isEmpty()) return ""
            if (startChar > line.length || endChar > line.length || startChar > endChar) {
                throw IndexOutOfBoundsException("Invalid char range in line $startLine: $startChar to $endChar, line length ${line.length}")
            }
            return line.substring(startChar, endChar)
        } else {
            val builder = StringBuilder()
            val firstLine = lines[startLine].text
            if (startChar > firstLine.length) {
                throw IndexOutOfBoundsException("Start char $startChar out of range in line $startLine, length ${firstLine.length}")
            }
            builder.append(firstLine.substring(startChar))
            for (i in startLine + 1 until endLine) {
                builder.append("\n").append(lines[i].text)
            }
            val lastLine = lines[endLine].text
            if (endChar > lastLine.length) {
                throw IndexOutOfBoundsException("End char $endChar out of range in line $endLine, length ${lastLine.length}")
            }
            builder.append("\n").append(lastLine.substring(0, endChar))
            return builder.toString()
        }
    }

    private fun colorizeAsync(
        selectionStartLine: Int,
        selectionStartChar: Int,
        selectionEndLine: Int,
        selectionEndChar: Int,
        textVersion: Int,
        showCompletions: Boolean,
    ) {
        isRecompiling = true
        recolorize(selectionStartLine, selectionEndLine, selectionStartChar, selectionEndChar, true)

        KotlinLanguageServer.textDocumentService.apply {
            debounceHighlight.schedule {
                val result = async.compute {
                    val cursor = lock.withLock {
                        val (newCompiledFile, cursor) = recover(
                            Position(selectionStartLine, selectionStartChar), Recompile.ALWAYS
                        )
                        isRecompiling = false
                        if (textVersion != version) return@compute -1
                        compiledFile = newCompiledFile
                        recolorize(selectionStartLine, selectionEndLine, selectionStartChar, selectionEndChar, false)
                        cursor
                    }

                    cursor
                }

                if (showCompletions) result.thenAcceptAsync { cursor ->
                    if (cursor == -1) return@thenAcceptAsync
                    val completions = completions(compiledFile, cursor, sp.index, config.completion)
                    completionProvider(completions.first, completions.second)
                }
            }
        }
    }

    fun recolorize(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        light: Boolean,
    ) {
        sp.sourceFile(file).apply { parseIfChanged() }.let { source ->
            val file = if(light) source.parsed else source.compiledFile
            file?.let {
                var highlight = it.findElementAt(offset(source.content, selectionStartLine, selectionStartChar))
                if (highlight == null || highlight is PsiWhiteSpace) highlight =
                    it.findElementAt(offset(source.content, selectionStartLine, selectionStartChar) - 1)
                val changed = ScriptColorizer.colorize(
                    it, font, source.compiledContext ?: BindingContext.EMPTY, highlight
                )
                if (light) {
                    val lines = mergeHighlight(
                        lines, changed, selectionStartLine, selectionEndLine
                    )
                    this.lines.clear()
                    this.lines.addAll(lines)
                } else {
                    lines.clear()
                    lines.addAll(changed)
                    source.compiledContext?.let { errorsProvider(it.diagnostics) }
                }
                if (lines.isEmpty()) lines.add(TextLine(listOf("" to TextAttributes(font, Color.WHITE))))

            }
        }
    }

    fun setText(text: String) {
        replaceText(0, lines.lastIndex, 0, lines.last().text.length, text)
    }

    override fun undo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (undoStack.isEmpty()) return
        val action = undoStack.removeLast()
        val oldText = action.oldLines[0].text
        val newText = action.newLines[0].text
        val oldTextLines = oldText.lines()
        val K = oldTextLines.size
        val endLine = if (K == 1) action.startLine else action.startLine + K - 1
        val endChar = if (K == 1) action.startChar + oldText.length else oldTextLines.last().length

        KotlinLanguageServer.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(file.path, ++version),
                listOf(
                    TextDocumentContentChangeEvent(
                        Range(
                            Position(action.startLine, action.startChar),
                            Position(action.caretLine, action.caretChar)
                        ),
                        oldText
                    )
                )
            )
        )
        colorizeAsync(action.startLine, action.startChar, action.caretLine, action.caretChar, version, false)
        redoStack.addLast(action)

        onSelectionChanged?.invoke(action.startLine, endLine, action.startChar, endChar)
    }

    override fun redo(onSelectionChanged: ((Int, Int, Int, Int) -> Unit)?) {
        if (redoStack.isEmpty()) return
        val action = redoStack.removeLast()
        val oldText = action.oldLines[0].text
        val newText = action.newLines[0].text
        val oldTextLines = oldText.lines()
        val K = oldTextLines.size
        val endLine = if (K == 1) action.startLine else action.startLine + K - 1
        val endChar = if (K == 1) action.startChar + oldText.length else oldTextLines.last().length

        KotlinLanguageServer.textDocumentService.didChange(
            DidChangeTextDocumentParams(
                VersionedTextDocumentIdentifier(file.path, ++version),
                listOf(
                    TextDocumentContentChangeEvent(
                        Range(
                            Position(action.startLine, action.startChar),
                            Position(endLine, endChar)
                        ),
                        newText
                    )
                )
            )
        )
        colorizeAsync(action.startLine, action.startChar, endLine, endChar, version, false)

        undoStack.addLast(action)
        onSelectionChanged?.invoke(action.caretLine, action.caretLine, action.caretChar, action.caretChar)
    }
}

fun mergeHighlight(
    old: List<TextLine>,
    light: List<TextLine>,
    selectionStartLine: Int,
    selectionEndLine: Int,
): List<TextLine> {
    val result = MutableList(light.size) { index ->
        val lineDelta = light.size - old.size

        val oldLine: TextLine?
        val newLine: TextLine?
        if (lineDelta >= 0) {
            oldLine = if (index - selectionEndLine >= 0) old.getOrNull(index - lineDelta) else old.getOrNull(index)
            newLine = light.getOrNull(index) ?: TextLine(emptyList())
        } else {
            oldLine = if (index - selectionStartLine >= 0) old.getOrNull(index - lineDelta) else old.getOrNull(index)
            newLine = light.getOrNull(index) ?: TextLine(emptyList())
        }

        return@MutableList when {
            oldLine == null -> newLine
            oldLine.text == newLine.text -> oldLine
            else -> mergeLineHighlights(oldLine, newLine)
        }
    }
    return result
}

private fun mergeLineHighlights(old: TextLine, new: TextLine): TextLine {
    val mergedSpans = mutableListOf<Pair<String, TextAttributes>>()
    var oldPos = 0
    var newPos = 0

    while (newPos < new.spans.size) {
        val newSpan = new.spans[newPos]
        val newText = newSpan.first
        val matchingOldSpan = findMatchingSpan(old, oldPos, newText)

        mergedSpans.add(
            if (matchingOldSpan != null) {
                oldPos += newText.length
                newText to matchingOldSpan.second
            } else {
                newSpan
            }
        )
        newPos++
    }
    return TextLine(mergedSpans)
}

private fun findMatchingSpan(
    line: TextLine,
    start: Int,
    target: String,
): Pair<String, TextAttributes>? {
    var pos = 0
    for (span in line.spans) {
        if (pos >= start) {
            if (span.first.startsWith(target)) {
                return span
            }
        }
        pos += span.first.length
    }
    return null
}