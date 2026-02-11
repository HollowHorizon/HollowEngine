package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.geometry.TextProps
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Font
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.Time
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class InlayHint(
    val index: Int,
    val text: String,
    val color: Color = Color("868A91"),
    val backgroundColor: Color? = Color("2D2E32"),
    val padding: Float = 4f
)

data class ScriptTextLine(val spans: List<Pair<String, TextAttributes>>, var inlayHints: List<InlayHint> = emptyList()) {
    val length: Int = spans.sumOf { it.first.length }

    // Вспомогательное свойство для получения чистого текста без спанов
    val text: String
        get() = spans.joinToString("") { (str,_) -> str }

    fun charIndexToPx(charIndex: Int): Float {
        var x = 0f
        var i = charIndex
        for (s in spans.indices) {
            val (txt, attr) = spans[s]
            for (j in 0 until min(txt.length, i)) {
                if (i-- == 0) return x
                x += attr.font.charWidth(txt[j])
            }
        }
        return x
    }

    fun charIndexFromPx(px: Float): Int {
        var x = 0f
        var i = 0
        for (s in spans.indices) {
            val (txt, attr) = spans[s]
            for (j in txt.indices) {
                val w = attr.font.charWidth(txt[j])
                if (x + w >= px) {
                    return if (abs(x - px) < abs(x + w - px)) i else i + 1
                }
                x += w
                i++
            }
        }
        return i
    }

    override fun toString(): String = spans.joinToString { "\"${it.first}\"" }

    companion object {
        val EMPTY = ScriptTextLine(emptyList(), emptyList())
    }
}

data class TextAttributes(
    val font: MsdfFont,
    val color: Color,
    val background: Color? = null
)

interface AttributedTextScope : UiScope {
    override val modifier: AttributedTextModifier

    fun charIndexFromLocalX(localX: Float): Int
    fun charIndexToLocalX(charIndex: Int): Float
}

open class AttributedTextModifier(surface: UiSurface) : UiModifier(surface) {
    var text: ScriptTextLine by property(ScriptTextLine.EMPTY)
    var textAlignX: AlignmentX by property(AlignmentX.Start)
    var textAlignY: AlignmentY by property(AlignmentY.Center)

    var caretPos: Int by property(0)
    var hasSelection: Boolean by property(false)
    var hasMultilineSelection: Boolean by property(false)
    var selectionStart: Int by property(0)
    var caretColor: Color by property { it.colors.onBackground }
    var isCaretVisible: Boolean by property(false)
    var selectionColor: Color by property { it.colors.primaryAlpha(0.5f) }
    var onSelectText: ((Int, Int) -> Unit)? by property(null)
}

fun <T: AttributedTextModifier> T.textAlignX(alignment: AlignmentX): T { textAlignX = alignment; return this }
fun <T: AttributedTextModifier> T.textAlignY(alignment: AlignmentY): T { textAlignY = alignment; return this }
fun <T: AttributedTextModifier> T.isCaretVisible(flag: Boolean): T { isCaretVisible = flag; return this }
fun <T: AttributedTextModifier> T.onSelectText(block: ((Int, Int) -> Unit)?): T { onSelectText = block; return this }
fun <T: AttributedTextModifier> T.cursorPos(cursor: Int): T {
    caretPos = cursor
    selectionStart = cursor
    return this
}
fun <T: AttributedTextModifier> T.selectionRange(start: Int, cursor: Int, hasSelection: Boolean, hasMultilineSelection: Boolean): T {
    caretPos = cursor
    selectionStart = start
    this.hasSelection = hasSelection
    this.hasMultilineSelection = hasMultilineSelection
    return this
}

inline fun UiScope.AttributedText(
    text: ScriptTextLine,
    scopeName: String? = null,
    block: AttributedTextScope.() -> Unit = { }
): AttributedTextScope {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }

    val textNd = uiNode.createChild(scopeName, AttributedTextNode::class, AttributedTextNode.factory)
    textNd.modifier.text = text
    textNd.modifier
        .onClick(textNd)
        .hoverListener(textNd)
        .dragListener(textNd)
    textNd.block()
    return textNd
}

open class AttributedTextNode(parent: UiNode?, surface: UiSurface)
    : UiNode(parent, surface), AttributedTextScope, Clickable, Hoverable, Draggable
{
    override val modifier = AttributedTextModifier(surface)

    private val textProps = TextProps(Font.DEFAULT_FONT)
    private val textOrigin = MutableVec2f()

    private var dragStartFrame = 0
    private var caretBlink = 0f
    private val isCaretBlink = mutableStateOf(false)
    private val caretWidth = Dp(1f)

    private val textCache = mutableListOf<CachedTextGeometry>()

    private inner class TextLayout {
        var charPositions = FloatArray(0)
        var glyphPositions = FloatArray(0)

        var totalWidth = 0f
        var totalHeight = 0f
        var baselineY = 0f

        fun measure(ctx: KoolContext) {
            val textLen = modifier.text.length
            if (charPositions.size != textLen + 1) {
                charPositions = FloatArray(textLen + 1)
                glyphPositions = FloatArray(textLen + 1)
            }

            var x = 0f
            var maxH = 0f
            var maxB = 0f

            val hintsByIndex = modifier.text.inlayHints.groupBy { it.index }
            var globalCharIndex = 0

            modifier.text.spans.forEach { (txt, attr) ->
                surface.applyFontScale(attr.font, ctx)
                val fontMetrics = attr.font.textDimensions(txt)
                maxH = max(maxH, attr.font.lineHeight)
                maxB = max(maxB, fontMetrics.ascentPx)

                for (char in txt) {
                    charPositions[globalCharIndex] = x

                    hintsByIndex[globalCharIndex]?.forEach { hint ->
                        val hintW = attr.font.derive(attr.font.sizePts*(0.75f * UiScale.windowScale.use())).textDimensions(hint.text).width + hint.padding * 2
                        x += hintW
                    }

                    glyphPositions[globalCharIndex] = x

                    x += attr.font.charWidth(char)
                    globalCharIndex++
                }
            }

            if (maxH == 0f) {
                val fallbackFont = MsdfFont(ColorTheme.Fonts.MONOCRAFT, 18f)
                surface.applyFontScale(fallbackFont, ctx)
                maxH = fallbackFont.lineHeight
                maxB = fallbackFont.textDimensions("W").ascentPx
            }

            // Обработка конца строки
            charPositions[globalCharIndex] = x
            hintsByIndex[globalCharIndex]?.forEach { hint ->
                val font = modifier.text.spans.lastOrNull()?.second?.font ?: Font.DEFAULT_FONT as MsdfFont
                val hintW = font.derive(font.sizePts*(0.75f * UiScale.windowScale.use())).textDimensions(hint.text).width + hint.padding * 2
                x += hintW
            }
            glyphPositions[globalCharIndex] = x

            totalWidth = x
            totalHeight = maxH
            baselineY = maxB
        }
    }

    private val layout = TextLayout()

    override fun measureContentSize(ctx: KoolContext) {
        while (textCache.size > modifier.text.spans.size) textCache.removeAt(textCache.lastIndex)
        while (textCache.size < modifier.text.spans.size) textCache += CachedTextGeometry(this)

        layout.measure(ctx)

        val modWidth = modifier.width
        val modHeight = modifier.height

        val measuredWidth = if (modWidth is Dp) modWidth.px else layout.totalWidth + caretWidth.px + paddingStartPx + paddingEndPx
        val measuredHeight = if (modHeight is Dp) modHeight.px else layout.totalHeight + paddingTopPx + paddingBottomPx
        setContentSize(measuredWidth, measuredHeight)
    }

    override fun render(ctx: KoolContext) {
        super.render(ctx)

        textOrigin.x = when (modifier.textAlignX) {
            AlignmentX.Start -> paddingStartPx
            AlignmentX.Center -> (widthPx - layout.totalWidth) / 2f
            AlignmentX.End -> widthPx - layout.totalWidth - caretWidth.px - paddingEndPx
        }
        textOrigin.y = layout.baselineY + when (modifier.textAlignY) {
            AlignmentY.Top -> paddingTopPx
            AlignmentY.Center -> (heightPx - layout.totalHeight) / 2f
            AlignmentY.Bottom -> heightPx - layout.totalHeight - paddingBottomPx
        }

        // Границы выделения
        val selectionMin = min(modifier.caretPos, modifier.selectionStart)
        val selectionMax = max(modifier.caretPos, modifier.selectionStart)
        val isSelectionActive = (modifier.caretPos != modifier.selectionStart) || modifier.hasSelection

        val hintsByIndex = modifier.text.inlayHints.groupBy { it.index }
        var globalCharIndex = 0

        modifier.text.spans.forEachIndexed { spanIndex, (txt, attr) ->
            val builder = getTextBuilder(attr.font)
            textProps.font = attr.font
            textProps.isYAxisUp = false

            for (char in txt) {
                val hintStartDrawX = textOrigin.x + layout.charPositions[globalCharIndex]
                val glyphStartDrawX = textOrigin.x + layout.glyphPositions[globalCharIndex]
                val charEndDrawX = textOrigin.x + layout.charPositions[globalCharIndex + 1]

                var currentHintX = hintStartDrawX
                hintsByIndex[globalCharIndex]?.forEach { hint ->
                    val w = drawInlayHint(hint, currentHintX, attr.font)
                    currentHintX += w
                }

                if (isSelectionActive) {
                    val isCharSelected = (globalCharIndex in selectionMin until selectionMax)

                    if (isCharSelected) {
                        val h = innerHeightPx
                        getUiPrimitives().localRect(glyphStartDrawX, paddingTopPx, charEndDrawX - glyphStartDrawX, h, modifier.selectionColor)
                    }
                }

                attr.background?.let { bg ->
                    getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRect(glyphStartDrawX, paddingTopPx, charEndDrawX - glyphStartDrawX, innerHeightPx, bg)
                }

                textProps.text = char.toString()
                textProps.origin.set(glyphStartDrawX, textOrigin.y, 0f)
                builder.configured(attr.color) { text(textProps) }

                globalCharIndex++
            }
        }

        if (isSelectionActive && selectionMax == modifier.text.length && (modifier.hasMultilineSelection)) {
            val lastX = textOrigin.x + layout.glyphPositions[globalCharIndex]
            val tailWidth = widthPx - paddingEndPx - lastX
            if (tailWidth > 0) {
                getUiPrimitives().localRect(lastX, paddingTopPx, tailWidth, innerHeightPx, modifier.selectionColor)
            }
        }

        var endHintX = textOrigin.x + layout.charPositions[globalCharIndex]
        hintsByIndex[globalCharIndex]?.forEach { hint ->
            val font = modifier.text.spans.lastOrNull()?.second?.font ?: Font.DEFAULT_FONT as MsdfFont
            val w = drawInlayHint(hint, endHintX, font)
            endHintX += w
        }

        if (modifier.isCaretVisible && surface.isFocused.use()) {
            surface.onEachFrame(::updateCaretBlinkState)
            if (isCaretBlink.use()) {
                val caretX = charIndexToLocalX(modifier.caretPos)
                getUiPrimitives().localRect(caretX, paddingTopPx, caretWidth.px, innerHeightPx, modifier.caretColor)
            }
        }
    }

    private fun drawInlayHint(hint: InlayHint, x: Float, mainFont: MsdfFont): Float {
        val scale = 0.75f * UiScale.windowScale.use()
        val derivedFont = mainFont.derive(mainFont.sizePts * scale)
        val dims = derivedFont.textDimensions(hint.text)

        val w = dims.width + hint.padding * 2
        val h = dims.height + hint.padding

        val yBase = textOrigin.y
        val y = yBase - dims.yBaseline

        if (hint.backgroundColor != null) {
            getUiPrimitives().localRoundRect(x, paddingTopPx + (innerHeightPx - h)/2f, w, h, sizes.smallGap.px * 0.5f, hint.backgroundColor)
        }

        val builder = getTextBuilder(mainFont)
        textProps.font = mainFont.copy(mainFont.sizePts * scale)
        textProps.text = hint.text
        textProps.origin.set(x + hint.padding, textOrigin.y - y / 3f, 0f) // Небольшая коррекция высоты наугад :D

        builder.configured(hint.color) { text(textProps) }

        textProps.font = mainFont
        return w
    }

    override fun charIndexToLocalX(charIndex: Int): Float {
        if (layout.glyphPositions.isEmpty()) return textOrigin.x
        val idx = charIndex.coerceIn(0, layout.glyphPositions.size - 1)
        return textOrigin.x + layout.glyphPositions[idx]
    }

    override fun charIndexFromLocalX(localX: Float): Int {
        if (layout.charPositions.isEmpty()) return 0
        val relX = localX - textOrigin.x

        val idx = layout.charPositions.binarySearch(relX)
        if (idx >= 0) return idx

        val insertionPoint = -(idx + 1)
        val slotIndex = (insertionPoint - 1).coerceAtLeast(0)

        if (slotIndex >= layout.charPositions.size - 1) return layout.charPositions.lastIndex

        val glyphStart = layout.glyphPositions[slotIndex]
        val glyphEnd = layout.charPositions[slotIndex + 1]

        val charWidth = glyphEnd - glyphStart
        val glyphCenter = glyphStart + charWidth / 2f

        return if (relX < glyphCenter) slotIndex else slotIndex + 1
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateCaretBlinkState(ctx: KoolContext) {
        if (modifier.isCaretVisible) {
            caretBlink -= Time.deltaT
            if (caretBlink < 0f) {
                isCaretBlink.set(!isCaretBlink.value)
                caretBlink += 0.5f
                if (caretBlink < 0f) caretBlink = 0.5f
            }
        } else {
            caretBlink = 0f
            isCaretBlink.set(false)
        }
    }

    fun resetCaretBlinkState() {
        caretBlink = 0.5f
        isCaretBlink.set(true)
    }

    override fun onHover(ev: PointerEvent) {
        PointerInput.cursorShape = CursorShape.TEXT
    }

    override fun onClick(ev: PointerEvent) {
        val txtI = charIndexFromLocalX(ev.position.x)
        modifier.onSelectText?.invoke(txtI, txtI)
        resetCaretBlinkState()
        dragStartFrame = Time.frameCount
    }

    override fun onDragStart(ev: PointerEvent) = onClick(ev)

    override fun onDrag(ev: PointerEvent) {
        PointerInput.cursorShape = CursorShape.TEXT
        if (Time.frameCount > dragStartFrame) {
            val txtI = charIndexFromLocalX(ev.position.x)
            if (txtI != modifier.caretPos) {
                modifier.onSelectText?.invoke(txtI, modifier.selectionStart)
            }
        }
    }

    companion object {
        val factory: (UiNode, UiSurface) -> AttributedTextNode = { parent, surface -> AttributedTextNode(parent, surface) }
    }
}