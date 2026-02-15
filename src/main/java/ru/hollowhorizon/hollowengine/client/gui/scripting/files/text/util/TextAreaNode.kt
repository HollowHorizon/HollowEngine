@file:OptIn(ExperimentalContracts::class)

package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.Clipboard
import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.input.UniversalKeyCode
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
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.TextSelectionController
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import kotlin.contracts.ExperimentalContracts

/**
 * Configuration constants for the text area.
 */
private object TextAreaConfig {
    // Completion settings
    const val MAX_COMPLETION_ITEMS = 10
    const val COMPLETION_ITEM_HEIGHT = 24

    // Indentation
    const val INDENT_SIZE = 4
    const val INDENT_GUIDE_OFFSET = 0.5f
    const val INDENT_GUIDE_ACTIVE_ALPHA = 0.8f
    const val INDENT_GUIDE_INACTIVE_ALPHA = 0.3f

    // Squiggly line (error underline) rendering
    const val SQUIGGLY_STEP = 5
    const val SQUIGGLY_AMPLITUDE = 5
    const val SQUIGGLY_LINE_WIDTH = 3f

    // Scroll settings
    const val SCROLL_WHEEL_X_MULTIPLIER = -20f
    const val SCROLL_WHEEL_Y_MULTIPLIER = -50f
}

var errorMessage = ""

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

    var completionIndex by property(0)
    var completionX: Float by property(0f)
    var completionY: Float by property(0f)

    var setCompletionIndex: (Int) -> Unit by property { {} }
    var setCompletionX: (Float) -> Unit by property { {} }
    var setCompletionY: (Float) -> Unit by property { {} }
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
    textArea.modifier.size(width, height).onWheelX { state.scrollDpX(it.pointer.scroll.x * TextAreaConfig.SCROLL_WHEEL_X_MULTIPLIER) }
        .onWheelY { state.scrollDpY(it.pointer.scroll.y * TextAreaConfig.SCROLL_WHEEL_Y_MULTIPLIER) }

    var completionIndex: Int by textArea.remember(0)
    var completionX: Float by textArea.remember(0f)
    var completionY: Float by textArea.remember(0f)
    textArea.modifier.completionIndex = completionIndex
    textArea.modifier.completionX = completionX
    textArea.modifier.completionY = completionY

    textArea.modifier.setCompletionIndex = { completionIndex = it }
    textArea.modifier.setCompletionX = { completionX = it }
    textArea.modifier.setCompletionY = { completionY = it }

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
            val completions = textArea.modifier.completions
            if (completions.isNotEmpty()) {

                Popup(modifier.completionX, modifier.completionY) {
                    modifier
                        .background(RoundRectBackground(EditorTheme.Popup.bg, Dimensions.PaddingMedium))
                        .border(RoundRectBorder(EditorTheme.Popup.border, Dimensions.PaddingMedium, Dimensions.PaddingSmall))
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
                                this@setupContent.modifier.completionIndex == index,
                                typedPrefix
                            ) { textArea.applyCompletion(it) }
                        }
                    }
                }
            }
        }) {
        this.block()

        if (errorMessage.isNotEmpty()) {
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

                Text(errorMessage) {
                    modifier.margin(sizes.smallGap).isWrapText(true).width(Grow.Std)
                }
            }
        }

        errorMessage = ""
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

    private val selectionController = TextSelectionController(
        owner = this,
        modifier = modifier,
        lineProvider = { lineProvider },
        linesHolder = { linesHolder },
        requestFocus = { requestFocus() },
        isFocused = { isFocused.use() }
    )

    private inner class LineItem(parent: UiNode?, surface: UiSurface) : RowNode(parent, surface) {
        var lineIndex = -1
        lateinit var indents: IntArray

        val font = MsdfFont(ColorTheme.Fonts.MONOCRAFT, 18f)

        override fun render(ctx: KoolContext) {
            if (lineIndex == this@TextAreaNode.modifier.selectionCaretLine && isFocused.use()) {
                getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRect(
                    0f, 0f, widthPx, heightPx, EditorTheme.currentLineBg
                )
            }
            super.render(ctx)

            val maxWidth = font.textDimensions(lineProvider.size.toString()).width.dp + sizes.smallGap * 2f
            this@TextAreaNode.modifier.errors.filter { lineIndex in it.range.start.line..it.range.end.line }
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
                        
                        for (i in ((leftPos + startPos).toInt()..(leftPos + endPos).coerceAtMost(clipBoundsPx.z - leftPx - TextAreaConfig.SQUIGGLY_AMPLITUDE.toFloat()).toInt()).step(TextAreaConfig.SQUIGGLY_STEP)) {
                            val offset = if (i % 2 == 0) TextAreaConfig.SQUIGGLY_AMPLITUDE else -TextAreaConfig.SQUIGGLY_AMPLITUDE
                            line(
                                Vec2f(i.toFloat(), heightPx + offset), Vec2f(i + TextAreaConfig.SQUIGGLY_STEP.toFloat(), heightPx - offset), TextAreaConfig.SQUIGGLY_LINE_WIDTH
                            )
                        }

                        val mouse = PointerInput.primaryPointer

                        if (mouse.pos.x in leftPx+leftPos + startPos..leftPx+leftPos + endPos && mouse.pos.y in topPx..bottomPx) {
                            errorMessage = error.message
                        }
                    }
                }

            val textNode = children.getOrNull(2)
            if (indents.isNotEmpty() && textNode != null) {
                val spaceWidth = font.charWidth(' ').dp.px
                val guideStartX = textNode.leftPx - this.leftPx + textNode.paddingStartPx

                for (i in indents) {
                    val x = guideStartX + i * spaceWidth - sizes.smallGap.px * TextAreaConfig.INDENT_GUIDE_OFFSET
                    val color =
                        if (selectionController.selectionCaretChar == i && lineIndex == this@TextAreaNode.modifier.selectionCaretLine)
                            EditorTheme.indentGuide.withAlpha(TextAreaConfig.INDENT_GUIDE_ACTIVE_ALPHA)
                        else
                            EditorTheme.indentGuide.withAlpha(TextAreaConfig.INDENT_GUIDE_INACTIVE_ALPHA)

                    getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRect(x, 0f, Dimensions.PaddingSmall.px, heightPx, color)
                }
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
        modifier.margin(horizontal = Dimensions.PaddingNormal).margin(bottom=Dimensions.PaddingNormal)
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
            areaModifier.setCompletionX(it.leftPx + line.charIndexToPx(dotIndex) + sizes.smallGap.px)

            val sizeY = (24.dp + sizes.smallGap).px * areaModifier.completions.size.coerceAtMost(TextAreaConfig.MAX_COMPLETION_ITEMS) + 24.dp.px
            val viewportBottom = surface.viewport.bottomPx
            if (it.bottomPx + sizeY > viewportBottom) {
                areaModifier.setCompletionY(it.topPx - sizeY)
            } else {
                areaModifier.setCompletionY(it.bottomPx)
            }
        }

        if (this@TextAreaNode.modifier.onSelectionChanged != null) {
            modifier.onClick {
                when (it.pointer.leftButtonRepeatedClickCount) {
                    1 -> selectionController.onSelectStart(this, lineIndex, it, false)
                    2 -> selectionController.selectWord(this, line.text, lineIndex, it)
                    3 -> selectionController.selectLine(this, line.text, lineIndex)
                }
            }.onDragStart { selectionController.onSelectStart(this, lineIndex, it, true) }
                .onDrag { selectionController.onDrag(it) }.onDragEnd { selectionController.onSelectEnd() }
                .onPointer { selectionController.onPointer(this, lineIndex, it) }

            selectionController.applySelectionRange(this, line, lineIndex)
        }
    }

    override fun onKeyEvent(keyEvent: KeyEvent) {

        if (keyEvent.isCharTyped) {
            handleCharTyped(keyEvent)
        } else if (keyEvent.isPressed) {
            handleKeyPress(keyEvent)
        } else if (keyEvent.isReleased) {
            handleKeyRelease(keyEvent)
        }
    }

    private fun handleCharTyped(keyEvent: KeyEvent) {
        val char = keyEvent.typedChar.toString()
        val closing = bracketPairs[keyEvent.localKeyCode.code.toChar()]

        if (closing == null) {
            editText(char)
        } else {
            applyBrackets(char, closing)
        }
    }

    private fun handleKeyPress(keyEvent: KeyEvent) {
        // Navigation & Deletion
        when (keyEvent.keyCode) {
            KeyboardInput.KEY_BACKSPACE -> handleBackspace(keyEvent)
            KeyboardInput.KEY_DEL -> handleDelete(keyEvent)
            KeyboardInput.KEY_ENTER, KeyboardInput.KEY_NP_ENTER -> handleEnter()
            KeyboardInput.KEY_ESC -> {
                selectionController.clearSelection()
                surface.requestFocus(null)
                // Clear completions
                modifier.completions.clear()
            }
            // Navigation arrows
            KeyboardInput.KEY_CURSOR_LEFT, KeyboardInput.KEY_CURSOR_RIGHT,
            KeyboardInput.KEY_CURSOR_UP, KeyboardInput.KEY_CURSOR_DOWN,
            KeyboardInput.KEY_PAGE_UP, KeyboardInput.KEY_PAGE_DOWN,
            KeyboardInput.KEY_HOME, KeyboardInput.KEY_END,
            KeyboardInput.KEY_TAB,
                -> handleNavigation(keyEvent)

            else -> handleShortcuts(keyEvent)
        }
    }

    private fun handleShortcuts(keyEvent: KeyEvent) {
        if (keyEvent.isCtrlDown) {
            when (keyEvent.keyCode) {
                KEY_CODE_SELECT_ALL -> selectionController.selectAll()
                KEY_CODE_PASTE -> Clipboard.getStringFromClipboard { it?.let { editText(it) } }
                KEY_CODE_COPY -> selectionController.copySelection()?.let { Clipboard.copyToClipboard(it) }
                KEY_CODE_CUT -> selectionController.copySelection()?.let {
                    Clipboard.copyToClipboard(it)
                    editText("")
                }

                KEY_CODE_UNDO -> {
                    if (keyEvent.isShiftDown) {
                        (lineProvider as? UndoRedoHandler)?.redo { sl, el, sc, ec ->
                            selectionController.selectionChanged(sl, el, sc, ec)
                        }
                    } else {
                        (lineProvider as? UndoRedoHandler)?.undo { sl, el, sc, ec ->
                            selectionController.selectionChanged(sl, el, sc, ec)
                        }
                    }
                }

                else -> {}
            }
        } else {
            // Autocomplete navigation
            if (modifier.completions.isNotEmpty()) {
                when (keyEvent.keyCode) {
                    UniversalKeyCode(' ') -> { /* Could trigger completion confirm if Ctrl+Space logic exists */
                    }

                    else -> {}
                }
            }
        }
    }

    private fun handleBackspace(keyEvent: KeyEvent) {
        if (selectionController.isEmptySelection) {
            selectionController.moveCaretLeft(wordWise = keyEvent.isCtrlDown, select = true)
        }
        // Smart bracket deletion logic
        val startChar = selectionController.caretLine?.text?.getOrNull(modifier.selectionCaretChar)
        editText("") // Delete content
        val nextChar = selectionController.caretLine?.text?.getOrNull(modifier.selectionCaretChar)

        // If we deleted an opening bracket and the next char is the closing one, delete it too
        bracketPairs[startChar]?.let { closing ->
            if (nextChar == closing) {
                // Just delete the next char
                modifier.editorHandler?.replaceText(
                    selectionController.selectionCaretLine, selectionController.selectionCaretLine,
                    selectionController.selectionCaretChar, selectionController.selectionCaretChar + 1,
                    ""
                )
            }
        }
    }

    private fun handleDelete(keyEvent: KeyEvent) {
        if (selectionController.isEmptySelection) {
            selectionController.moveCaretRight(wordWise = keyEvent.isCtrlDown, select = true)
        }
        editText("")
    }

    private fun handleEnter() {
        if (modifier.completions.isNotEmpty()) {
            applyCompletion(modifier.completions.getOrNull(modifier.completionIndex) ?: return)
            return
        }

        val line = selectionController.caretLine ?: return
        val text = line.text
        val caretPos = selectionController.selectionCaretChar.coerceAtMost(text.length)

        // Smart Indentation
        var whitespaces = text.takeWhile { it == ' ' }.length

        val isLPar = text.substring(0, caretPos).trimEnd().endsWith("{")
        val isRPar = text.substring(caretPos).trimStart().startsWith("}")

        if (isLPar) whitespaces += TextAreaConfig.INDENT_SIZE

        // If hitting enter between {} ->
        // {
        //     |
        // }
        if (isLPar && isRPar) {
            val baseIndent = (whitespaces - TextAreaConfig.INDENT_SIZE).coerceAtLeast(0)
            val indentStr = " ".repeat(whitespaces)
            val closeIndentStr = " ".repeat(baseIndent)

            editText("\n$indentStr\n$closeIndentStr")
            // Move caret back up to the middle line
            selectionController.moveCaretLineUp(select = false)
            selectionController.moveCaretLineEnd(select = false)
        } else {
            editText("\n" + " ".repeat(whitespaces))
        }
    }

    private fun handleNavigation(keyEvent: KeyEvent) {
        val isShift = keyEvent.isShiftDown
        val isCtrl = keyEvent.isCtrlDown

        // If completions are visible, Up/Down/Enter controls the popup
        if (modifier.completions.isNotEmpty() && !isCtrl) {
            when (keyEvent.keyCode) {
                KeyboardInput.KEY_CURSOR_UP -> {
                    modifier.setCompletionIndex((modifier.completionIndex - 1 + modifier.completions.size) % modifier.completions.size)
                    return
                }

                KeyboardInput.KEY_CURSOR_DOWN -> {
                    modifier.setCompletionIndex((modifier.completionIndex + 1) % modifier.completions.size)
                    return
                }

                KeyboardInput.KEY_ENTER, KeyboardInput.KEY_NP_ENTER -> {
                    applyCompletion(modifier.completions[modifier.completionIndex])
                    return
                }

                else -> {}
            }
        }

        when (keyEvent.keyCode) {
            KeyboardInput.KEY_CURSOR_LEFT -> selectionController.moveCaretLeft(wordWise = isCtrl, select = isShift)
            KeyboardInput.KEY_CURSOR_RIGHT -> selectionController.moveCaretRight(wordWise = isCtrl, select = isShift)
            KeyboardInput.KEY_CURSOR_UP -> selectionController.moveCaretLineUp(select = isShift)
            KeyboardInput.KEY_CURSOR_DOWN -> selectionController.moveCaretLineDown(select = isShift)
            KeyboardInput.KEY_PAGE_UP -> selectionController.moveCaretPageUp(select = isShift)
            KeyboardInput.KEY_PAGE_DOWN -> selectionController.moveCaretPageDown(select = isShift)
            KeyboardInput.KEY_HOME -> selectionController.moveCaretLineStart(select = isShift)
            KeyboardInput.KEY_END -> selectionController.moveCaretLineEnd(select = isShift)
            else -> {}
        }
    }

    fun applyCompletion(item: CompletionItem) {
        val handler = modifier.editorHandler ?: return
        var lineIdx = modifier.selectionCaretLine
        val charIdx = modifier.selectionCaretChar

        if (item is CompletionItem.Declaration && item.import && !item.fqName.isNullOrBlank()) {
            val linesAdded = ensureImport(item.fqName, handler)
            lineIdx += linesAdded
        }

        val lineText = lineProvider[lineIdx].text

        val startIdx = runCatching {
            lineText.substring(0, charIdx).indexOfLast { !it.isLetterOrDigit() } + 1
        }.getOrElse { charIdx }

        val replaceStart = if (startIdx == -1) charIdx else startIdx

        val newPos = handler.replaceText(lineIdx, lineIdx, replaceStart, charIdx, item.insert)

        if (item.moveCaret != 0) {
            val customCaretX = newPos.x + item.moveCaret
            selectionController.selectionChanged(newPos.y, newPos.y, customCaretX, customCaretX)
        } else {
            selectionController.selectionChanged(newPos.y, newPos.y, newPos.x, newPos.x)
        }

        modifier.completions.clear()
        surface.requestFocus(this)
    }

    private fun ensureImport(fqName: String, handler: TextEditorHandler): Int {
        val importLine = "import $fqName"

        val textLines = (0 until lineProvider.size).map { lineProvider[it].text }

        if (textLines.any { it.trim() == importLine }) return 0

        var insertIndex = 0
        var foundPackage = false
        var lastImportIndex = -1

        for ((i, line) in textLines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("package ")) {
                foundPackage = true
                insertIndex = i + 1
            } else if (trimmed.startsWith("import ")) {
                lastImportIndex = i
                if (importLine < trimmed) {
                    insertIndex = i
                    break
                } else {
                    insertIndex = i + 1
                }
            } else if (trimmed.isNotBlank() && lastImportIndex != -1) {
                break
            }
        }

        val textToInsert = if (insertIndex == 0 && !foundPackage) "$importLine\n" else "$importLine\n"
        handler.insertText(insertIndex, 0, textToInsert)

        return 1
    }

    private fun handleKeyRelease(keyEvent: KeyEvent) {
        if (keyEvent.keyCode == KeyboardInput.KEY_TAB) {
            if (modifier.completions.isNotEmpty()) {
                applyCompletion(modifier.completions.getOrNull(modifier.completionIndex) ?: return)
                return
            }

            if (keyEvent.isShiftDown) unindentSelection() else indentSelection()
        }
    }

    private fun applyBrackets(char: String, closing: Char) {
        if (!selectionController.isEmptySelection) {
            // Границы выделения
            val fromLine = selectionController.selectionFromLine
            val toLine = selectionController.selectionToLine
            val fromChar = selectionController.selectionFromChar
            val toChar = selectionController.selectionToChar

            // Получаем текст выделения
            val selectedText = selectionController.copySelection() ?: return
            val editor = modifier.editorHandler ?: return


            // Заменяем выделение на обёртку (открывающая + оригинал + закрывающая)
            editor.replaceText(
                fromLine, toLine, fromChar, toChar, "$char$selectedText$closing"
            )

            // Ставим новое выделение ровно на ту же часть, но внутри скобок
            selectionController.selectionChanged(
                fromLine, toLine, fromChar + 1, fromChar + 1 + selectedText.length
            )

        } else {
            // --- нет выделения: поведение как раньше ---
            editText(char)
            editText(closing.toString())
            // возвращаем каретку между скобками
            selectionController.moveCaretLeft(wordWise = false, select = false)
        }
    }

    private fun indentSelection() {
        val editor = modifier.editorHandler ?: return

        // Определяем границы выделения
        val fromLine = selectionController.selectionFromLine
        val toLine = selectionController.selectionToLine

        // Если нет выделения — просто вставляем INDENT_SIZE пробелов в текущую строку
        if (fromLine == toLine && selectionController.isEmptySelection) {
            val caretLine = selectionController.selectionCaretLine
            val caretChar = selectionController.selectionCaretChar
            // Вставляем INDENT_SIZE пробелов перед кареткой
            editor.insertText(caretLine, caretChar, " ".repeat(TextAreaConfig.INDENT_SIZE))
            // Сдвигаем каретку вправо на INDENT_SIZE
            selectionController.selectionChanged(caretLine, caretLine, caretChar + TextAreaConfig.INDENT_SIZE, caretChar + TextAreaConfig.INDENT_SIZE)
            return
        }

        // Для каждой строки в диапазоне вставляем INDENT_SIZE пробелов в начало
        for (line in fromLine..toLine) {
            editor.insertText(line, 0, " ".repeat(TextAreaConfig.INDENT_SIZE))
        }

        // Обновляем координаты выделения: сдвигаем отступы начала и конца
        val newFromChar = selectionController.selectionFromChar + TextAreaConfig.INDENT_SIZE
        val newToChar = selectionController.selectionToChar + TextAreaConfig.INDENT_SIZE
        selectionController.selectionChanged(fromLine, toLine, newFromChar, newToChar)
    }

    private fun unindentSelection() {
        val editor = modifier.editorHandler ?: return

        // 1) Нет выделения → удаляем до INDENT_SIZE пробелов прямо перед кареткой
        if (selectionController.isEmptySelection) {
            val line = selectionController.selectionCaretLine
            val char = selectionController.selectionCaretChar
            val text = lineProvider[line].text
            // сколько пробелов подряд перед кареткой?
            val spacesToRemove = text.take(char).takeLastWhile { it == ' ' }.length.coerceAtMost(TextAreaConfig.INDENT_SIZE)

            if (spacesToRemove > 0) {
                editor.replaceText(
                    line, line, char - spacesToRemove, char, ""
                )
                // ставим каретку на место после удаления
                selectionController.selectionChanged(
                    line, line, char - spacesToRemove, char - spacesToRemove
                )
            }
            return
        }

        // 2) Есть выделение → для каждой строки удаляем до INDENT_SIZE пробелов в начале
        val fromLine = selectionController.selectionFromLine
        val toLine = selectionController.selectionToLine

        var removedAtStart = 0
        var removedAtEnd = 0

        for (line in fromLine..toLine) {
            val text = lineProvider[line].text
            val count = text.takeWhile { it == ' ' }.length.coerceAtMost(TextAreaConfig.INDENT_SIZE)

            if (count > 0) {
                editor.replaceText(
                    line, line, 0, count, ""
                )
                if (line == fromLine) removedAtStart = count
                if (line == toLine) removedAtEnd = count
            }
        }

        // Пересчитываем границы выделения, чтобы оно «повисло» на том же тексте
        val newFromChar = (selectionController.selectionFromChar - removedAtStart).coerceAtLeast(0)
        val newToChar = (selectionController.selectionToChar - removedAtEnd).coerceAtLeast(0)

        selectionController.selectionChanged(
            fromLine, toLine, newFromChar, newToChar
        )
    }

    private fun editText(text: String) {
        val editor = modifier.editorHandler ?: return
        val caretPos = if (selectionController.isEmptySelection) {
            editor.insertText(selectionController.selectionCaretLine, selectionController.selectionCaretChar, text)
        } else {
            editor.replaceText(
                selectionController.selectionFromLine,
                selectionController.selectionToLine,
                selectionController.selectionFromChar,
                selectionController.selectionToChar,
                text
            )
        }
        selectionController.selectionChanged(caretPos.y, caretPos.y, caretPos.x, caretPos.x)
    }

    companion object {
        private val KEY_CODE_SELECT_ALL = UniversalKeyCode('A')
        private val KEY_CODE_CUT = UniversalKeyCode('X')
        private val KEY_CODE_COPY = UniversalKeyCode('C')
        private val KEY_CODE_PASTE = UniversalKeyCode('V')
        private val KEY_CODE_UNDO = UniversalKeyCode('Z')

        val factory: (UiNode, UiSurface) -> TextAreaNode = { parent, surface -> TextAreaNode(parent, surface) }
    }
}

/**
 * Manages the indent stack for tracking indentation levels in the text editor.
 * Used for rendering indent guides and smart indentation features.
 */
private class IndentStackManager {
    private val stack = mutableListOf<Int>()
    
    /**
     * Updates the indent stack based on the current line's indent index.
     * Performs both pop and push operations in one call.
     * @param indentIndex The column index of the first non-space character, or -1 if line is all spaces
     * @param lineLength The length of the current line
     */
    fun update(indentIndex: Int, lineLength: Int) {
        if (indentIndex == -1 && lineLength != 0) return
        
        while (indentIndex <= (stack.lastOrNull() ?: 0) && stack.isNotEmpty()) {
            stack.removeLastOrNull()
            if (lineLength == 0) break
        }
        
        if (indentIndex > 0 && indentIndex != stack.lastOrNull()) {
            stack.add(indentIndex)
        }
    }
    
    /**
     * Pops indent levels that are greater than or equal to the current indent.
     * Used for split operation where indents need to be captured after pop but before push.
     * @param indentIndex The column index of the first non-space character, or -1 if line is all spaces
     * @param lineLength The length of the current line
     */
    fun popToIndent(indentIndex: Int, lineLength: Int) {
        if (indentIndex == -1 && lineLength != 0) return
        
        while (indentIndex <= (stack.lastOrNull() ?: 0) && stack.isNotEmpty()) {
            stack.removeLastOrNull()
            if (lineLength == 0) break
        }
    }
    
    /**
     * Pushes a new indent level if applicable.
     * Used for split operation after indents have been captured.
     * @param indentIndex The column index of the first non-space character
     */
    fun pushIndent(indentIndex: Int) {
        if (indentIndex > 0 && indentIndex != stack.lastOrNull()) {
            stack.add(indentIndex)
        }
    }
    
    /**
     * Returns a copy of the current indent positions.
     */
    fun getIndents(): IntArray = stack.toIntArray()
    
    /**
     * Returns the current indent level (stack size).
     */
    val indentLevel: Int get() = stack.size
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
}
