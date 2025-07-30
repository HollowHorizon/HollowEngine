package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.Formatter.KOTLINLANG_FORMAT
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.logE
import kotlinx.coroutines.*
import org.eclipse.lsp4j.PublishDiagnosticsParams
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.SaveFilePacket
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionVariant
import ru.hollowhorizon.hollowengine.common.scripting.core.parser.ScriptingContext
import java.net.URI
import java.util.concurrent.Future
import kotlin.coroutines.CoroutineContext


class TextFileData(name: String, path: String, code: String) :
    FileData(name, path) {
    private val lines = MutableStateList(code.lines().map {
        TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 18f), Color.WHITE)))
    }.toMutableList())

    var context: ScriptingContext? = null
    lateinit var modifier: ScriptTextAreaModifier
    lateinit var area: ScriptTextAreaScope

    fun setText(text: String) {
        lines.clear()
        lines.addAll(mutableStateListOf(*text.lines().map {
            TextLine(listOf(it to TextAttributes(MsdfFont(HACK_FONT, 18f), Color.WHITE)))
        }.toTypedArray()))
        surface.triggerUpdate()
    }

    fun onErrorsEvent(errors: PublishDiagnosticsParams) {
        modifier.errors.clear()
        modifier.errors.addAll(errors.diagnostics)
        surface.triggerUpdate()
    }

    override fun save() {
        if (filePath.startsWith("%")) return
        SaveFilePacket(filePath, lines.joinToString("\n") { it.text }.toByteArray()).send()
    }

    private val provider = CompiledFileProvider(URI.create(filePath)) {
        modifier.completions.clear()
        val completions = it.items.map { completion ->
            CompletionVariant(
                completion.insertText ?: completion.label,
                completion.label,
                completion.detail ?: "",
                CompletionVariant.Icon.fromKind(completion.kind),
                null,
                emptyList(),
                completion.additionalTextEdits?.map { it.newText } ?: emptyList(),
            )
        }
        modifier.completions += completions
    }

    override fun UiScope.compose() {
        modifier.backgroundColor(colors.backgroundVariant)

        ScriptTextArea(
            provider,
            vScrollbarModifier = { it.width(sizes.smallGap) },
            hScrollbarModifier = { it.height(sizes.smallGap) },
        ) {
            modifier.margin(vertical = sizes.smallGap)
            this@TextFileData.modifier = modifier
            this@TextFileData.area = this
            installSelectionHandler(provider) { startLine, caretLine, startChar, caretChar ->
                modifier.completions.clear()

                save()

                if(!provider.isRecompiling) provider.recolorize(startLine, caretLine, startChar, caretChar, false)
            }

            modifier.editorHandler(provider)
        }
    }

    override fun SubMenuItem<Dockable>.createMenu() {
        item("Форматировать", "hollowengine:textures/gui/icons/icon_41.png") {
            //val editorHandler = editorHandler as? ScriptTextEditorHandler

            try {
                val original = lines.joinToString("\n") { it.text }
                val new = Formatter.format(KOTLINLANG_FORMAT, original)
                if (original == new) return@item
                //editorHandler?.replaceAll(new, area)
                modifier.onSelectionChanged?.let { it(-1, -1, 0, 0) }
            } catch (ex: Exception) {
                logE { ex.stackTraceToString() }
            }
        }
    }
}

object ActionManager {
    private var currentJob: Job? = null
    private var futureTask: Future<*>? = null
    private val scope = CoroutineScope(Dispatchers.Default) + ScriptContext()

    fun launch(action: suspend () -> Unit) {
        currentJob?.cancel()
        futureTask?.cancel(true)
        currentJob = scope.launch debounce@{
            delay(300)
            if (!isActive) return@debounce

            try {
                action()
            } catch (e: Exception) {
                // Ignore
                coroutineContext[ScriptContext.Key]?.context?.close()
            }
        }
    }

    class ScriptContext(var context: ScriptingContext? = null) : CoroutineContext.Element {
        companion object Key : CoroutineContext.Key<ScriptContext>

        override val key get() = Key
    }
}

private fun ScriptTextAreaScope.installSelectionHandler(
    provider: CompiledFileProvider,
    onChange: (startLine: Int, caretLine: Int, startChar: Int, caretChar: Int) -> Unit,
) {
    val selStartLine = remember(-1)
    val selCaretLine = remember(-1)
    val selStartChar = remember(0)
    val selCaretChar = remember(0)

    modifier.onSelectionChanged = handler@{ startLine, caretLine, startChar, caretChar ->
        if (startLine >= provider.size || startLine < 0) return@handler
        if (caretLine >= provider.size) return@handler
        val start = provider[startLine]
        if (startChar > start.length) return@handler
        val caret = provider[caretLine]
        if (caretChar > caret.length) return@handler

        selStartLine.set(startLine)
        selCaretLine.set(caretLine)
        selStartChar.set(startChar)
        selCaretChar.set(caretChar)
        onChange(startLine, caretLine, startChar, caretChar)
    }
    modifier.selectionStartLine = selStartLine.use()
    modifier.selectionCaretLine = selCaretLine.use()
    modifier.selectionStartChar = selStartChar.use()
    modifier.selectionCaretChar = selCaretChar.use()
}