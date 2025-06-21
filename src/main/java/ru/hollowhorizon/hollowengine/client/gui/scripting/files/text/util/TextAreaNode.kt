@file:OptIn(ExperimentalContracts::class)

package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.Clipboard
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.LocalKeyCode
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.*
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.TriangulatedLineMesh
import de.fabmax.kool.scene.addTriangulatedLineMesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.TextCaretNavigation
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.getCharAfterSelection
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.getCharBeforeSelection
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys.toEngine
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionVariant
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.max
import kotlin.math.min

var errorMessage = ""

internal val bracketPairs = mapOf(
    '(' to ')', '[' to ']', '{' to '}',
    '<' to '>', '"' to '"', '\'' to '\''
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

    val completions by property(mutableListOf<CompletionVariant>())
    val errors by property(mutableListOf<ScriptError>())

    var completionIndex by property(-1)
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
    textArea.modifier
        .size(width, height)
        .onWheelX { state.scrollDpX(it.pointer.scroll.x * -20f) }
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

                Popup(
                    modifier.completionX,
                    modifier.completionY
                ) {
                    modifier.padding(sizes.smallGap * 0.5f)
                        .height(
                            (24.dp + sizes.smallGap) * completions.size.coerceAtMost(10) + sizes.smallGap
                        )
                        .width(Grow(1f, max = FitContent))
                        .background(null)
                        .border(null)
                        .zLayer(UiSurface.LAYER_POPUP)

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
                        }
                    ) {
                        modifier.margin(end = sizes.gap)

                        textArea.completionsList = (this as LazyListNode).state
                        itemsIndexed(completions) { index, completion ->
                            completion.apply { create(textArea, this@setupContent.modifier.completionIndex == index) }
                        }
                    }
                }
            }
        }
    ) {
        this.block()

        val pos = Minecraft.getInstance().mouseHandler
        if (errorMessage.isNotEmpty()) {
            surface.triggerUpdate()
            Popup(pos.xpos().toFloat(), pos.ypos().toFloat()) {
                modifier.background(UiRenderer { node ->
                    node.apply {
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                            .localRoundRect(0f, 0f, widthPx, heightPx, sizes.smallGap.px, colors.background)
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                            .localRoundRectBorder(
                                0f,
                                0f,
                                widthPx,
                                heightPx,
                                sizes.smallGap.px,
                                sizes.borderWidth.px,
                                colors.primaryVariant
                            )
                    }
                })

                modifier.width(Grow(1f, max = FitContent))

                Text(errorMessage) {
                    modifier.margin(sizes.smallGap).isWrapText(true).width(Grow.Std)
                }
            }
        }

        errorMessage = ""
        val primitives = surface.getUiPrimitives(modifier.zLayer)
        primitives.children.filterIsInstance<TriangulatedLineMesh>().forEach {
            primitives.removeNode(it)
        }
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
            linesHolder.modifier
                .orientation(ListOrientation.Vertical)
                .layout(ColumnLayout)
                .width(Grow.MinFit)

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

        selectionHandler.updateSelectionRange()
        linesHolder.indices(lineProvider.size) { lineIndex ->
            if (lineIndex >= lineProvider.size) return@indices
            val line = lineProvider[lineIndex]
            val font = MsdfFont(HACK_FONT, 18f)

            Row(Grow.Std) {
                val maxWidth = font.textDimensions(lineProvider.size.toString()).width.dp


                errors.find { it.line - 1 == lineIndex }?.let { error ->
                    setupError(error, font, line.text, maxWidth)
                }

                Box(maxWidth) {
                    modifier.margin(horizontal = sizes.smallGap)
                        .alignY(AlignmentY.Center)
                    Text((lineIndex + 1).toString()) {
                        modifier.font(font).textColor(Color("717888FF"))
                            .align(AlignmentX.End, AlignmentY.Center)
                    }
                }

                Box(sizes.borderWidth, Grow.Std) {
                    modifier
                        .backgroundColor(Color("3C3C4AFF"))
                        .alignY(AlignmentY.Center)
                }

                setupTextLine(line, lineIndex, textAreaMod, lineProvider).apply {
                    modifier.alignY(AlignmentY.Center).margin(start = sizes.smallGap * 0.5f).alignY(AlignmentY.Top)
                    modifier.padding(sizes.smallGap * 0.5f)
                    if (lineIndex == this@TextAreaNode.modifier.selectionStartLine) {
                        modifier.border(RoundRectBorder(Color("3C3C4AFF"), sizes.smallGap, sizes.borderWidth))
                    }
                }
            }
        }
    }

    private fun UiScope.setupError(error: ScriptError, font: MsdfFont, text: String, maxWidth: Dp) {
        val column = error.column - 1
        val startPos = if (text.isEmpty()) 0f else font.textDimensions(
            text.substring(0, column.coerceAtMost(text.lastIndex))
        ).width.dp.px
        val endPos = if (text.isEmpty()) 0f else font.textDimensions(
            text.substring(0, TextCaretNavigation.endOfWord(text, column).coerceAtMost(text.lastIndex) + 1)
        ).width.dp.px

        if (error.severity.ordinal > 2) getUiPrimitives().addTriangulatedLineMesh {
            this.width = 3f
            this.color = Color.RED

            val leftPos = uiNode.leftPx + maxWidth.px + sizes.smallGap.px * 3f + sizes.borderWidth.px
            for (i in ((leftPos + startPos).toInt()..(leftPos + endPos).toInt()).step(5)) {
                val offset = if (i % 2 == 0) 5 else -5
                addLine(
                    Vec3f(i + 0f, uiNode.bottomPx + offset, 0f),
                    Vec3f(i + 5f, uiNode.bottomPx - offset, 0f)
                )
            }

            val mouse = PointerInput.primaryPointer

            if (mouse.pos.x in leftPos + startPos..leftPos + endPos && mouse.pos.y in uiNode.topPx..uiNode.bottomPx) {
                errorMessage = error.message
            }
        }
    }

    protected open fun UiScope.setupTextLine(
        line: TextLine,
        lineIndex: Int,
        textAreaMod: ScriptTextAreaModifier,
        lineProvider: TextLineProvider,
    ): UiScope = AttributedText(line) {
        modifier.width(Grow.MinFit)

        modifier.onPositioned {
            val areaModifier = this@TextAreaNode.modifier

            if (lineIndex != areaModifier.selectionStartLine) return@onPositioned

            val selectionIndex = (areaModifier.selectionStartChar - 1).coerceAtLeast(0)

            var dotIndex = TextCaretNavigation.startOfWord(line.text, selectionIndex)
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
            modifier
                .onClick {
                    when (it.pointer.leftButtonRepeatedClickCount) {
                        1 -> selectionHandler.onSelectStart(this, lineIndex, it, false)
                        2 -> selectionHandler.selectWord(this, line.text, lineIndex, it)
                        3 -> selectionHandler.selectLine(this, line.text, lineIndex)
                    }
                }
                .onDragStart { selectionHandler.onSelectStart(this, lineIndex, it, true) }
                .onDrag { selectionHandler.onDrag(it) }
                .onDragEnd { selectionHandler.onSelectEnd() }
                .onPointer { selectionHandler.onPointer(this, lineIndex, it) }

            modifier.padding(start = textAreaMod.lineStartPadding, end = textAreaMod.lineEndPadding)
            if (lineIndex == 0) {
                modifier
                    .textAlignY(AlignmentY.Bottom)
                    .padding(top = textAreaMod.firstLineTopPadding)
            }
            if (lineIndex == lineProvider.lastIndex) {
                modifier
                    .textAlignY(AlignmentY.Top)
                    .padding(bottom = textAreaMod.lastLineBottomPadding)
            }

            selectionHandler.applySelectionRange(this, line, lineIndex)
        }
    }

    override fun onKeyEvent(keyEvent: KeyEvent) {
        val event = keyEvent.toEngine(this)
        EventBus.post(event)
        if (event.isCanceled) return

        val startChar = modifier.getCharBeforeSelection()
        val nextChar = modifier.getCharAfterSelection()

        if (keyEvent.isCharTyped) {
            val closing = bracketPairs[keyEvent.localKeyCode.code.toChar()]

            if (closing == null) {
                editText(keyEvent.typedChar.toString())
            } else {
                applyBrackets(keyEvent.typedChar.toString(), closing)
            }
        } else if (keyEvent.isPressed) {
            when (keyEvent.keyCode) {
                KeyboardInput.KEY_BACKSPACE -> {
                    if (selectionHandler.isEmptySelection) {
                        selectionHandler.moveCaretLeft(wordWise = keyEvent.isCtrlDown, select = true)
                    }
                    editText("")
                    bracketPairs[startChar]?.let {
                        if (nextChar != it) return@let
                        if (selectionHandler.isEmptySelection) {
                            selectionHandler.moveCaretRight(wordWise = keyEvent.isCtrlDown, select = true)
                        }
                        editText("")
                    }
                }

                KeyboardInput.KEY_DEL -> {
                    if (selectionHandler.isEmptySelection) {
                        selectionHandler.moveCaretRight(wordWise = keyEvent.isCtrlDown, select = true)
                    }
                    editText("")
                }

                KeyboardInput.KEY_ENTER, KeyboardInput.KEY_NP_ENTER -> {
                    try {
                        val whitespaces = selectionHandler.caretLine?.text
                            ?.let {
                                var whitespaces = 0
                                for(c in it) {
                                    if(c == ' ') whitespaces++
                                    else break
                                }
                                whitespaces
                            }
                            ?: 0

                        val isLambda = selectionHandler.caretLine?.text
                            ?.substring(0, selectionHandler.selectionCaretChar.coerceAtLeast(0))
                            ?.trim()?.endsWith("{") == true

                        editText("\n" + " ".repeat(whitespaces + if (isLambda) 4 else 0))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                KeyboardInput.KEY_CURSOR_LEFT -> selectionHandler.moveCaretLeft(
                    wordWise = keyEvent.isCtrlDown,
                    select = keyEvent.isShiftDown
                )

                KeyboardInput.KEY_CURSOR_RIGHT -> selectionHandler.moveCaretRight(
                    wordWise = keyEvent.isCtrlDown,
                    select = keyEvent.isShiftDown
                )

                KeyboardInput.KEY_CURSOR_UP -> selectionHandler.moveCaretLineUp(select = keyEvent.isShiftDown)
                KeyboardInput.KEY_CURSOR_DOWN -> selectionHandler.moveCaretLineDown(select = keyEvent.isShiftDown)
                KeyboardInput.KEY_PAGE_UP -> selectionHandler.moveCaretPageUp(select = keyEvent.isShiftDown)
                KeyboardInput.KEY_PAGE_DOWN -> selectionHandler.moveCaretPageDown(select = keyEvent.isShiftDown)
                KeyboardInput.KEY_HOME -> selectionHandler.moveCaretLineStart(select = keyEvent.isShiftDown)
                KeyboardInput.KEY_END -> selectionHandler.moveCaretLineEnd(select = keyEvent.isShiftDown)
                KeyboardInput.KEY_ESC -> {
                    selectionHandler.clearSelection()
                    surface.requestFocus(null)
                }

                else -> {
                    if (keyEvent.isCtrlDown) {
                        when (keyEvent.localKeyCode) {
                            KEY_CODE_SELECT_ALL -> selectionHandler.selectAll()
                            KEY_CODE_PASTE -> Clipboard.getStringFromClipboard { paste -> paste?.let { editText(it) } }
                            KEY_CODE_COPY -> selectionHandler.copySelection()?.let { Clipboard.copyToClipboard(it) }
                            KEY_CODE_CUT -> {
                                selectionHandler.copySelection()?.let {
                                    Clipboard.copyToClipboard(it)
                                    editText("")
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        } else if (keyEvent.isReleased) {
            when (keyEvent.keyCode) {
                KeyboardInput.KEY_TAB -> {
                    if (keyEvent.isShiftDown) {
                        unindentSelection()
                    } else {
                        indentSelection()
                    }
                }

                else -> {}
            }
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
                fromLine, toLine,
                fromChar, toChar,
                "$char$selectedText$closing",
                this
            )

            // Ставим новое выделение ровно на ту же часть, но внутри скобок
            selectionHandler.selectionChanged(
                fromLine, toLine,
                fromChar + 1,
                fromChar + 1 + selectedText.length
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
            editor.insertText(caretLine, caretChar, "    ", this)
            // Сдвигаем каретку вправо на 4
            selectionHandler.selectionChanged(caretLine, caretLine, caretChar + 4, caretChar + 4)
            return
        }

        // Для каждой строки в диапазоне вставляем 4 пробела в начало
        for (line in fromLine..toLine) {
            editor.insertText(line, 0, "    ", this)
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
            val spacesToRemove = text
                .take(char)
                .takeLastWhile { it == ' ' }
                .length
                .coerceAtMost(4)

            if (spacesToRemove > 0) {
                editor.replaceText(
                    line, line,
                    char - spacesToRemove, char,
                    "", this
                )
                // ставим каретку на место после удаления
                selectionHandler.selectionChanged(
                    line, line,
                    char - spacesToRemove, char - spacesToRemove
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
            val count = text
                .takeWhile { it == ' ' }
                .length
                .coerceAtMost(4)

            if (count > 0) {
                editor.replaceText(
                    line, line,
                    0, count,
                    "", this
                )
                if (line == fromLine) removedAtStart = count
                if (line == toLine) removedAtEnd = count
            }
        }

        // Пересчитываем границы выделения, чтобы оно «повисло» на том же тексте
        val newFromChar = (selectionHandler.selectionFromChar - removedAtStart).coerceAtLeast(0)
        val newToChar = (selectionHandler.selectionToChar - removedAtEnd).coerceAtLeast(0)

        selectionHandler.selectionChanged(
            fromLine, toLine,
            newFromChar, newToChar
        )
    }

    private fun editText(text: String) {
        val editor = modifier.editorHandler ?: return
        val caretPos = if (selectionHandler.isEmptySelection) {
            editor.insertText(selectionHandler.selectionCaretLine, selectionHandler.selectionCaretChar, text, this)
        } else {
            editor.replaceText(
                selectionHandler.selectionFromLine, selectionHandler.selectionToLine,
                selectionHandler.selectionFromChar, selectionHandler.selectionToChar,
                text, this
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

        val caretLine: TextLine?
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

        fun applySelectionRange(attributedText: AttributedTextScope, line: TextLine, lineIndex: Int) {
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

            attributedText.modifier
                .selectionRange(selStartPos, selCaretPos)
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
            val startChar = TextCaretNavigation.startOfWord(text, charIndex)
            val caretChar = TextCaretNavigation.endOfWord(text, charIndex)
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
                        selectionCaretChar = TextCaretNavigation.moveWordLeft(newTxt, selectionCaretChar)
                    }
                    if (!select) {
                        selectionStartLine = selectionCaretLine
                        selectionStartChar = selectionCaretChar
                    }

                } else if (wordWise) {
                    selectionCaretChar = TextCaretNavigation.moveWordLeft(txt, selectionCaretChar)
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
                        selectionCaretChar = TextCaretNavigation.moveWordRight(newTxt, selectionCaretChar)
                    }
                    if (!select) {
                        selectionStartLine = selectionCaretLine
                        selectionStartChar = selectionCaretChar
                    }

                } else if (wordWise) {
                    selectionCaretChar = TextCaretNavigation.moveWordRight(txt, selectionCaretChar)
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

            if (startLine != modifier.selectionStartLine
                || caretLine != modifier.selectionCaretLine
                || startChar != modifier.selectionStartChar
                || caretChar != modifier.selectionCaretChar
            ) {

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
        private val KEY_CODE_SELECT_ALL = LocalKeyCode('a')
        private val KEY_CODE_CUT = LocalKeyCode('x')
        private val KEY_CODE_COPY = LocalKeyCode('c')
        private val KEY_CODE_PASTE = LocalKeyCode('v')

        val factory: (UiNode, UiSurface) -> TextAreaNode = { parent, surface -> TextAreaNode(parent, surface) }
    }
}

interface TextLineProvider {
    val size: Int
    val lastIndex: Int get() = size - 1
    operator fun get(index: Int): TextLine
}

class ListTextLineProvider(val lines: MutableList<TextLine> = mutableStateListOf()) : TextLineProvider {
    override val size: Int get() = lines.size
    override operator fun get(index: Int) = lines[index]
}

interface TextEditorHandler {
    fun insertText(line: Int, caret: Int, insertion: String, textAreaScope: ScriptTextAreaScope): Vec2i
    fun replaceText(
        selectionStartLine: Int,
        selectionEndLine: Int,
        selectionStartChar: Int,
        selectionEndChar: Int,
        replacement: String,
        textAreaScope: ScriptTextAreaScope,
    ): Vec2i
}

class DefaultTextEditorHandler(val text: MutableList<TextLine> = mutableStateListOf()) : TextEditorHandler {
    var editAttribs: TextAttributes? = null

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
        val startLine = this[selectionStartLine] ?: return Vec2i(selectionEndChar, selectionEndLine)
        val endLine = this[selectionEndLine] ?: return Vec2i(selectionEndChar, selectionEndLine)
        val before = startLine.before(selectionStartChar)
        val after = endLine.after(selectionEndChar)

        val caretPos = MutableVec2i()
        val attr = editAttribs ?: before.lastAttribs() ?: after.firstAttribs() ?: TextAttributes(
            MsdfFont.DEFAULT_FONT,
            Color.GRAY
        )
        val replaceLines = replacement.toLines(attr)

        caretPos.y = selectionStartLine + replaceLines.lastIndex
        val insertion = if (replaceLines.size == 1) {
            caretPos.x = before.length + replaceLines[0].length
            listOf(before + replaceLines[0] + after)
        } else {
            caretPos.x = replaceLines.last().length
            listOf(before + replaceLines[0]) + replaceLines.subList(
                1,
                replaceLines.lastIndex
            ) + (replaceLines.last() + after)
        }

        insertLines(insertion, selectionStartLine, selectionEndLine)
        return caretPos
    }

    private fun insertLines(insertLines: List<TextLine>, insertFrom: Int, insertTo: Int) {
        val linesBefore = mutableListOf<TextLine>()
        val linesAfter = mutableListOf<TextLine>()
        if (insertFrom > 0) {
            linesBefore += text.subList(0, insertFrom)
        }
        if (insertTo < text.lastIndex) {
            linesAfter += text.subList(insertTo + 1, text.size)
        }

        text.clear()
        text += linesBefore
        text += insertLines
        text += linesAfter
    }

    fun String.toLines(attributes: TextAttributes): List<TextLine> {
        return lines().map { str -> TextLine(listOf(str to attributes)) }
    }

    operator fun get(line: Int): TextLine? {
        return if (text.isEmpty()) {
            null
        } else {
            text[line.clamp(0, text.lastIndex)]
        }
    }

    operator fun TextLine.plus(other: TextLine): TextLine {
        return TextLine(sanitize(spans + other.spans))
    }

    fun TextLine.firstAttribs(): TextAttributes? {
        return if (spans.isNotEmpty()) {
            spans.first().second
        } else {
            null
        }
    }

    fun TextLine.lastAttribs(): TextAttributes? {
        return if (spans.isNotEmpty()) {
            spans.last().second
        } else {
            null
        }
    }

    fun TextLine.before(charIndex: Int): TextLine {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        var i = 0
        var spanI = 0
        while (spanI < spans.size && i + spans[spanI].first.length < charIndex) {
            newSpans += spans[spanI]
            i += spans[spanI].first.length
            spanI++
        }
        newSpans += spans[spanI].before(charIndex - i)
        return TextLine(sanitize(newSpans))
    }

    fun TextLine.after(charIndex: Int): TextLine {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        var i = 0
        var spanI = 0
        while (spanI < spans.size && i + spans[spanI].first.length < charIndex) {
            i += spans[spanI].first.length
            spanI++
        }
        if (spanI < spans.size) {
            newSpans += spans[spanI].after(charIndex - i)
            for (j in spanI + 1 until spans.size) {
                newSpans += spans[j]
            }
        }
        return TextLine(sanitize(newSpans))
    }

    fun TextLine.append(text: String): TextLine {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        newSpans += spans
        newSpans[spans.lastIndex] = spans.last().append(text)
        return TextLine(sanitize(newSpans))
    }

    fun Pair<String, TextAttributes>.before(index: Int): Pair<String, TextAttributes> {
        return first.substring(0, index) to second
    }

    fun Pair<String, TextAttributes>.after(index: Int): Pair<String, TextAttributes> {
        return first.substring(index) to second
    }

    fun Pair<String, TextAttributes>.append(text: String): Pair<String, TextAttributes> {
        return (first + text) to second
    }

    fun sanitize(spans: List<Pair<String, TextAttributes>>): List<Pair<String, TextAttributes>> {
        val newSpans = mutableListOf<Pair<String, TextAttributes>>()
        if (spans.isNotEmpty()) {
            var prevSpan = spans[0]
            newSpans += prevSpan
            for (i in 1 until spans.size) {
                val span = spans[i]
                if (span.second == prevSpan.second) {
                    prevSpan = prevSpan.append(span.first)
                    newSpans[newSpans.lastIndex] = prevSpan
                } else if (span.first.isNotEmpty()) {
                    prevSpan = span
                    newSpans += span
                }
            }
        }
        if (newSpans.size > 1 && newSpans[0].first.isEmpty()) {
            newSpans.removeAt(0)
        }
        return newSpans
    }
}