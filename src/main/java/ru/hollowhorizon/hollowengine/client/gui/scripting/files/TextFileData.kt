package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.Formatter.KOTLINLANG_FORMAT
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logE
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.offset
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity

class TextFileData(name: String, path: String) :
    FileData(name, path) {

    constructor(path: String, ignored: ByteArray) : this(path.substringAfterLast('/'), path)

    lateinit var modifier: ScriptTextAreaModifier
    lateinit var area: ScriptTextAreaScope

    override fun save() {
    }

    private val provider = CompiledFileProvider(filePath.fromReadablePath(), {
        modifier.errors.clear()
        modifier.errors.addAll(it)
    }) {
        modifier.completions.clear()
        modifier.completions.addAll(it)
    }

    override fun UiScope.compose() {
        modifier.background(RoundRectBackground(EditorTheme.bg, sizes.smallGap))

        Box(Grow.Std, Grow.Std) {
            ScriptTextArea(
                provider,
                vScrollbarModifier = {
                    it.width(sizes.smallGap)
                        .colors(
                            trackColor = EditorTheme.Scrollbar.trackColor,
                            trackHoverColor = EditorTheme.Scrollbar.trackHover,
                            color = EditorTheme.Scrollbar.color,
                            hoverColor = EditorTheme.Scrollbar.hoverColor,
                        )
                        .margin(sizes.smallGap)
                },
                hScrollbarModifier = {
                    it.height(sizes.smallGap).margin(sizes.smallGap)
                        .colors(
                            trackColor = EditorTheme.Scrollbar.trackColor,
                            trackHoverColor = EditorTheme.Scrollbar.trackHover,
                            color = EditorTheme.Scrollbar.color,
                            hoverColor = EditorTheme.Scrollbar.hoverColor,
                        )
                },
            ) {
                this@TextFileData.modifier = modifier
                this@TextFileData.area = this
                installSelectionHandler(provider) { startLine, caretLine, startChar, caretChar ->
                    provider.lines.clear()
                    val code = ScriptingEnvironment.INSTANCE.analyzer.highlight(
                        name,
                        provider.currentText,
                        offset(provider.currentText, startLine, startChar)
                    )
                    provider.lines.addAll(code.map { it.toKool(provider.font) })
                }

                modifier.editorHandler(provider)
            }
            Row {
                modifier.align(AlignmentX.End, AlignmentY.Top)
                    .margin(end = sizes.smallGap * 2f)
                    .zLayer(5)

                val errors = this@TextFileData.modifier.errors.count { it.severity == Severity.ERROR }
                val warnings = this@TextFileData.modifier.errors.count { it.severity == Severity.WARNING }

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