@file:OptIn(ExperimentalContracts::class)

package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.HighlightTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.ApplyCompletionItemCommand
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import kotlin.contracts.ExperimentalContracts

internal val bracketPairs = mapOf(
    '(' to ')', '[' to ']', '{' to '}', '<' to '>', '"' to '"', '\'' to '\''
)

interface ScriptTextAreaScope : UiScope {
    override val modifier: ScriptTextAreaModifier

    val linesHolder: LazyListScope

    fun installDefaultSelectionHandler() {
        val selStartLine = remember(-1)
        val selCaretLine = remember(-1)
        val selStartChar = remember(0)
        val selCaretChar = remember(0)

        modifier.onSelectionChanged = { startLine, caretLine, startChar, caretChar ->
            selStartLine.set(startLine)
            selCaretLine.set(caretLine)
            selStartChar.set(startChar)
            selCaretChar.set(caretChar)
        }
        modifier.selectionStartLine = selStartLine.use()
        modifier.selectionCaretLine = selCaretLine.use()
        modifier.selectionStartChar = selStartChar.use()
        modifier.selectionCaretChar = selCaretChar.use()
    }
}

open class ScriptTextAreaModifier(surface: UiSurface) : UiModifier(surface) {
    var lineStartPadding: Dp by property(Dp(0f))
    var lineEndPadding: Dp by property(Dp(100f))
    var firstLineTopPadding: Dp by property(Dp(0f))
    var lastLineBottomPadding: Dp by property(Dp(16f))

    var editorHandler: TextEditorHandler? by property(null)

    var selectionStartLine: Int by property(-1)
    var selectionCaretLine: Int by property(-1)
    var selectionStartChar: Int by property(0)
    var selectionCaretChar: Int by property(0)
    var onSelectionChanged: ((Int, Int, Int, Int) -> Unit)? by property(null)

    val completions by property(mutableListOf<CompletionItem>())
    val errors by property(mutableListOf<Diagnostic>())

    var errorMessage: String by property("")
}

fun <T : ScriptTextAreaModifier> T.lineStartPadding(padding: Dp): T {
    lineStartPadding = padding; return this
}

fun <T : ScriptTextAreaModifier> T.lineEndPadding(padding: Dp): T {
    lineEndPadding = padding; return this
}

fun <T : ScriptTextAreaModifier> T.firstLineTopPadding(padding: Dp): T {
    firstLineTopPadding = padding; return this
}

fun <T : ScriptTextAreaModifier> T.lastLineBottomPadding(padding: Dp): T {
    lastLineBottomPadding = padding; return this
}

fun <T : ScriptTextAreaModifier> T.onSelectionChanged(block: ((Int, Int, Int, Int) -> Unit)?): T {
    onSelectionChanged = block
    return this
}

fun <T : ScriptTextAreaModifier> T.editorHandler(handler: TextEditorHandler): T {
    editorHandler = handler; return this
}

fun <T : ScriptTextAreaModifier> T.setCaretPos(line: Int, caretPos: Int): T {
    selectionStartLine = line
    selectionCaretLine = line
    selectionStartChar = caretPos
    selectionCaretChar = caretPos
    onSelectionChanged?.invoke(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
    return this
}

fun <T : ScriptTextAreaModifier> T.setSelectionRange(startLine: Int, caretLine: Int, startPos: Int, caretPos: Int): T {
    selectionStartLine = startLine
    selectionCaretLine = caretLine
    selectionStartChar = startPos
    selectionCaretChar = caretPos
    onSelectionChanged?.invoke(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
    return this
}

@OptIn(ExperimentalContracts::class)
fun UiScope.ScriptTextArea(
    lineProvider: TextLineProvider,
    width: Dimension = Grow.Std,
    height: Dimension = Grow.Std,
    withVerticalScrollbar: Boolean = true,
    withHorizontalScrollbar: Boolean = true,
    scrollbarColor: Color? = null,
    scrollPaneModifier: ((ScrollPaneModifier) -> Unit)? = null,
    vScrollbarModifier: ((ScrollbarModifier) -> Unit)? = null,
    hScrollbarModifier: ((ScrollbarModifier) -> Unit)? = null,
    state: LazyListState = rememberListState(),
    scopeName: String? = null,
    block: ScriptTextAreaScope.() -> Unit,
) {

    val textArea = uiNode.createChild(scopeName, TextAreaNode::class, TextAreaNode.factory)
    textArea.listState = state
    textArea.modifier.size(width, height)
        .onWheelX { state.scrollDpX(it.pointer.scroll.x * TextAreaConfig.SCROLL_WHEEL_X_MULTIPLIER) }
        .onWheelY { state.scrollDpY(it.pointer.scroll.y * TextAreaConfig.SCROLL_WHEEL_Y_MULTIPLIER) }

    textArea.completionIndex = remember(0)
    textArea.completionX = remember(0f)
    textArea.completionY = remember(0f)

    textArea.lineProvider = lineProvider
    textArea.setupContent(
        lineProvider,
        withVerticalScrollbar,
        withHorizontalScrollbar,
        scrollbarColor,
        scrollPaneModifier,
        vScrollbarModifier,
        hScrollbarModifier,
        afterContent = {
            val handler = textArea.modifier.editorHandler
            val provider = handler as? CompiledFileProvider
            val completions = provider?.analysisState?.completions ?: textArea.modifier.completions
            if (completions.isNotEmpty()) {
                Popup(textArea.completionX.use(), textArea.completionY.use()) {
                    modifier
                        .background(RoundRectBackground(EditorTheme.Popup.bg, Dimensions.PaddingMedium))
                        .border(
                            RoundRectBorder(
                                EditorTheme.Popup.border,
                                Dimensions.PaddingMedium,
                                Dimensions.PaddingSmall
                            )
                        )
                        .padding(Dimensions.PaddingSmall)
                        .height(
                            (18f.dp + Dimensions.PaddingMedium) * completions.size.coerceAtMost(TextAreaConfig.MAX_COMPLETION_ITEMS)
                        )
                        .width(Grow(1f, max = FitContent))
                        .zLayer(500)

                    LazyColumn(
                        withVerticalScrollbar = true,
                        withHorizontalScrollbar = false,
                        isScrollableHorizontal = true,
                        containerModifier = {
                            it.background(null)
                        },
                        vScrollbarModifier = {
                            it.width(sizes.smallGap).margin(sizes.smallGap * 0.5f)
                                .zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                                .colors(
                                    trackColor = EditorTheme.Scrollbar.trackColor,
                                    trackHoverColor = EditorTheme.Scrollbar.trackHover,
                                    color = EditorTheme.Scrollbar.color,
                                    hoverColor = EditorTheme.Scrollbar.hoverColor,
                                )
                        },
                        hScrollbarModifier = {
                            it.height(sizes.smallGap).margin(sizes.smallGap * 0.5f)
                                .zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                                .colors(
                                    trackColor = EditorTheme.Scrollbar.trackColor,
                                    trackHoverColor = EditorTheme.Scrollbar.trackHover,
                                    color = EditorTheme.Scrollbar.color,
                                    hoverColor = EditorTheme.Scrollbar.hoverColor,
                                )
                        },
                        scrollPaneModifier = {
                            it.allowOverscrollY = false
                        }) {
                        modifier.margin(end = sizes.gap)

                        textArea.completionsList = (this as LazyListNode).state

                        val currentLine = lineProvider[textArea.modifier.selectionCaretLine].text
                        val currentCharIdx = textArea.modifier.selectionCaretChar
                        val prefixStart = TextCaretNavigation.startOfExpression(currentLine, currentCharIdx)
                        val typedPrefix = if (prefixStart != -1 && prefixStart < currentCharIdx) {
                            currentLine.substring(prefixStart, currentCharIdx)
                        } else ""

                        itemsIndexed(completions) { index, completion ->
                            CompletionRenderer.renderCompletion(
                                completion,
                                textArea.completionIndex.use() == index,
                                typedPrefix
                            ) { textArea.applyCompletion(it) }
                        }
                    }
                }
            }
        }) {
        this.block()

        val currentError = textArea.modifier.errorMessage
        if (currentError.isNotEmpty()) {
            surface.triggerUpdate()
            val pos = PointerInput.primaryPointer.pos
            Popup(pos.x, pos.y) {
                modifier.background(UiRenderer { node ->
                    node.apply {
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRoundRect(
                            0f, 0f, widthPx, heightPx, sizes.smallGap.px, colors.background
                        )
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRoundRectBorder(
                            0f, 0f, widthPx, heightPx, sizes.smallGap.px, sizes.borderWidth.px, colors.primaryVariant
                        )
                    }
                }).zLayer(300)

                modifier.width(Grow(1f, max = FitContent))

                Text(currentError) {
                    modifier.margin(sizes.smallGap).isWrapText(true).width(Grow.Std)
                }
            }
        }

        textArea.modifier.errorMessage = ""
    }
}

open class TextAreaNode(parent: UiNode?, surface: UiSurface) : BoxNode(parent, surface), ScriptTextAreaScope,
    Focusable {
    override val modifier = ScriptTextAreaModifier(surface)
    override val isFocused = mutableStateOf(false)

    lateinit var lineProvider: TextLineProvider
    lateinit var completionsList: LazyListState

    lateinit var listState: LazyListState
    override lateinit var linesHolder: LazyListNode

    lateinit var completionX: MutableStateValue<Float>
    lateinit var completionY: MutableStateValue<Float>
    lateinit var completionIndex: MutableStateValue<Int>

    private val selectionController = TextSelectionController(
        owner = this,
        modifier = modifier,
        lineProvider = { lineProvider },
        linesHolder = { linesHolder },
        requestFocus = { requestFocus() },
        isFocused = { isFocused.use() }
    )

    private val completionManager = CompletionManager(modifier, { completionIndex }) { if (this::completionsList.isInitialized) completionsList else null }
    private val inputController = TextInputController(
        modifier = modifier,
        selectionController = selectionController,
        lineProvider = { lineProvider },
        requestFocusNone = { surface.requestFocus(null) },
        completionManager = completionManager,
    )

    private inner class LineItem(parent: UiNode?, surface: UiSurface) : RowNode(parent, surface) {
        var lineIndex = -1
        lateinit var indents: IntArray

        val font = MsdfFont(ColorTheme.Fonts.MONOCRAFT, 18f)

        override fun render(ctx: KoolContext) {
            renderCurrentLineBackground()
            super.render(ctx)
            renderDiagnostics()
            renderIndentGuides()
        }

        private fun renderCurrentLineBackground() {
            if (lineIndex == this@TextAreaNode.modifier.selectionCaretLine && isFocused.use()) {
                getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRect(
                    0f, 0f, widthPx, heightPx, EditorTheme.currentLineBg
                )
            }
        }

        private fun renderDiagnostics() {
            val maxWidth = font.textDimensions(lineProvider.size.toString()).width.dp + sizes.smallGap * 2f
            val handler = this@TextAreaNode.modifier.editorHandler
            val provider = handler as? CompiledFileProvider
            val errors = provider?.analysisState?.diagnostics ?: this@TextAreaNode.modifier.errors

            errors.filter { lineIndex in it.range.start.line..it.range.end.line }
                .forEach { error ->
                    val text = lineProvider[lineIndex].text
                    val (startPos, endPos) = if (text.isEmpty()) {
                        0f to widthPx
                    } else {
                        val startIdx = error.range.start.column.coerceIn(0, text.length)
                        val endIdx = error.range.end.column.coerceIn(0, text.length)
                        val start = font.textDimensions(text.substring(0, startIdx)).width.dp.px
                        val end = font.textDimensions(text.substring(0, endIdx)).width.dp.px
                        start to end
                    }

                    val color =
                        if (error.severity.isError()) HighlightTheme.ERROR_ELEMENT
                        else HighlightTheme.KEYWORD.mix(HighlightTheme.ANNOTATION, 0.5f)

                    getPlainBuilder(UiSurface.LAYER_FLOATING).configured(color, clipped = false) {
                        val leftPos = maxWidth.px + Dimensions.PaddingHuge.px

                        val rangeEnd = clipBoundsPx.z - leftPx - TextAreaConfig.SQUIGGLY_AMPLITUDE.toFloat()
                        for (i in ((leftPos + startPos).toInt()..(leftPos + endPos).coerceAtMost(rangeEnd)
                            .toInt()).step(
                            TextAreaConfig.SQUIGGLY_STEP
                        )) {
                            val offset =
                                if (i % 2 == 0) TextAreaConfig.SQUIGGLY_AMPLITUDE else -TextAreaConfig.SQUIGGLY_AMPLITUDE
                            line(
                                Vec2f(i.toFloat(), heightPx + offset),
                                Vec2f(
                                    i + TextAreaConfig.SQUIGGLY_STEP.toFloat(),
                                    heightPx - offset
                                ),
                                TextAreaConfig.SQUIGGLY_LINE_WIDTH
                            )
                        }

                        val mouse = PointerInput.primaryPointer
                        val fromX = leftPx + leftPos + startPos
                        val toX = leftPx + leftPos + endPos
                        if (mouse.pos.x in fromX..toX && mouse.pos.y in topPx..bottomPx) {
                            this@TextAreaNode.modifier.errorMessage = error.message
                        }
                    }
                }
        }

        private fun renderIndentGuides() {
            val textNode = children.getOrNull(2)
            if (indents.isEmpty() || textNode == null) return

            val spaceWidth = font.charWidth(' ').dp.px
            val guideStartX = textNode.leftPx - this.leftPx + textNode.paddingStartPx

            for (i in indents) {
                val x =
                    guideStartX + i * spaceWidth - sizes.smallGap.px * TextAreaConfig.INDENT_GUIDE_OFFSET
                val color =
                    if (selectionController.selectionCaretChar == i && lineIndex == this@TextAreaNode.modifier.selectionCaretLine)
                        EditorTheme.indentGuide.withAlpha(TextAreaConfig.INDENT_GUIDE_ACTIVE_ALPHA)
                    else
                        EditorTheme.indentGuide.withAlpha(TextAreaConfig.INDENT_GUIDE_INACTIVE_ALPHA)

                getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                    .localRect(x, 0f, Dimensions.PaddingSmall.px, heightPx, color)
            }
        }
    }

    private val lineItemFactory: (UiNode, UiSurface) -> LineItem = { parent, surface -> LineItem(parent, surface) }

    fun setupContent(
        lineProvider: TextLineProvider,
        withVerticalScrollbar: Boolean,
        withHorizontalScrollbar: Boolean,
        scrollbarColor: Color?,
        scrollPaneModifier: ((ScrollPaneModifier) -> Unit)?,
        vScrollbarModifier: ((ScrollbarModifier) -> Unit)?,
        hScrollbarModifier: ((ScrollbarModifier) -> Unit)?,
        afterContent: ScriptTextAreaScope.() -> Unit = {},
        block: ScriptTextAreaScope.() -> Unit,
    ) {
        this.lineProvider = lineProvider
        modifier.margin(horizontal = Dimensions.PaddingNormal).margin(bottom = Dimensions.PaddingNormal)
            .padding(Dimensions.PaddingHuge)
            .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, sizes.smallGap))

        ScrollPane(listState) {
            modifier.width(Grow.MinFit)
            scrollPaneModifier?.let { it(modifier) }

            linesHolder = uiNode.createChild(null, LazyListNode::class, LazyListNode.factory)
            linesHolder.state = listState
            linesHolder.modifier.orientation(ListOrientation.Vertical).layout(ColumnLayout).width(Grow.MinFit)

            block.invoke(this@TextAreaNode)

            setText(lineProvider)

            afterContent.invoke(this@TextAreaNode)
        }

        if (withVerticalScrollbar) {
            VerticalScrollbar {
                lazyListAware(
                    listState,
                    ScrollbarOrientation.Vertical,
                    ListOrientation.Vertical,
                    scrollbarColor,
                    vScrollbarModifier
                )
            }
        }
        if (withHorizontalScrollbar) {
            HorizontalScrollbar {
                lazyListAware(
                    listState,
                    ScrollbarOrientation.Horizontal,
                    ListOrientation.Vertical,
                    scrollbarColor,
                    hScrollbarModifier
                )
            }
        }
    }

    private fun setText(lineProvider: TextLineProvider) {
        val textAreaMod = this@TextAreaNode.modifier

        val indentManager = IndentStackManager()
        for (i in 0..<linesHolder.state.itemsFrom.use().coerceAtMost(lineProvider.size)) {
            val line = lineProvider[i]
            val indentIndex = line.text.indexOfFirst { it != ' ' }
            indentManager.update(indentIndex, line.length)
        }
        selectionController.updateSelectionRange()
        linesHolder.indices(lineProvider.size) { lineIndex ->
            if (lineIndex >= lineProvider.size) return@indices
            val line = lineProvider[lineIndex]
            val indentIndex = line.text.indexOfFirst { it != ' ' }
            indentManager.popToIndent(indentIndex, line.length)

            val font = MsdfFont(ColorTheme.Fonts.MONOCRAFT, 18f)

            val lineItem = uiNode.createChild(null, LineItem::class, lineItemFactory)
            lineItem.lineIndex = lineIndex
            lineItem.indents = indentManager.getIndents()
            lineItem.modifier.width(Grow.Std).layout(RowLayout)
            with(lineItem) {
                val maxWidth = font.textDimensions(lineProvider.size.toString()).width.dp + Dimensions.PaddingHuge

                Box(maxWidth) {
                    modifier
                        .height(Grow.Std)
                        .alignY(AlignmentY.Center)

                    Text((lineIndex + 1).toString()) {
                        val textColor = if (lineIndex == this@TextAreaNode.modifier.selectionCaretLine)
                            EditorTheme.gutterText.mix(Color.WHITE, 0.75f)
                        else
                            EditorTheme.gutterText

                        modifier.font(font).textColor(textColor).align(AlignmentX.End, AlignmentY.Center)
                    }
                }
                Box(Dimensions.PaddingHuge) {}

                setupTextLine(line, lineIndex, textAreaMod, lineProvider).apply {
                    modifier.alignY(AlignmentY.Center)
                        .padding(vertical = Dimensions.PaddingSmall)
                }
            }

            indentManager.pushIndent(indentIndex)
        }
    }

    protected open fun UiScope.setupTextLine(
        line: ScriptTextLine,
        lineIndex: Int,
        textAreaMod: ScriptTextAreaModifier,
        lineProvider: TextLineProvider,
    ): UiScope = AttributedText(line) {
        modifier.width(Grow.Std)

        modifier.onPositioned {
            val areaModifier = this@TextAreaNode.modifier

            if (lineIndex != areaModifier.selectionStartLine) return@onPositioned

            val selectionIndex = (areaModifier.selectionStartChar - 1).coerceAtLeast(0)

            var dotIndex = TextCaretNavigation.startOfExpression(line.text, selectionIndex)
            if (dotIndex == -1) dotIndex = selectionIndex
            completionX.set(it.leftPx + line.charIndexToPx(dotIndex) + sizes.smallGap.px)

            val sizeY =
                (24.dp + sizes.smallGap).px * areaModifier.completions.size.coerceAtMost(TextAreaConfig.MAX_COMPLETION_ITEMS) + 24.dp.px
            val viewportBottom = surface.viewport.bottomPx
            if (it.bottomPx + sizeY > viewportBottom) {
                completionY.set(it.topPx - sizeY)
            } else {
                completionY.set(it.bottomPx)
            }
        }

        if (this@TextAreaNode.modifier.onSelectionChanged != null) {
            modifier.onClick {
                inputController.clearCompletions()
                when (it.pointer.leftButtonRepeatedClickCount) {
                    1 -> selectionController.onSelectStart(this, lineIndex, it, false)
                    2 -> selectionController.selectWord(this, line.text, lineIndex, it)
                    3 -> selectionController.selectLine(this, line.text, lineIndex)
                }
            }.onDragStart {
                inputController.clearCompletions()
                selectionController.onSelectStart(this, lineIndex, it, true)
            }
                .onDrag { selectionController.onDrag(it) }.onDragEnd { selectionController.onSelectEnd() }
                .onPointer { selectionController.onPointer(this, lineIndex, it) }

            selectionController.applySelectionRange(this, line, lineIndex)
        }
    }

    override fun onKeyEvent(keyEvent: KeyEvent) {
        inputController.onKeyEvent(keyEvent)
    }

    fun applyCompletion(item: CompletionItem) {
        val provider = lineProvider
        val ctx = EditorCommandContext(
            event = null,
            selection = selectionController,
            lineProvider = provider,
            inputController = inputController,
            historyManager = provider as? UndoRedoHandler ?: return,
            hasCompletions = modifier.completions.isNotEmpty(),
            completion = null,
        ).apply {
            completionItem = item
        }
        
        CommandRegistry.execute(ApplyCompletionItemCommand.Key, ctx)
        
        modifier.completions.clear()
        (modifier.editorHandler as? CompiledFileProvider)?.analysisState?.completions?.clear()
        surface.requestFocus(this)
    }

    companion object {
        val factory: (UiNode, UiSurface) -> TextAreaNode = { parent, surface -> TextAreaNode(parent, surface) }
    }
}

interface TextLineProvider {
    val size: Int
    val lastIndex: Int get() = size - 1
    operator fun get(index: Int): ScriptTextLine
}

class ListTextLineProvider(val lines: MutableList<ScriptTextLine> = mutableStateListOf()) : TextLineProvider {
    override val size: Int get() = lines.size
    override operator fun get(index: Int) = lines[index]
}

interface TextEditorHandler {
    fun insertText(line: Int, caret: Int, insertion: String): Vec2i
    fun replaceText(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        replacement: String,
    ): Vec2i

    companion object {
        val EMPTY = object : TextEditorHandler {
            override fun insertText(
                line: Int,
                caret: Int,
                insertion: String,
            ): Vec2i {
                return Vec2i(caret, line)
            }

            override fun replaceText(
                selectionStartLine: Int,
                selectionEndLine: Int,
                selectionStartChar: Int,
                selectionEndChar: Int,
                replacement: String,
            ): Vec2i {
                return Vec2i(selectionStartChar, selectionStartLine)
            }
        }
    }
}
