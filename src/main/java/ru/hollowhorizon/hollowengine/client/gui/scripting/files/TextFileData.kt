package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.Formatter.KOTLINLANG_FORMAT
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logE
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath


class TextFileData(name: String, path: String, code: String) :
    FileData(name, path) {

    constructor(path: String, code: ByteArray) : this(path.substringAfterLast('/'), path, String(code))

    lateinit var modifier: ScriptTextAreaModifier
    lateinit var area: ScriptTextAreaScope

    fun setText(text: String) {
        surface?.triggerUpdate()
    }

    override fun save() {
    }

    private val provider = CompiledFileProvider(filePath.fromReadablePath())

    override fun UiScope.compose() {
        modifier.background(RoundRectBackground(colors.backgroundVariant, sizes.smallGap))

        Box(Grow.Std, Grow.Std) {
            ScriptTextArea(
                provider,
                vScrollbarModifier = { it.width(sizes.smallGap).margin(end = sizes.smallGap) },
                hScrollbarModifier = { it.height(sizes.smallGap).margin(bottom = sizes.smallGap) },
            ) {
                this@TextFileData.modifier = modifier
                this@TextFileData.area = this
                installSelectionHandler(provider) { startLine, caretLine, startChar, caretChar ->
                    modifier.completions.clear()
                    // if (!provider.isRecompiling) provider.recolorize(startLine, caretLine, startChar, caretChar, false)
                }

                modifier.editorHandler(provider)
            }
            Row {
                modifier.align(AlignmentX.End, AlignmentY.Top)
                    .margin(end = sizes.smallGap * 2f)
                    .zLayer(5)

                val errors = 0 //this@TextFileData.modifier.errors.count { it.severity == DiagnosticSeverity.Error }
                val warnings = 0 // this@TextFileData.modifier.errors.count { it.severity == DiagnosticSeverity.Warning }

                if (errors > 0) {
                    Row {
                        val color = hoverColors(0.5f, Color.BLACK.withAlpha(0f), Color.GRAY.withAlpha(0.5f))
                        modifier.background(RoundRectBackground(color, sizes.smallGap))
                            .padding(vertical = sizes.smallGap * 0.5f, horizontal = sizes.smallGap)

                        Image("hollowengine:textures/gui/icons/error.png") {
                            modifier.size(18.dp - sizes.smallGap * 0.5f, 18.dp - sizes.smallGap * 0.5f)
                                .alignY(AlignmentY.Center)
                        }
                        Text(errors.toString()) {
                            modifier.font(provider.font)
                                .margin(horizontal = sizes.smallGap)
                        }
                    }
                }
                if (warnings > 0) {
                    Row {
                        val color = hoverColors(0.5f, Color.BLACK.withAlpha(0f), Color.GRAY.withAlpha(0.5f))
                        modifier.background(RoundRectBackground(color, sizes.smallGap))
                            .padding(vertical = sizes.smallGap * 0.5f, horizontal = sizes.smallGap)

                        Image("hollowengine:textures/gui/icons/warn.png") {
                            modifier.size(18.dp - sizes.smallGap * 0.5f, 18.dp - sizes.smallGap * 0.5f)
                                .alignY(AlignmentY.Center)
                        }
                        Text(warnings.toString()) {
                            modifier.font(provider.font)
                                .margin(horizontal = sizes.smallGap)
                        }
                    }
                }
            }
        }
    }

    override fun SubMenuItem<Dockable>.createMenu() {
        item("Форматировать", "hollowengine:textures/gui/icons/icon_41.png") {
            try {
                val original = provider.lines.joinToString("\n") { it.text }
                val new = Formatter.format(KOTLINLANG_FORMAT, original)
                if (original == new) return@item
                provider.setText(new)
                modifier.onSelectionChanged?.let { it(-1, -1, 0, 0) }
            } catch (ex: Exception) {
                logE { ex.stackTraceToString() }
            }
        }
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