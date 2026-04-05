package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.Formatter.KOTLINLANG_FORMAT
import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logE
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorState
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.TextSource
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.client.utils.offset
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity

class ScriptFile(path: String) : EditorFile(path) {

    lateinit var modifier: ScriptTextAreaModifier
        private set

    override fun save() {
        editorState.saveToDisk()
    }

    private val editorState by lazy {
        EditorState(TextSource.File(filePath.fromReadablePath()))
    }

    private var isDisposed = false

    override fun UiScope.compose() {
        modifier.background(null)

        if(!HollowEngine.compilerLoader.isLoaded) {
            Text("hollowengine.gui.script.compiler_not_found".lang) {}
            return
        }

        Box(Grow.Std, Grow.Std) {

            ScriptTextArea(
                editorState,
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
                this@ScriptFile.modifier = modifier

                modifier.editorConfig = editorState.config

                modifier.errors.clear()
                modifier.errors.addAll(editorState.analysis.diagnostics)

                modifier.completions.clear()
                modifier.completions.addAll(editorState.analysis.completions)

                installSelectionHandler(editorState.provider) { startLine, caretLine, startChar, caretChar ->
                    editorState.provider.lines.clear()
                    val code = editorState.language.analyzer.highlight(
                        name,
                        editorState.provider.currentText,
                        offset(editorState.provider.currentText, startLine, startChar)
                    )
                    editorState.provider.lines.addAll(code.map { it.toKool(editorState.provider.font) })
                }

                modifier.editorHandler(editorState.editor)
            }
            Row {
                modifier.align(AlignmentX.End, AlignmentY.Top)
                    .margin(end = sizes.smallGap, top = sizes.smallGap)
                    .zLayer(5)

                val errors = this@ScriptFile.modifier.errors.count { it.severity == Severity.ERROR }
                val warnings = this@ScriptFile.modifier.errors.count { it.severity == Severity.WARNING }

                if (errors > 0) {
                    Row {
                        val isHovered by modifier.hoverable()
                        val color by animateColorAsState(if(!isHovered) Color.BLACK.withAlpha(0f) else Color.GRAY.withAlpha(0.5f), tween(easing = Easing.easeOutQuart))

                        modifier.background(RoundRectBackground(color, sizes.smallGap))
                            .padding(vertical = sizes.smallGap * 0.5f, horizontal = sizes.smallGap)

                        Image("hollowengine:textures/gui/icons/error.png") {
                            modifier.size(18.dp - sizes.smallGap * 0.5f, 18.dp - sizes.smallGap * 0.5f)
                                .alignY(AlignmentY.Center)
                        }
                        Text(errors.toString()) {
                            modifier.font(editorState.provider.font)
                                .margin(horizontal = sizes.smallGap)
                        }
                    }
                }
                if (warnings > 0) {
                    Row {
                        val isHovered by modifier.hoverable()
                        val color by animateColorAsState(if(!isHovered) Color.BLACK.withAlpha(0f) else Color.GRAY.withAlpha(0.5f), tween(easing = Easing.easeOutQuart))
                        modifier.background(RoundRectBackground(color, sizes.smallGap))
                            .padding(vertical = sizes.smallGap * 0.5f, horizontal = sizes.smallGap)

                        Image("hollowengine:textures/gui/icons/warn.png") {
                            modifier.size(18.dp - sizes.smallGap * 0.5f, 18.dp - sizes.smallGap * 0.5f)
                                .alignY(AlignmentY.Center)
                        }
                        Text(warnings.toString()) {
                            modifier.font(editorState.provider.font)
                                .margin(horizontal = sizes.smallGap)
                        }
                    }
                }
            }
        }
    }

    override fun SubMenuItem<Dockable>.createMenu() {
        item("Format", icons.ICON_41) {
            try {
                val original = editorState.provider.lines.joinToString("\n") { it.text }
                val new = Formatter.format(KOTLINLANG_FORMAT, original)
                if (original == new) return@item
                editorState.provider.setText(new)
                modifier.onSelectionChanged?.let { it(-1, -1, 0, 0) }
            } catch (ex: IllegalStateException) {
                logE { "Formatting error: ${ex.message}" }
            } catch (ex: Exception) {
                logE { "Unexpected formatting error: ${ex.stackTraceToString()}" }
            }
        }
    }

    override fun close() {
        super.close()
        if (!isDisposed && HollowEngine.compilerLoader.isLoaded) {
            isDisposed = true
            editorState.dispose()
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
        // Validate selection bounds
        if (startLine < 0 || startLine >= provider.size) return@handler
        if (caretLine < 0 || caretLine >= provider.size) return@handler
        
        val start = provider[startLine]
        if (startChar < 0 || startChar > start.length) return@handler
        
        val caret = provider[caretLine]
        if (caretChar < 0 || caretChar > caret.length) return@handler

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