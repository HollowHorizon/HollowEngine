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
        .onWheelX { state.scrollDpX(it.pointer.delta.x * -20f) }
        .onWheelY { state.scrollDpY(it.pointer.delta.y * -50f) }

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
            val font = MsdfFont(HACK_FONT, 10f)
            val width = lineProvider[currentLine].charIndexToPx(modifier.selectionCaretChar.coerceAtLeast(0))

            Popup(
                uiNode.leftPx + width + uiNode.paddingStartPx,
                uiNode.topPx + (modifier.selectionStartLine + 1) * font.lineHeight + sizes.gap.px
            ) {
                modifier.padding(sizes.smallGap*0.5f)
                    .height(
                        Grow(
                            1f,
                            max = Dp(
                                (sizes.normalText.lineHeight + sizes.smallGap.px * 2) * completions.size.coerceAtMost(10) + sizes.smallGap.px * 2
                            )
                        )
                    )
                    .width(Grow(1f, max = FitContent))
                    .background(RoundRectBackground(colors.backgroundMid, sizes.smallGap))
                    .border(RoundRectBorder(colors.primaryVariant, sizes.smallGap, sizes.borderWidth))
                    .zLayer(UiSurface.LAYER_POPUP)

                LazyList(
                    withVerticalScrollbar = true,
                    withHorizontalScrollbar = false,
                    isScrollableHorizontal = true,
                    vScrollbarModifier = {
                        it.width(sizes.smallGap).margin(sizes.smallGap*0.5f).zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                    },
                    hScrollbarModifier = {
                        it.height(sizes.smallGap).margin(sizes.smallGap*0.5f).zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                    },
                    scrollPaneModifier = {
                        it.allowOverscrollY = false
                    }
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

                modifier.width(Grow(1f, max=FitContent))

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
        val font = MsdfFont(HACK_FONT, 18f)

        val row = scope.Row(Grow.Std) {

            val width = font.textDimensions(lineProvider.size.toString()).width.dp


            errors.find { it.line - 1 == lineIndex }?.let { error ->
                val text = line.text
                val column = error.column - 1
                val startPos = if (text.isEmpty()) 0f else font.textDimensions(
                    text.substring(
                        0,
                        column.coerceAtMost(text.lastIndex)
                    )
                ).width.dp.px
                val endPos = if (text.isEmpty()) 0f else font.textDimensions(
                    text.substring(
                        0, TextCaretNavigation.endOfWord(
                            line.text,
                            column
                        ).coerceAtMost(text.lastIndex) + 1
                    )
                ).width.dp.px

                if (error.severity.ordinal > 2) getUiPrimitives().addTriangulatedLineMesh {
                    this.width = 3f
                    this.color = Color.RED

                    val leftPos = uiNode.leftPx + width.px + sizes.smallGap.px * 3f + sizes.borderWidth.px
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

            Box(width) {
                modifier.margin(horizontal = sizes.smallGap)

                Text((lineIndex + 1).toString()) {
                    modifier.font(font).align(AlignmentX.End, AlignmentY.Center)
                }
            }

            Box(sizes.borderWidth, Grow.Std) {
                modifier
                    .backgroundColor(Color("3C3C4AFF"))
                    .alignY(AlignmentY.Center)
            }
            super.setupTextLine(this, line, lineIndex, textAreaMod, lineProvider).apply {
                modifier.alignY(AlignmentY.Center).margin(start = sizes.smallGap*0.5f).alignY(AlignmentY.Top)
                modifier.padding(sizes.smallGap*0.5f)
                if (lineIndex == this@ScriptTextArea.modifier.selectionStartLine) {
                    modifier.border(RoundRectBorder(Color("3C3C4AFF"), sizes.smallGap, sizes.borderWidth))
                }
            }
        }

        return row
    }

    companion object {
        val factory: (UiNode, UiSurface) -> ScriptTextArea = { parent, surface -> ScriptTextArea(parent, surface) }
    }
}