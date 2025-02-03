package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text

import de.fabmax.kool.input.*
import de.fabmax.kool.input.KeyboardInput.KEY_EV_CHAR_TYPED
import de.fabmax.kool.input.KeyboardInput.KEY_EV_DOWN
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.TriangulatedLineMesh
import de.fabmax.kool.scene.addTriangulatedLineMesh
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.TextCaretNavigation
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.kool.hoverBg
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.currentLine
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys.toEngine
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptError
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.CompletionVariant

class ScriptTextAreaModifier(surface: UiSurface) : TextAreaModifier(surface) {
    val completions by property(mutableListOf<CompletionVariant>())
    val errors by property(mutableListOf<ScriptError>())
    var completionIndex by property(-1)
    var setCompletionIndex: (Int) -> Unit by property { {} }
    var onCharTyped: (KeyEvent) -> Unit by property { {} }
}

var errorMessage = ""

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

        val pos = Minecraft.getInstance().mouseHandler
        if (errorMessage.isNotEmpty()) {
            surface.triggerUpdate()
            Popup(pos.xpos().toFloat(), pos.ypos().toFloat()) {
                modifier.background(UiRenderer { node ->
                    node.apply {
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                            .localRoundRect(0f, 0f, widthPx, heightPx, heightPx * 0.5f, colors.background)
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                            .localRoundRectBorder(
                                0f,
                                0f,
                                widthPx,
                                heightPx,
                                heightPx * 0.5f,
                                sizes.borderWidth.px,
                                colors.hoverBg
                            )
                    }
                })

                Text(errorMessage) {
                    modifier.margin(sizes.smallGap)
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

internal val bracketPairs = mapOf(
    '(' to ')', '[' to ']', '{' to '}',
    '<' to '>', '"' to '"', '\'' to '\''
)

class ScriptTextArea(parent: UiNode?, surface: UiSurface) : TextAreaNode(parent, surface), ScriptTextAreaScope {
    override val modifier: ScriptTextAreaModifier = ScriptTextAreaModifier(surface)

    lateinit var lineProvider: TextLineProvider
    lateinit var completionsList: LazyListState

    override fun onKeyEvent(keyEvent: KeyEvent) {
        val event = keyEvent.toEngine(this)
        EventBus.post(event)
        if (event.isCanceled) return

        val startChar = modifier.getCharBeforeSelection()
        val nextChar = modifier.getCharAfterSelection()

        super.onKeyEvent(keyEvent)

        if (keyEvent.isCharTyped) {
            bracketPairs[keyEvent.localKeyCode.code.toChar()]?.let {
                super.onKeyEvent(it.type())
                modifier.setCaretPos(modifier.selectionStartLine, modifier.selectionStartChar - 1)
            }
        }
        if (keyEvent.keyCode == KeyboardInput.KEY_BACKSPACE && startChar != null) {
            bracketPairs[startChar]?.let {
                if (nextChar != it) return@let
                super.onKeyEvent(KeyEvent(KeyboardInput.KEY_DEL, KeyboardInput.KEY_DEL, KEY_EV_DOWN, 0, it))
            }
        }

        if (keyEvent.isCharTyped ||
            keyEvent.keyCode == KeyboardInput.KEY_BACKSPACE ||
            keyEvent.keyCode == KeyboardInput.KEY_DEL ||
            keyEvent.keyCode == KeyboardInput.KEY_ENTER ||
            (keyEvent.isCtrlDown && keyEvent.localKeyCode in setOf('x', 'v').map(::LocalKeyCode))
        ) {
            modifier.onCharTyped(keyEvent)
        }
    }

    fun Char.type() = KeyEvent(UniversalKeyCode(code), LocalKeyCode(code), KEY_EV_CHAR_TYPED, 0, this)

    override fun setupTextLine(
        scope: UiScope,
        line: TextLine,
        lineIndex: Int,
        textAreaMod: TextAreaModifier,
        lineProvider: TextLineProvider,
    ): UiScope {
        val errors = this@ScriptTextArea.modifier.errors
        val font = MsdfFont(HACK_FONT, 30f)

        val row = scope.Row(Grow.Std, height = font.lineHeight.dp) {
            if (lineIndex == this@ScriptTextArea.modifier.selectionStartLine) {
                modifier.backgroundColor(colors.hoverBg)
            }
            val width = font.textDimensions(lineProvider.size.toString()).width.dp


            errors.find { it.line - 1 == lineIndex }?.let { error ->
                val text = line.text
                val column = error.column - 1
                val startPos = if (text.isEmpty()) 0f else font.textDimensions(
                    text.substring(
                        0,
                        column.coerceAtMost(text.lastIndex)
                    )
                ).width
                val endPos = if (text.isEmpty()) 0f else font.textDimensions(
                    text.substring(
                        0, TextCaretNavigation.endOfWord(
                            line.text,
                            column
                        ).coerceAtMost(text.lastIndex) + 1
                    )
                ).width

                if (error.severity.ordinal > 2) getUiPrimitives().addTriangulatedLineMesh {
                    this.width = 3f
                    this.color = Color.RED

                    val leftPos =
                        uiNode.leftPx + width.value + sizes.gap.value * 4 + sizes.borderWidth.value + sizes.smallGap.value
                    for (i in ((leftPos + startPos).toInt()..(leftPos + endPos).toInt()).step(5)) {
                        val offset = if (i % 2 == 0) 5 else -5
                        addLine(
                            Vec3f(i + 0f, uiNode.bottomPx + offset, 0f),
                            Vec3f(i + 5f, uiNode.bottomPx - offset, 0f)
                        )
                    }

                    val mouse = PointerInput.primaryPointer

                    if (mouse.x in leftPos + startPos..leftPos + endPos && mouse.y in uiNode.topPx..uiNode.bottomPx) {
                        errorMessage = error.message
                    }
                }
            }

            Box(width) {
                Text((lineIndex + 1).toString()) {
                    modifier.font(font).align(AlignmentX.End, AlignmentY.Center)
                }
                modifier.margin(horizontal = sizes.gap * 2)
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

        return row
    }

    companion object {
        val factory: (UiNode, UiSurface) -> ScriptTextArea = { parent, surface -> ScriptTextArea(parent, surface) }
    }
}