package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.eclipse.lsp4j.*
import org.jetbrains.kotlin.resolve.BindingContext
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.KotlinLanguageServer
import ru.hollowhorizon.hollowengine.common.project.kt.completion.completions
import ru.hollowhorizon.hollowengine.common.project.kt.position.offset
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.ScriptColorizer
import java.net.URI

class CompiledFileProvider(val file: URI, val completionProvider: (CompletionList) -> Unit) : TextLineProvider, TextEditorHandler {
    private val sp = KotlinLanguageServer.sourcePath

    init {
        KotlinLanguageServer.textDocumentService.didOpen(
            DidOpenTextDocumentParams(
                TextDocumentItem(
                    file.path,
                    "kotlin",
                    0,
                    sp.contentProvider.contentOf(file)
                )
            )
        )
    }

    private var compiledFile: CompiledFile = recover(Position(0, 0), Recompile.NEVER).first
    private val font = MsdfFont(HACK_FONT, 18f)
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
                VersionedTextDocumentIdentifier(file.path, textVersion),
                listOf(
                    TextDocumentContentChangeEvent(
                        Range(
                            Position(selectionStartLine, selectionStartChar),
                            Position(selectionEndLine, selectionEndChar)
                        ),
                        replacement
                    )
                )
            )
        )
        recolorize(selectionStartLine, selectionStartChar)
        KotlinLanguageServer.textDocumentService.apply {
            debounceHighlight.schedule {
                async.compute {
                    isRecompiling = true
                    val (newCompiledFile, cursor) = recover(Position(selectionStartLine, selectionStartChar), Recompile.ALWAYS)
                    if (textVersion != version) return@compute -1
                    compiledFile = newCompiledFile
                    recolorize(selectionStartLine, selectionStartChar)
                    isRecompiling = false
                    cursor
                }.thenAcceptAsync { cursor ->
                    if(cursor == -1 || replacement.length != 1 || !(replacement[0].isLetterOrDigit() || replacement[0] == '.')) return@thenAcceptAsync
                    val completions = completions(compiledFile, cursor, sp.index, config.completion)
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

    fun recolorize(selectionStartLine: Int, selectionStartChar: Int) {
        sp.sourceFile(file).apply { parseIfChanged() }.let { source ->
            source.parsed?.let {
                val changed = ScriptColorizer.colorize(
                    it,
                    source.compiledContext ?: BindingContext.EMPTY,
                    it.findElementAt(offset(source.content, selectionStartLine, selectionStartChar))
                )
                lines.clear()
                if(changed.isEmpty()) lines.add(TextLine(listOf("" to TextAttributes(font, Color.WHITE))))
                else lines += changed
                HollowCore.LOGGER.info("Recolored. Version: $version")
            }
        }
    }
}