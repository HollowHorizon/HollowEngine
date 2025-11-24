@file:OptIn(ExperimentalContracts::class)

package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.Clipboard
import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.clamp
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.HighlightTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.UndoRedoHandler
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.max
import kotlin.math.min

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
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }

    val textArea = uiNode.createChild(scopeName, TextAreaNode::class, TextAreaNode.factory)
    textArea.listState = state
    textArea.modifier.size(width, height).onWheelX { state.scrollDpX(it.pointer.scroll.x * -20f) }
        .onWheelY { state.scrollDpY(it.pointer.scroll.y * -50f) }

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
                        .background(RoundRectBackground(EditorTheme.Popup.bg, 8.dp))
                        .border(RoundRectBorder(EditorTheme.Popup.border, 8.dp, sizes.borderWidth))
                        .padding(sizes.smallGap * 0.5f)
                        .height(
                            (24.dp + sizes.smallGap) * completions.size.coerceAtMost(10) + sizes.smallGap
                        )
                        .width(Grow(1f, max = FitContent))
                        .background(null)
                        .border(null)
                        .zLayer(500)

                    LazyColumn(
                        withVerticalScrollbar = true,
                        withHorizontalScrollbar = false,
                        isScrollableHorizontal = true,
                        vScrollbarModifier = {
                            it.width(sizes.smallGap).margin(sizes.smallGap * 0.5f)
                                .zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                        },
                        hScrollbarModifier = {
                            it.height(sizes.smallGap).margin(sizes.smallGap * 0.5f)
                                .zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
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
    val selectionHandler = SelectionHandler()

    private inner class LineItem(parent: UiNode?, surface: UiSurface) : RowNode(parent, surface) {
        var lineIndex = -1
        lateinit var indents: IntArray

        val font = MsdfFont(HACK_FONT, 18f)

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
                        val startIdx = error.range.start.column.takeIf { it in text.indices } ?: return
                        val endIdx = error.range.end.column.takeIf { it in text.indices } ?: return
                        val start = font.textDimensions(text.substring(0, startIdx)).width.dp.px
                        val end = font.textDimensions(text.substring(0, endIdx)).width.dp.px
                        start to end
                    }

                    val color =
                        if (error.severity.isError()) HighlightTheme.ERROR_ELEMENT
                        else HighlightTheme.KEYWORD.mix(HighlightTheme.ANNOTATION, 0.5f)
                    getPlainBuilder(UiSurface.LAYER_FLOATING).configured(color) {
                        val leftPos = maxWidth.px + sizes.borderWidth.px + sizes.smallGap.px
                        for (i in ((leftPos + startPos).toInt()..(leftPos + endPos).toInt()).step(5)) {
                            val offset = if (i % 2 == 0) 5 else -5
                            line(
                                Vec2f(i.toFloat(), heightPx - 5 + offset), Vec2f(i + 5f, heightPx - 5 - offset), 3f
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
                    val x = guideStartX + i * spaceWidth - sizes.smallGap.px * 0.5f
                    val color =
                        if (selectionHandler.selectionCaretChar == i && lineIndex == this@TextAreaNode.modifier.selectionCaretLine)
                            EditorTheme.indentGuide.withAlpha(0.8f)
                        else
                            EditorTheme.indentGuide.withAlpha(0.3f)

                    getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRect(x, 0f, 1.dp.px, heightPx, color)
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
        val errors = modifier.errors

        val indentStack = mutableListOf<Int>()
        for (i in 0..<linesHolder.state.itemsFrom.use().coerceAtMost(lineProvider.size)) {
            val line = lineProvider[i]
            val indentIndex = line.text.indexOfFirst { it != ' ' }

            indentIndex.let {
                if (it == -1 && line.length != 0) return@let
                while (it <= (indentStack.lastOrNull() ?: 0) && indentStack.isNotEmpty()) {
                    indentStack.removeLastOrNull()
                    if (line.length == 0) break
                }
                if (it > 0 && it != indentStack.lastOrNull()) {
                    indentStack.add(it)
                }
            }
        }
        selectionHandler.updateSelectionRange()
        linesHolder.indices(lineProvider.size) { lineIndex ->
            if (lineIndex >= lineProvider.size) return@indices
            val line = lineProvider[lineIndex]
            val indentIndex = line.text.indexOfFirst { it != ' ' }
            indentIndex.let {
                if (it == -1 && line.length != 0) return@let
                while (it <= (indentStack.lastOrNull() ?: 0) && indentStack.isNotEmpty()) {
                    indentStack.removeLastOrNull()
                    if (line.length == 0) break
                }
            }
            val font = MsdfFont(HACK_FONT, 18f)

            val lineItem = uiNode.createChild(null, LineItem::class, lineItemFactory)
            lineItem.lineIndex = lineIndex
            lineItem.indents = indentStack.toIntArray()
            lineItem.modifier.width(Grow.Std).layout(RowLayout)
            with(lineItem) {
                val maxWidth = font.textDimensions(lineProvider.size.toString()).width.dp + sizes.smallGap * 2f

                Box(maxWidth) {
                    modifier
                        .background(RectBackground(EditorTheme.gutterBg))
                        .height(Grow.Std)
                        .padding(horizontal = sizes.smallGap)
                        .alignY(AlignmentY.Center)

                    Text((lineIndex + 1).toString()) {
                        val textColor = if (lineIndex == this@TextAreaNode.modifier.selectionCaretLine)
                            EditorTheme.gutterText.mix(Color.WHITE, 0.75f)
                        else
                            EditorTheme.gutterText

                        modifier.font(font).textColor(textColor).align(AlignmentX.End, AlignmentY.Center)
                    }
                }

                Box(sizes.borderWidth, Grow.Std) { modifier.backgroundColor(EditorTheme.Popup.border) }

                setupTextLine(line, lineIndex, textAreaMod, lineProvider).apply {
                    modifier
                        .alignY(AlignmentY.Center)
                        .padding(start = sizes.smallGap)
                }
            }

            indentIndex.let {
                if (it > 0 && it != indentStack.lastOrNull()) {
                    indentStack.add(it)
                }
            }
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
            areaModifier.setCompletionX(it.leftPx + line.charIndexToPx(dotIndex))

            val sizeY = (24.dp + sizes.smallGap).px * areaModifier.completions.size.coerceAtMost(10) + 24.dp.px
            if (it.bottomPx + sizeY > bottomPx) {
                areaModifier.setCompletionY(it.bottomPx - sizeY)
            } else {
                areaModifier.setCompletionY(it.bottomPx)
            }
        }

        if (this@TextAreaNode.modifier.onSelectionChanged != null) {
            modifier.onClick {
                when (it.pointer.leftButtonRepeatedClickCount) {
                    1 -> selectionHandler.onSelectStart(this, lineIndex, it, false)
                    2 -> selectionHandler.selectWord(this, line.text, lineIndex, it)
                    3 -> selectionHandler.selectLine(this, line.text, lineIndex)
                }
            }.onDragStart { selectionHandler.onSelectStart(this, lineIndex, it, true) }
                .onDrag { selectionHandler.onDrag(it) }.onDragEnd { selectionHandler.onSelectEnd() }
                .onPointer { selectionHandler.onPointer(this, lineIndex, it) }

            modifier.padding(start = textAreaMod.lineStartPadding, end = textAreaMod.lineEndPadding)

            selectionHandler.applySelectionRange(this, line, lineIndex)
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
                selectionHandler.clearSelection()
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
                UniversalKeyCode('A') -> selectionHandler.selectAll()
                UniversalKeyCode('V') -> Clipboard.getStringFromClipboard { it?.let { editText(it) } }
                UniversalKeyCode('C') -> selectionHandler.copySelection()?.let { Clipboard.copyToClipboard(it) }
                UniversalKeyCode('X') -> selectionHandler.copySelection()?.let {
                    Clipboard.copyToClipboard(it)
                    editText("")
                }

                UniversalKeyCode('Z') -> {
                    if (keyEvent.isShiftDown) {
                        (lineProvider as? UndoRedoHandler)?.redo { sl, el, sc, ec ->
                            selectionHandler.selectionChanged(sl, el, sc, ec)
                        }
                    } else {
                        (lineProvider as? UndoRedoHandler)?.undo { sl, el, sc, ec ->
                            selectionHandler.selectionChanged(sl, el, sc, ec)
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
        if (selectionHandler.isEmptySelection) {
            selectionHandler.moveCaretLeft(wordWise = keyEvent.isCtrlDown, select = true)
        }
        // Smart bracket deletion logic
        val startChar = selectionHandler.caretLine?.text?.getOrNull(modifier.selectionCaretChar)
        editText("") // Delete content
        val nextChar = selectionHandler.caretLine?.text?.getOrNull(modifier.selectionCaretChar)

        // If we deleted an opening bracket and the next char is the closing one, delete it too
        bracketPairs[startChar]?.let { closing ->
            if (nextChar == closing) {
                // Just delete the next char
                modifier.editorHandler?.replaceText(
                    selectionHandler.selectionCaretLine, selectionHandler.selectionCaretLine,
                    selectionHandler.selectionCaretChar, selectionHandler.selectionCaretChar + 1,
                    ""
                )
            }
        }
    }

    private fun handleDelete(keyEvent: KeyEvent) {
        if (selectionHandler.isEmptySelection) {
            selectionHandler.moveCaretRight(wordWise = keyEvent.isCtrlDown, select = true)
        }
        editText("")
    }

    private fun handleEnter() {
        if (modifier.completions.isNotEmpty()) {
            applyCompletion(modifier.completions.getOrNull(modifier.completionIndex) ?: return)
            return
        }

        val line = selectionHandler.caretLine ?: return
        val text = line.text
        val caretPos = selectionHandler.selectionCaretChar.coerceAtMost(text.length)

        // Smart Indentation
        var whitespaces = text.takeWhile { it == ' ' }.length

        val isLPar = text.substring(0, caretPos).trimEnd().endsWith("{")
        val isRPar = text.substring(caretPos).trimStart().startsWith("}")

        if (isLPar) whitespaces += 4

        // If hitting enter between {} ->
        // {
        //     |
        // }
        if (isLPar && isRPar) {
            val baseIndent = (whitespaces - 4).coerceAtLeast(0)
            val indentStr = " ".repeat(whitespaces)
            val closeIndentStr = " ".repeat(baseIndent)

            editText("\n$indentStr\n$closeIndentStr")
            // Move caret back up to the middle line
            selectionHandler.moveCaretLineUp(select = false)
            selectionHandler.moveCaretLineEnd(select = false)
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
            KeyboardInput.KEY_CURSOR_LEFT -> selectionHandler.moveCaretLeft(wordWise = isCtrl, select = isShift)
            KeyboardInput.KEY_CURSOR_RIGHT -> selectionHandler.moveCaretRight(wordWise = isCtrl, select = isShift)
            KeyboardInput.KEY_CURSOR_UP -> selectionHandler.moveCaretLineUp(select = isShift)
            KeyboardInput.KEY_CURSOR_DOWN -> selectionHandler.moveCaretLineDown(select = isShift)
            KeyboardInput.KEY_PAGE_UP -> selectionHandler.moveCaretPageUp(select = isShift)
            KeyboardInput.KEY_PAGE_DOWN -> selectionHandler.moveCaretPageDown(select = isShift)
            KeyboardInput.KEY_HOME -> selectionHandler.moveCaretLineStart(select = isShift)
            KeyboardInput.KEY_END -> selectionHandler.moveCaretLineEnd(select = isShift)
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
            selectionHandler.selectionChanged(newPos.y, newPos.y, customCaretX, customCaretX)
        } else {
            selectionHandler.selectionChanged(newPos.y, newPos.y, newPos.x, newPos.x)
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
        if (!selectionHandler.isEmptySelection) {
            // Границы выделения
            val fromLine = selectionHandler.selectionFromLine
            val toLine = selectionHandler.selectionToLine
            val fromChar = selectionHandler.selectionFromChar
            val toChar = selectionHandler.selectionToChar

            // Получаем текст выделения
            val selectedText = selectionHandler.copySelection() ?: return
            val editor = modifier.editorHandler ?: return


            // Заменяем выделение на обёртку (открывающая + оригинал + закрывающая)
            editor.replaceText(
                fromLine, toLine, fromChar, toChar, "$char$selectedText$closing"
            )

            // Ставим новое выделение ровно на ту же часть, но внутри скобок
            selectionHandler.selectionChanged(
                fromLine, toLine, fromChar + 1, fromChar + 1 + selectedText.length
            )

        } else {
            // --- нет выделения: поведение как раньше ---
            editText(char)
            editText(closing.toString())
            // возвращаем каретку между скобками
            selectionHandler.moveCaretLeft(wordWise = false, select = false)
        }
    }

    private fun indentSelection() {
        val editor = modifier.editorHandler ?: return

        // Определяем границы выделения
        val fromLine = selectionHandler.selectionFromLine
        val toLine = selectionHandler.selectionToLine

        // Если нет выделения — просто вставляем 4 пробела в текущую строку
        if (fromLine == toLine && selectionHandler.isEmptySelection) {
            val caretLine = selectionHandler.selectionCaretLine
            val caretChar = selectionHandler.selectionCaretChar
            // Вставляем 4 пробела перед кареткой
            editor.insertText(caretLine, caretChar, "    ")
            // Сдвигаем каретку вправо на 4
            selectionHandler.selectionChanged(caretLine, caretLine, caretChar + 4, caretChar + 4)
            return
        }

        // Для каждой строки в диапазоне вставляем 4 пробела в начало
        for (line in fromLine..toLine) {
            editor.insertText(line, 0, "    ")
        }

        // Обновляем координаты выделения: сдвигаем отступы начала и конца
        val newFromChar = selectionHandler.selectionFromChar + 4
        val newToChar = selectionHandler.selectionToChar + 4
        selectionHandler.selectionChanged(fromLine, toLine, newFromChar, newToChar)
    }

    private fun unindentSelection() {
        val editor = modifier.editorHandler ?: return

        // 1) Нет выделения → удаляем до 4 пробелов прямо перед кареткой
        if (selectionHandler.isEmptySelection) {
            val line = selectionHandler.selectionCaretLine
            val char = selectionHandler.selectionCaretChar
            val text = lineProvider[line].text
            // сколько пробелов подряд перед кареткой?
            val spacesToRemove = text.take(char).takeLastWhile { it == ' ' }.length.coerceAtMost(4)

            if (spacesToRemove > 0) {
                editor.replaceText(
                    line, line, char - spacesToRemove, char, ""
                )
                // ставим каретку на место после удаления
                selectionHandler.selectionChanged(
                    line, line, char - spacesToRemove, char - spacesToRemove
                )
            }
            return
        }

        // 2) Есть выделение → для каждой строки удаляем до 4 пробелов в начале
        val fromLine = selectionHandler.selectionFromLine
        val toLine = selectionHandler.selectionToLine

        var removedAtStart = 0
        var removedAtEnd = 0

        for (line in fromLine..toLine) {
            val text = lineProvider[line].text
            val count = text.takeWhile { it == ' ' }.length.coerceAtMost(4)

            if (count > 0) {
                editor.replaceText(
                    line, line, 0, count, ""
                )
                if (line == fromLine) removedAtStart = count
                if (line == toLine) removedAtEnd = count
            }
        }

        // Пересчитываем границы выделения, чтобы оно «повисло» на том же тексте
        val newFromChar = (selectionHandler.selectionFromChar - removedAtStart).coerceAtLeast(0)
        val newToChar = (selectionHandler.selectionToChar - removedAtEnd).coerceAtLeast(0)

        selectionHandler.selectionChanged(
            fromLine, toLine, newFromChar, newToChar
        )
    }

    private fun editText(text: String) {
        val editor = modifier.editorHandler ?: return
        val caretPos = if (selectionHandler.isEmptySelection) {
            editor.insertText(selectionHandler.selectionCaretLine, selectionHandler.selectionCaretChar, text)
        } else {
            editor.replaceText(
                selectionHandler.selectionFromLine,
                selectionHandler.selectionToLine,
                selectionHandler.selectionFromChar,
                selectionHandler.selectionToChar,
                text
            )
        }
        selectionHandler.selectionChanged(caretPos.y, caretPos.y, caretPos.x, caretPos.x)
    }

    inner class SelectionHandler {
        var isSelecting = false

        var selectionStartLine = 0
        var selectionCaretLine = 0
        var selectionStartChar = 0
        var selectionCaretChar = 0

        val caretLine: ScriptTextLine?
            get() = if (selectionCaretLine in 0 until lineProvider.size) lineProvider[selectionCaretLine] else null
        var caretLineScope: AttributedTextScope? = null

        val isReverseSelection: Boolean
            get() = selectionCaretLine < selectionStartLine
        val isEmptySelection: Boolean
            get() = selectionStartLine == selectionCaretLine && selectionStartChar == selectionCaretChar

        val selectionFromLine: Int
            get() = min(selectionStartLine, selectionCaretLine)
        val selectionToLine: Int
            get() = max(selectionStartLine, selectionCaretLine)
        val selectionFromChar: Int
            get() = when {
                isReverseSelection -> selectionCaretChar
                selectionStartLine == selectionCaretLine -> min(selectionStartChar, selectionCaretChar)
                else -> selectionStartChar
            }
        val selectionToChar: Int
            get() = when {
                isReverseSelection -> selectionStartChar
                selectionStartLine == selectionCaretLine -> max(selectionStartChar, selectionCaretChar)
                else -> selectionCaretChar
            }

        fun updateSelectionRange() {
            selectionStartLine = modifier.selectionStartLine
            selectionCaretLine = modifier.selectionCaretLine
            selectionStartChar = modifier.selectionStartChar
            selectionCaretChar = modifier.selectionCaretChar
            caretLineScope = null
        }

        fun applySelectionRange(attributedText: AttributedTextScope, line: ScriptTextLine, lineIndex: Int) {
            val from = selectionFromLine
            val to = selectionToLine

            var selCaretPos = 0
            var selStartPos = 0

            if (lineIndex in (from + 1) until to) {
                // line is completely in selection range
                selStartPos = 0
                selCaretPos = line.length

            } else if (lineIndex == selectionStartLine && selectionStartLine == selectionCaretLine) {
                // single-line selection
                selStartPos = selectionStartChar
                selCaretPos = selectionCaretChar

            } else if (lineIndex == selectionFromLine) {
                // multi-line selection, first selected line
                if (isReverseSelection) {
                    // reverse selection
                    selStartPos = line.length
                    selCaretPos = selectionCaretChar
                } else {
                    // forward selection
                    selStartPos = selectionStartChar
                    selCaretPos = line.length
                }
            } else if (lineIndex == selectionToLine) {
                // multi-line selection, last selected line
                if (isReverseSelection) {
                    // reverse selection
                    selStartPos = selectionStartChar
                    selCaretPos = 0
                } else {
                    // forward selection
                    selStartPos = 0
                    selCaretPos = selectionCaretChar
                }
            }

            if (lineIndex == selectionCaretLine) {
                caretLineScope = attributedText
            }

            val isMultiLineSelection = from != to
            val hasSelection = (selStartPos != selCaretPos) ||
                    (lineIndex in (from + 1) until to) ||
                    (isMultiLineSelection && lineIndex == from)

            attributedText.modifier.selectionColor = EditorTheme.selection
            attributedText.modifier.caretColor = EditorTheme.caret

            attributedText.modifier.selectionRange(selStartPos, selCaretPos, hasSelection, isMultiLineSelection)
                .isCaretVisible(isFocused.use() && lineIndex == selectionCaretLine)
        }

        fun copySelection(): String? {
            return if (isEmptySelection) {
                null

            } else if (selectionStartLine == selectionCaretLine) {
                // single-line selection
                val fromChar = min(selectionStartChar, selectionCaretChar)
                val toChar = max(selectionStartChar, selectionCaretChar)
                lineProvider[selectionFromLine].text.substring(fromChar, toChar)

            } else {
                // multi-line selection
                return buildString {
                    append(lineProvider[selectionFromLine].text.substring(selectionFromChar)).append('\n')
                    for (i in (selectionFromLine + 1) until selectionToLine) {
                        append(lineProvider[i].text).append('\n')
                    }
                    append(lineProvider[selectionToLine].text.substring(0, selectionToChar))
                }
            }
        }

        fun clearSelection() {
            selectionChanged(selectionCaretLine, selectionCaretLine, selectionCaretChar, selectionCaretChar, false)
        }

        fun selectAll() {
            selectionChanged(0, lineProvider.lastIndex, 0, lineProvider[lineProvider.lastIndex].length, false)
        }

        fun selectWord(attributedText: AttributedTextScope, text: String, lineIndex: Int, ev: PointerEvent) {
            val charIndex = attributedText.charIndexFromLocalX(ev.position.x)
            val startChar = TextCaretNavigation.startOfExpression(text, charIndex)
            val caretChar = TextCaretNavigation.endOfExpression(text, charIndex)
            caretLineScope = attributedText
            selectionChanged(lineIndex, lineIndex, startChar, caretChar)
        }

        fun selectLine(attributedText: AttributedTextScope, text: String, lineIndex: Int) {
            selectionChanged(lineIndex, lineIndex, 0, text.length)
            caretLineScope = attributedText
        }

        fun onSelectStart(attributedText: AttributedTextScope, lineIndex: Int, ev: PointerEvent, isSelecting: Boolean) {
            requestFocus()

            this.isSelecting = isSelecting
            val charIndex = attributedText.charIndexFromLocalX(ev.position.x)
            caretLineScope = attributedText
            selectionChanged(lineIndex, lineIndex, charIndex, charIndex)
        }

        fun onDrag(ev: PointerEvent) {
            caretLineScope?.apply {
                val dragLocalPos = MutableVec2f()
                uiNode.toLocal(ev.screenPosition, dragLocalPos)
                val charIndex = charIndexFromLocalX(dragLocalPos.x)
                selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, charIndex, false)
            }
        }

        fun onSelectEnd() {
            isSelecting = false
        }

        fun onPointer(attributedText: AttributedTextScope, lineIndex: Int, ev: PointerEvent) {
            if (isSelecting && ev.pointer.isDrag) {
                caretLineScope = attributedText
                selectionChanged(selectionStartLine, lineIndex, selectionStartChar, selectionCaretChar, false)
            }
        }

        fun moveCaretLeft(wordWise: Boolean, select: Boolean) {
            caretLine?.text?.let { txt ->
                if (selectionCaretChar == 0 && selectionCaretLine > 0) {
                    selectionCaretLine--
                    val line = lineProvider[selectionCaretLine]
                    val newTxt = line.text
                    selectionCaretChar = line.length

                    if (wordWise) {
                        if (newTxt.isEmpty()) return
                        selectionCaretChar = TextCaretNavigation.moveExpressionLeft(newTxt, selectionCaretChar)
                    }
                    if (!select) {
                        selectionStartLine = selectionCaretLine
                        selectionStartChar = selectionCaretChar
                    }

                } else if (wordWise) {
                    selectionCaretChar = TextCaretNavigation.moveExpressionLeft(txt, selectionCaretChar)
                } else {
                    selectionCaretChar = (selectionCaretChar - 1).clamp(0, txt.length)
                }
            }
            if (!select) {
                selectionStartLine = selectionCaretLine
                selectionStartChar = selectionCaretChar
            }
            selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
        }

        fun moveCaretRight(wordWise: Boolean, select: Boolean) {
            caretLine?.text?.let { txt ->
                if (selectionCaretChar == txt.length && selectionCaretLine < lineProvider.lastIndex) {
                    selectionCaretLine++
                    val line = lineProvider[selectionCaretLine]
                    val newTxt = line.text
                    selectionCaretChar = 0

                    if (wordWise) {
                        selectionCaretChar = TextCaretNavigation.moveExpressionRight(newTxt, selectionCaretChar)
                    }
                    if (!select) {
                        selectionStartLine = selectionCaretLine
                        selectionStartChar = selectionCaretChar
                    }

                } else if (wordWise) {
                    selectionCaretChar = TextCaretNavigation.moveExpressionRight(txt, selectionCaretChar)
                } else {
                    selectionCaretChar = (selectionCaretChar + 1).clamp(0, txt.length)
                }
            }
            if (!select) {
                selectionStartLine = selectionCaretLine
                selectionStartChar = selectionCaretChar
            }
            selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
        }

        fun moveCaretLineUp(select: Boolean) {
            moveCaretToLine(selectionCaretLine - 1, select)
        }

        fun moveCaretLineDown(select: Boolean) {
            moveCaretToLine(selectionCaretLine + 1, select)
        }

        fun moveCaretPageUp(select: Boolean) {
            val bottomLinePad = 2
            val numPageLines = max(1, linesHolder.state.numVisibleItems - bottomLinePad)
            moveCaretToLine(selectionCaretLine - numPageLines, select)
        }

        fun moveCaretPageDown(select: Boolean) {
            val bottomLinePad = 2
            val numPageLines = max(1, linesHolder.state.numVisibleItems - bottomLinePad)
            moveCaretToLine(selectionCaretLine + numPageLines, select)
        }

        private fun moveCaretToLine(targetLine: Int, select: Boolean) {
            val line = caretLine ?: return
            val caretX = line.charIndexToPx(selectionCaretChar)

            if (targetLine in 0 until lineProvider.size) {
                selectionCaretChar = lineProvider[targetLine].charIndexFromPx(caretX)
                selectionCaretLine = targetLine
            } else if (targetLine < 0) {
                selectionCaretChar = 0
                selectionCaretLine = 0
            } else if (targetLine > lineProvider.lastIndex) {
                selectionCaretChar = lineProvider[lineProvider.lastIndex].length
                selectionCaretLine = lineProvider.lastIndex
            }

            if (!select) {
                selectionStartLine = selectionCaretLine
                selectionStartChar = selectionCaretChar
            }
            selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
        }

        fun moveCaretLineStart(select: Boolean) {
            selectionCaretChar = 0
            if (!select) {
                selectionStartLine = selectionCaretLine
                selectionStartChar = selectionCaretChar
            }
            selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
        }

        fun moveCaretLineEnd(select: Boolean) {
            val line = caretLine ?: return
            selectionCaretChar = line.length
            if (!select) {
                selectionStartLine = selectionCaretLine
                selectionStartChar = selectionCaretChar
            }
            selectionChanged(selectionStartLine, selectionCaretLine, selectionStartChar, selectionCaretChar)
        }

        fun selectionChanged(
            startLine: Int,
            caretLine: Int,
            startChar: Int,
            caretChar: Int,
            scrollToCaret: Boolean = true,
        ) {
            selectionStartLine = startLine
            selectionCaretLine = caretLine
            selectionStartChar = startChar
            selectionCaretChar = caretChar

            if (startLine != modifier.selectionStartLine || caretLine != modifier.selectionCaretLine || startChar != modifier.selectionStartChar || caretChar != modifier.selectionCaretChar) {

                modifier.setSelectionRange(startLine, caretLine, startChar, caretChar)
                resetCaretBlinkState()
                if (scrollToCaret) {
                    scrollToCaret()
                }
            }
        }

        fun resetCaretBlinkState() {
            (caretLineScope as? AttributedTextNode)?.resetCaretBlinkState()
        }

        fun scrollToCaret() {
            val scrState = linesHolder.state
            scrState.scrollToItem.set(selectionCaretLine)

            val scrollPad = 16f
            val caretX = Dp.fromPx(caretLine?.charIndexToPx(selectionCaretChar) ?: 0f).value
            val scrLt = scrState.xScrollDp.value
            val scrRt = scrState.xScrollDp.value + scrState.viewWidthDp.value
            if (caretX - scrollPad < scrLt) {
                scrState.scrollDpX(caretX - scrLt - scrollPad)
            } else if (caretX + scrollPad * 4 > scrRt) {
                scrState.scrollDpX(caretX - scrRt + scrollPad * 4)
            }
        }
    }

    companion object {
        private val KEY_CODE_SELECT_ALL = UniversalKeyCode('A')
        private val KEY_CODE_CUT = UniversalKeyCode('X')
        private val KEY_CODE_COPY = UniversalKeyCode('C')
        private val KEY_CODE_PASTE = UniversalKeyCode('V')

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
}
