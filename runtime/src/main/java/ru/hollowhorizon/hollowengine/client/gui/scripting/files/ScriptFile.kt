package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.math.Easing
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.logE
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.HighlightTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorState
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.TextSource
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.UiPreviewState
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.StartScriptPacket
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.generated.Assets

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

        val showDiagnostics = remember(false)
        val diagnosticsHeight = remember(180f)
        val diagnostics = editorState.analysis.diagnostics

        Column(Grow.Std, Grow.Std) {
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
                    modifier.errors.addAll(diagnostics)

                    modifier.completions.clear()
                    modifier.completions.addAll(editorState.analysis.completions)

                    installSelectionHandler(editorState.provider) { startLine, caretLine, startChar, caretChar ->
                    }

                    modifier.editorHandler(editorState.editor)
                }
                DiagnosticsCounterBar(diagnostics) {
                    showDiagnostics.set(true)
                }
            }

            if (showDiagnostics.use()) {
                DiagnosticsSplitter(diagnosticsHeight)
                DiagnosticsPanel(diagnostics, diagnosticsHeight) {
                    showDiagnostics.set(false)
                }
            }
        }
    }

    private fun UiScope.DiagnosticsCounterBar(
        diagnostics: List<Diagnostic>,
        onOpen: () -> Unit,
    ) = Row {
        modifier.align(AlignmentX.End, AlignmentY.Top)
            .margin(end = sizes.smallGap, top = sizes.smallGap)
            .zLayer(5)

        val errors = diagnostics.count { it.severity.isError() }
        val warnings = diagnostics.count { it.severity == Severity.WARNING }

        if (errors > 0) {
            DiagnosticCounter("hollowengine:textures/gui/icons/error.png", errors, onOpen)
        }
        if (warnings > 0) {
            DiagnosticCounter("hollowengine:textures/gui/icons/warn.png", warnings, onOpen)
        }
    }

    private fun UiScope.DiagnosticCounter(
        icon: String,
        count: Int,
        onOpen: () -> Unit,
    ) = Row {
        val isHovered by modifier.hoverable()
        val color by animateColorAsState(
            if (!isHovered) Color.BLACK.withAlpha(0f) else Color.GRAY.withAlpha(0.5f),
            tween(easing = Easing.easeOutQuart)
        )

        modifier.background(RoundRectBackground(color, sizes.smallGap))
            .padding(vertical = sizes.smallGap * 0.5f, horizontal = sizes.smallGap)
            .onClick { onOpen() }

        Image(icon) {
            modifier.size(18.dp - sizes.smallGap * 0.5f, 18.dp - sizes.smallGap * 0.5f)
                .alignY(AlignmentY.Center)
        }
        Text(count.toString()) {
            modifier.font(editorState.provider.font)
                .margin(horizontal = sizes.smallGap)
        }
    }

    private fun UiScope.DiagnosticsSplitter(height: MutableStateValue<Float>) = Box(Grow.Std, sizes.smallGap) {
        val isHovered by modifier.hoverable()
        val color by animateColorAsState(
            if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
            tween(easing = Easing.easeOutQuart)
        )

        modifier
            .backgroundColor(color)
            .onDrag { event ->
                height.set((height.value - Dp.fromPx(event.pointer.delta.y).value).coerceIn(96f, 360f))
            }
    }

    private fun UiScope.DiagnosticsPanel(
        diagnostics: List<Diagnostic>,
        height: MutableStateValue<Float>,
        onClose: () -> Unit,
    ) = Box(Grow.Std, height.use().dp) {
        modifier
            .backgroundColor(ColorTheme.UI.BackgroundSecondary)
            .border(RectBorder(ColorTheme.UI.BackgroundAccent.withAlpha(0.35f), sizes.smallGap * 0.25f))
            .padding(sizes.smallGap)

        Column(Grow.Std, Grow.Std) {
            Row(Grow.Std, FitContent) {
                modifier.margin(bottom = sizes.smallGap)

                Text("Problems") {
                    modifier.font(editorState.provider.font.derive(16f))
                        .textColor(EditorTheme.Popup.textPrimary)
                        .alignY(AlignmentY.Center)
                        .width(Grow.Std)
                }
                Text("x") {
                    val isHovered by modifier.hoverable()
                    modifier.font(editorState.provider.font.derive(16f))
                        .textColor(if (isHovered) Color.WHITE else EditorTheme.Popup.textDim)
                        .padding(horizontal = sizes.smallGap)
                        .onClick { onClose() }
                }
            }

            DiagnosticsHeader()

            ScrollArea(
                Grow.Std,
                Grow.Std,
                withVerticalScrollbar = true,
                withHorizontalScrollbar = false,
                containerModifier = {
                    it.background(null)
                },
                vScrollbarModifier = {
                    it.width(sizes.smallGap)
                        .colors(
                            trackColor = EditorTheme.Scrollbar.trackColor,
                            trackHoverColor = EditorTheme.Scrollbar.trackHover,
                            color = EditorTheme.Scrollbar.color,
                            hoverColor = EditorTheme.Scrollbar.hoverColor,
                        )
                },
            ) {
                Column(Grow.Std, Grow.MinFit) {
                    diagnostics
                        .sortedWith(compareBy<Diagnostic> { it.range.start.line }.thenBy { it.range.start.column })
                        .forEach { diagnostic ->
                            DiagnosticRow(diagnostic)
                        }
                }
            }
        }
    }

    private fun UiScope.DiagnosticsHeader() = Row(Grow.Std, FitContent) {
        modifier
            .backgroundColor(ColorTheme.UI.BackgroundElements.withAlpha(0.7f))
            .padding(vertical = sizes.smallGap * 0.5f, horizontal = sizes.smallGap)

        DiagnosticsCell("Severity", 100.dp, EditorTheme.Popup.textDim)
        DiagnosticsCell("Line", 80.dp, EditorTheme.Popup.textDim)
        DiagnosticsCell("Description", Grow.Std, EditorTheme.Popup.textDim)
    }

    private fun UiScope.DiagnosticRow(diagnostic: Diagnostic) = Row(Grow.Std, FitContent) {
        val isHovered by modifier.hoverable()
        val bgColor by animateColorAsState(
            if (isHovered) ColorTheme.UI.BackgroundElements else Color.BLACK.withAlpha(0f),
            tween(easing = Easing.easeOutQuart)
        )

        modifier
            .backgroundColor(bgColor)
            .padding(vertical = sizes.smallGap * 0.5f, horizontal = sizes.smallGap)
            .onClick {
                val line = diagnostic.range.start.line.coerceIn(0, editorState.provider.lastIndex.coerceAtLeast(0))
                val column = diagnostic.range.start.column.coerceIn(0, editorState.provider[line].length)
                this@ScriptFile.modifier.setSelectionRange(line, line, column, column)
            }

        val lineText = "${diagnostic.range.start.line + 1}:${diagnostic.range.start.column + 1}"
        DiagnosticsCell(diagnostic.severity.name.lowercase(), 100.dp, diagnostic.severityColor())
        DiagnosticsCell(lineText, 80.dp, EditorTheme.Popup.textPrimary)
        DiagnosticsCell(diagnostic.message, Grow.Std, EditorTheme.Popup.textPrimary)
    }

    private fun UiScope.DiagnosticsCell(
        text: String,
        width: Dimension,
        color: Color,
    ) = Text(text) {
        modifier
            .font(editorState.provider.font.derive(14f))
            .textColor(color)
            .width(width)
            .alignY(AlignmentY.Center)
    }

    private fun Diagnostic.severityColor(): Color {
        return when {
            severity.isError() -> HighlightTheme.ERROR_ELEMENT
            severity == Severity.WARNING -> HighlightTheme.KEYWORD.mix(HighlightTheme.ANNOTATION, 0.5f)
            else -> EditorTheme.Popup.textDim
        }
    }

    override fun UiScope.drawHeaderRight(color: Color) {
        if (filePath.endsWith(".ui")) {
            HeaderActionButton(Assets.Hollowengine.Textures.Gui.Icons.CODE_EDITOR) {
                save()
                UiPreviewState.previewPath.set(filePath)
                LayoutLoader.LAYOUTS["hollowengine.gui.ide.ui_preview"]?.open()
            }
            return
        }

        Box {
            val isHovered by modifier.hoverable()
            val color by animateColorAsState(
                if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary,
                tween(easing = Easing.easeOutQuart)
            )

            modifier.padding(Dimensions.PaddingNormal)
                .margin(end = Dimensions.PaddingNormal)
                .background(RoundRectBackground(color, Dimensions.PaddingSmall))
                .onClick {
                    StartScriptPacket(filePath).send()
                }

            Image(Assets.Hollowengine.Textures.Gui.Icons.PLAY) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge).alignY(AlignmentY.Center)
            }
        }
    }

    override fun onKeyInput(event: KeyEvent) {
        if (!this::modifier.isInitialized || event.isConsumed) return
        modifier.onKeyEvent?.invoke(event)
    }

    private fun UiScope.HeaderActionButton(icon: ResourceLocation, action: () -> Unit) {
        Box {
            val isHovered by modifier.hoverable()
            val color by animateColorAsState(
                if (isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary,
                tween(easing = Easing.easeOutQuart)
            )

            modifier.padding(Dimensions.PaddingNormal)
                .margin(end = Dimensions.PaddingNormal)
                .background(RoundRectBackground(color, Dimensions.PaddingSmall))
                .onClick { action() }

            Image(icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge).alignY(AlignmentY.Center)
            }
        }
    }

    override fun SubMenuItem<Dockable>.createMenu() {
        item("Format", icons.ICON_41) {
            try {
                val original = editorState.provider.lines.joinToString("\n") { it.text }
                val new = original //TODO Formatter.format(KOTLINLANG_FORMAT, original)
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
        if (!isDisposed) {
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
