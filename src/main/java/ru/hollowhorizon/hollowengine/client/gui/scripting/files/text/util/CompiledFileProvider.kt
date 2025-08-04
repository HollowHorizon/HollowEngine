package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.eclipse.lsp4j.*
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.KotlinLanguageServer
import ru.hollowhorizon.hollowengine.common.project.kt.completion.completions
import ru.hollowhorizon.hollowengine.common.project.kt.position.offset
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.ScriptColorizer
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class CompiledFileProvider(
    val file: File,
    val completionProvider: (CompletionList) -> Unit,
    val errorsProvider: (Diagnostics) -> Unit,
) : TextLineProvider, TextEditorHandler {
    private val sp = KotlinLanguageServer.sourcePath
    private var lock = ReentrantLock()

    init {
        KotlinLanguageServer.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(
                    file.path, "kotlin", 0, sp.contentProvider.contentOf(file)
                )
            )
        )
        colorizeAsync(0, 0, 0, 0, 0)
    }

    private var compiledFile: CompiledFile = recover(Position(0, 0), Recompile.NEVER).first
    val font = MsdfFont(HACK_FONT, 18f)
    private val lines = compiledFile.content.lines().map {
        TextLine(listOf(it to TextAttributes(font, Color.WHITE)))
    }.toMutableList()
    private var version = 0
    var isRecompiling = false
        private set

    private enum class Recompile {
        ALWAYS, AFTER_DOT, NEVER
    }

    private fun recover(position: Position, recompile: Recompile): Pair<CompiledFile, Int> {
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

    override fun insertText(line: Int, caret: Int, insertion: String, textAreaScope: ScriptTextAreaScope): Vec2i {
        return replaceText(line, line, caret, caret, insertion, textAreaScope)
    }

    override fun replaceText(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        replacement: String,
        textAreaScope: ScriptTextAreaScope,
    ): Vec2i {
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
        isRecompiling = true
        recolorize(selectionStartLine, selectionEndLine, selectionStartChar, selectionEndChar, true)
        KotlinLanguageServer.textDocumentService.apply {
            debounceHighlight.schedule {
                colorizeAsync(
                    selectionStartLine,
                    selectionStartChar,
                    selectionEndLine,
                    selectionEndChar,
                    textVersion,
                ).thenAcceptAsync { cursor ->
                    if (cursor == -1 || replacement.length != 1 || !(replacement[0].isLetterOrDigit() || replacement[0] == '.')) return@thenAcceptAsync
                    val completions = completions(compiledFile, cursor + 1, sp.index, config.completion)
                    completionProvider(completions)
                }
            }
        }
        return when {
            replacement.isEmpty() -> Vec2i(selectionStartChar, selectionStartLine)
            !replacement.contains('\n') -> Vec2i(selectionStartChar + replacement.length, selectionStartLine)
            else -> {
                val lines = replacement.lines()
                Vec2i(lines.last().length, selectionStartLine + lines.size - 1)
            }
        }
    }

    private fun colorizeAsync(
        selectionStartLine: Int,
        selectionStartChar: Int,
        selectionEndLine: Int,
        selectionEndChar: Int,
        textVersion: Int,
    ): CompletableFuture<Int> {
        return KotlinLanguageServer.textDocumentService.async.compute {
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

            if (compiledFile.compile[BindingContext.SCRIPT, compiledFile.parse.script] == null) {
                HollowCore.LOGGER.warn("Somehow script compilation failed... Trying again...")
                sp.refresh()
                compiledFile = sp.currentVersion(file)
                HollowCore.LOGGER.info(
                    "Compiled script: {}", compiledFile.compile[BindingContext.SCRIPT, compiledFile.parse.script]
                )
            }

            cursor
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
            source.parsed?.let {
                var highlight = it.findElementAt(offset(source.content, selectionStartLine, selectionStartChar))
                if (highlight == null || highlight is PsiWhiteSpace) highlight =
                    it.findElementAt(offset(source.content, selectionStartLine, selectionStartChar) - 1)
                val changed = ScriptColorizer.colorize(
                    it, source.compiledContext ?: BindingContext.EMPTY, highlight
                )
                if (light) {
                    val lines = mergeHighlight(
                        lines, changed, selectionStartLine, selectionEndLine, selectionStartChar, selectionEndChar
                    )
                    this.lines.clear()
                    this.lines.addAll(lines)
                } else {
                    lines.clear()
                    lines.addAll(changed)
                    source.compiledContext?.let { errorsProvider(it.diagnostics) }
                }
                if (lines.isEmpty()) lines.add(TextLine(listOf("" to TextAttributes(font, Color.WHITE))))
                HollowCore.LOGGER.info("Recolored. Version: $version")
            }
        }
    }
}

fun mergeHighlight(
    old: List<TextLine>,
    light: List<TextLine>,
    selectionStartLine: Int,
    selectionEndLine: Int,
    selectionStartChar: Int,
    selectionEndChar: Int,
): List<TextLine> {
    val result = MutableList(light.size) { index ->
        val inChanged = index in selectionStartLine..selectionEndLine
        val lineDelta = light.size - old.size

        val oldLine: TextLine?
        val newLine: TextLine?
        if (lineDelta >= 0) {
            oldLine = if (index - selectionEndLine >= 2) old.getOrNull(index - lineDelta) else old.getOrNull(index)
            newLine = light.getOrNull(index) ?: TextLine(emptyList())
        } else {
            oldLine = if (index - selectionStartLine >= 1) old.getOrNull(index - lineDelta) else old.getOrNull(index)
            newLine = light.getOrNull(index) ?: TextLine(emptyList())
        }

        return@MutableList when {
            inChanged && light.size > old.size -> newLine
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

        // Проверяем совпадает ли span со старым
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
