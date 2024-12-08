package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.editor.ui.backgroundMid
import de.fabmax.kool.editor.ui.hoverBg
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.LocalKeyCode
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionVariant

class ScriptTextAreaModifier(surface: UiSurface) : TextAreaModifier(surface) {
    val completions by property(mutableListOf<CompletionVariant>())
    var completionIndex by property(-1)
    var setCompletionIndex: (Int) -> Unit by property { {} }
    var onCharTyped: (KeyEvent) -> Unit by property { {} }
}

interface ScriptTextAreaScope : TextAreaScope {
    override val modifier: ScriptTextAreaModifier
}

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
    val textArea = uiNode.createChild(scopeName, ScriptTextArea::class, ScriptTextArea.factory)
    textArea.listState = state
    textArea.modifier
        .size(width, height)
        .onWheelX { state.scrollDpX(it.pointer.deltaScrollX.toFloat() * -20f) }
        .onWheelY { state.scrollDpY(it.pointer.deltaScrollY.toFloat() * -50f) }

    var completionIndex: Int by textArea.remember(0)
    textArea.modifier.completionIndex = completionIndex
    textArea.modifier.setCompletionIndex = {
        completionIndex = it
    }

    textArea.lineProvider = lineProvider
    textArea.setupContent(
        lineProvider,
        withVerticalScrollbar,
        withHorizontalScrollbar,
        scrollbarColor,
        scrollPaneModifier,
        vScrollbarModifier,
        hScrollbarModifier
    ) {
        val scriptScope = this as ScriptTextAreaScope
        block(scriptScope)
        val completions = textArea.modifier.completions
        if (completions.isNotEmpty()) {
            val font = MsdfFont(HACK_FONT, 30f)
            val width = lineProvider[currentLine].charIndexToPx(modifier.selectionCaretChar.coerceAtLeast(0))

            Popup(
                uiNode.leftPx + width + uiNode.paddingStartPx,
                uiNode.topPx + (modifier.selectionStartLine + 1) * font.lineHeight + sizes.gap.px
            ) {
                modifier.padding(sizes.smallGap)
                    .height(
                        Grow(
                            1f,
                            max = Dp(
                                (sizes.normalText.lineHeight + sizes.smallGap.px * 2) * completions.size.coerceAtMost(10) + sizes.smallGap.px * 2
                            )
                        )
                    )
                    .width(Grow(1f, max = FitContent))
                    .background(RoundRectBackground(colors.backgroundMid, sizes.gap))
                    .border(RoundRectBorder(colors.hoverBg, sizes.gap, 3.dp))
                    .zLayer(UiSurface.LAYER_POPUP)

                LazyList(
                    withVerticalScrollbar = true,
                    withHorizontalScrollbar = true,
                    isScrollableHorizontal = true,
                    vScrollbarModifier = {
                        it.width(10.dp).margin(5.dp).zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                    },
                    hScrollbarModifier = {
                        it.height(10.dp).margin(5.dp).zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                    },
                ) {
                    textArea.completionsList = (this as LazyListNode).state
                    itemsIndexed(completions) { index, completion ->
                        completion.apply { create(textArea, scriptScope.modifier.completionIndex == index) }
                    }
                }
            }
        }
    }
}

class ScriptTextArea(parent: UiNode?, surface: UiSurface) : TextAreaNode(parent, surface), ScriptTextAreaScope {
    override val modifier: ScriptTextAreaModifier = ScriptTextAreaModifier(surface)

    lateinit var lineProvider: TextLineProvider
    lateinit var completionsList: LazyListState

    override fun onKeyEvent(keyEvent: KeyEvent) {
        val completions = modifier.completions

        if (completions.isNotEmpty() && keyEvent.isPressed) {
            when (keyEvent.keyCode) {
                KeyboardInput.KEY_ENTER, KeyboardInput.KEY_TAB -> {
                    if (modifier.completionIndex == -1) return
                    modifier.completions[modifier.completionIndex].use(this)
                    return
                }

                KeyboardInput.KEY_CURSOR_UP -> {
                    if (modifier.completionIndex > 0) {
                        completionsList.scrollToItem.set(modifier.completionIndex - 1)
                        modifier.setCompletionIndex(modifier.completionIndex - 1)
                    }
                    return
                }

                KeyboardInput.KEY_CURSOR_DOWN -> {
                    if (modifier.completionIndex < completions.size - 1) {
                        completionsList.scrollToItem.set(modifier.completionIndex + 1)
                        modifier.setCompletionIndex(modifier.completionIndex + 1)
                    }
                    return
                }

                KeyboardInput.KEY_ESC -> {
                    modifier.completions.clear()
                    return
                }

                else -> {}
            }
        }

        super.onKeyEvent(keyEvent)
        if (keyEvent.isCharTyped || keyEvent.keyCode == KeyboardInput.KEY_BACKSPACE || (keyEvent.isCtrlDown && keyEvent.localKeyCode == LocalKeyCode('v'))) modifier.onCharTyped(keyEvent)

    }

    override fun setupTextLine(
        scope: UiScope,
        line: TextLine,
        lineIndex: Int,
        textAreaMod: TextAreaModifier,
        lineProvider: TextLineProvider,
    ): UiScope = scope.Row(Grow.Std, height = MsdfFont(HACK_FONT, 30f).lineHeight.dp) {
        if(lineIndex == this@ScriptTextArea.modifier.selectionStartLine) {
            modifier.backgroundColor(colors.hoverBg)
        }

        Box(MsdfFont(HACK_FONT, 30f).textDimensions(lineProvider.size.toString()).width.dp) {
            Text((lineIndex + 1).toString()) {
                modifier.font(MsdfFont(HACK_FONT, 30f)).align(AlignmentX.End, AlignmentY.Center)
            }
            modifier.margin(horizontal = sizes.gap*2)
        }
        Box(sizes.borderWidth, Grow.Std) {
            modifier
                .backgroundColor(colors.secondaryVariant)
                .alignY(AlignmentY.Center)
        }
        super.setupTextLine(this, line, lineIndex, textAreaMod, lineProvider).apply {
            modifier.alignY(AlignmentY.Center).margin(start = sizes.smallGap).alignY(AlignmentY.Top)
        }
    }

    companion object {
        val factory: (UiNode, UiSurface) -> ScriptTextArea = { parent, surface -> ScriptTextArea(parent, surface) }
    }
}