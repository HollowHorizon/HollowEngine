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
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// --- 1. Новая структура для Inlay Hints ---
data class InlayHint(
    val index: Int,
    val text: String,
    val color: Color = Color("868A91"),
    val backgroundColor: Color? = Color("2D2E32"),
    val padding: Float = 4f
)

data class ScriptTextLine(val spans: List<Pair<String, TextAttributes>>, var inlayHints: List<InlayHint> = emptyList()) {
    val length: Int = spans.sumOf { it.first.length }
    val text: String
        get() = spans.joinToString("") { (str,_) -> str }

    // Старые методы charIndexToPx оставлены для совместимости,
    // но Node теперь использует свой внутренний Layout с учетом хинтов.
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

    // Временные пропсы для отрисовки
    private val textProps = TextProps(Font.DEFAULT_FONT)
    private val textOrigin = MutableVec2f()

    // Состояние каретки
    private var dragStartFrame = 0
    private var caretBlink = 0f
    private val isCaretBlink = mutableStateOf(false)
    private val caretWidth = Dp(1f)

    // Кэш геометрии текста (для Kool UI)
    private val textCache = mutableListOf<CachedTextGeometry>()

    // --- Исправленный Layout ---
    private inner class TextLayout {
        // Массив позиций каждого символа + 1 (для конца строки).
        // Инициализируем пустым, будем ресайзить в measure.
        var charPositions = FloatArray(0)

        var totalWidth = 0f
        var totalHeight = 0f
        var baselineY = 0f

        fun measure(ctx: KoolContext) {
            val textLen = modifier.text.length
            // РЕСАЙЗ МАССИВА: Если длина текста изменилась, пересоздаем массив
            if (charPositions.size != textLen + 1) {
                charPositions = FloatArray(textLen + 1)
            }

            var x = 0f
            var maxH = 0f
            var maxB = 0f

            // Группируем хинты для быстрого поиска по индексу символа
            val hintsByIndex = modifier.text.inlayHints.groupBy { it.index }
            var globalCharIndex = 0

            // Проходим по всем кускам текста (spans)
            modifier.text.spans.forEach { (txt, attr) ->
                surface.applyFontScale(attr.font, ctx)
                val fontMetrics = attr.font.textDimensions(txt)
                maxH = max(maxH, attr.font.lineHeight)
                maxB = max(maxB, fontMetrics.ascentPx)

                for (char in txt) {
                    // Записываем начало блока "Хинт + Символ"
                    charPositions[globalCharIndex] = x

                    // 1. Сдвигаем x на ширину хинтов (если есть)
                    val hints = hintsByIndex[globalCharIndex]
                    if (hints != null) {
                        for (hint in hints) {
                            val hintW = attr.font.derive(attr.font.sizePts*(0.75f * UiScale.windowScale.use())).textDimensions(hint.text).width + hint.padding * 2
                            x += hintW
                        }
                    }

                    // 2. Сдвигаем x на ширину символа
                    x += attr.font.charWidth(char)
                    globalCharIndex++
                }
            }

            // Обработка хинтов в самом конце строки (после последнего символа)
            charPositions[globalCharIndex] = x
            val endHints = hintsByIndex[globalCharIndex]
            if (endHints != null) {
                val font = modifier.text.spans.lastOrNull()?.second?.font ?: Font.DEFAULT_FONT as MsdfFont
                for (hint in endHints) {
                    val hintW = font.derive(font.sizePts*(0.75f * UiScale.windowScale.use())).textDimensions(hint.text).width + hint.padding * 2
                    x += hintW
                }
                // Если были хинты в конце, обновляем позицию "конца", чтобы ширина виджета их учла
                // Но для charPositions[last] мы обычно храним позицию курсора.
                // Если курсор в конце, он должен быть ПЕРЕД хинтами конца? Или после?
                // Обычно inlay hints в конце строки не двигают курсор. Оставим x увеличенным для totalWidth.
            }

            totalWidth = x
            totalHeight = maxH
            baselineY = maxB
        }
    }

    private val layout = TextLayout()

    override fun measureContentSize(ctx: KoolContext) {
        // Синхронизируем размер кэша геометрии с количеством спанов
        while (textCache.size > modifier.text.spans.size) textCache.removeAt(textCache.lastIndex)
        while (textCache.size < modifier.text.spans.size) textCache += CachedTextGeometry(this)

        // Пересчитываем координаты
        layout.measure(ctx)

        val modWidth = modifier.width
        val modHeight = modifier.height

        // Используем рассчитанные размеры
        val measuredWidth = if (modWidth is Dp) modWidth.px else layout.totalWidth + caretWidth.px + paddingStartPx + paddingEndPx
        val measuredHeight = if (modHeight is Dp) modHeight.px else layout.totalHeight + paddingTopPx + paddingBottomPx
        setContentSize(measuredWidth, measuredHeight)
    }

    override fun render(ctx: KoolContext) {
        super.render(ctx)

        // Расчет начала координат текста (Align)
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

        // --- 1. Отрисовка Выделения ---
        if (modifier.caretPos != modifier.selectionStart || modifier.hasSelection) {
            val minIndex = min(modifier.caretPos, modifier.selectionStart)
            val maxIndex = max(modifier.caretPos, modifier.selectionStart)

            val x1 = charIndexToLocalX(minIndex)
            // Фикс "пустой строки": если выделение до конца текста, тянем до края виджета
            val x2 = if (maxIndex == modifier.text.length && modifier.hasMultilineSelection) {
                widthPx - paddingEndPx
            } else {
                charIndexToLocalX(maxIndex)
            }
            // Высота выделения
            val h = innerHeightPx
            getUiPrimitives().localRect(x1, paddingTopPx, max(x2 - x1, 2f), h, modifier.selectionColor)
        }

        // --- 2. Отрисовка Текста и Хинтов ---
        val hintsByIndex = modifier.text.inlayHints.groupBy { it.index }
        var globalCharIndex = 0

        modifier.text.spans.forEachIndexed { spanIndex, (txt, attr) ->
            val cache = textCache[spanIndex]

            // Настраиваем builder (но будем добавлять символы вручную по координатам)
            val builder = getTextBuilder(attr.font)
            textProps.font = attr.font
            textProps.isYAxisUp = false

            for (char in txt) {
                // Базовая позиция блока (где стоит курсор перед символом)
                var currentDrawX = textOrigin.x + layout.charPositions[globalCharIndex]

                // Рисуем хинты, если они есть перед этим символом
                hintsByIndex[globalCharIndex]?.forEach { hint ->
                    val w = drawInlayHint(hint, currentDrawX, attr.font)
                    currentDrawX += w // Сдвигаем позицию отрисовки символа вправо
                }

                // Костыль, чтобы выделение не символа задевало инлайн подсказки
                attr.background?.let { bg ->
                    val endX = textOrigin.x + layout.charPositions[globalCharIndex + 1]
                    getUiPrimitives(UiSurface.LAYER_BACKGROUND).localRect(currentDrawX, paddingTopPx, endX - currentDrawX, innerHeightPx, bg)
                }

                // Рисуем сам символ
                textProps.text = char.toString()
                textProps.origin.set(currentDrawX, textOrigin.y, 0f)
                builder.configured(attr.color) { text(textProps) }

                globalCharIndex++
            }
        }

        // Отрисовка хинтов в самом конце текста (если есть)
        val endX = textOrigin.x + layout.charPositions[globalCharIndex]
        var endHintDrawX = endX
        hintsByIndex[globalCharIndex]?.forEach { hint ->
            val font = modifier.text.spans.lastOrNull()?.second?.font ?: Font.DEFAULT_FONT as MsdfFont
            val w = drawInlayHint(hint, endHintDrawX, font)
            endHintDrawX += w
        }

        // --- 3. Отрисовка Каретки ---
        if (modifier.isCaretVisible && surface.isFocused.use()) {
            surface.onEachFrame(::updateCaretBlinkState)
            if (isCaretBlink.use()) {
                val caretX = charIndexToLocalX(modifier.caretPos)
                getUiPrimitives().localRect(caretX, paddingTopPx, caretWidth.px, innerHeightPx, modifier.caretColor)
            }
        }
    }

    private fun drawInlayHint(hint: InlayHint, x: Float, mainFont: MsdfFont): Float {
        val dims = mainFont.derive(mainFont.sizePts*(0.75f * UiScale.windowScale.use())).textDimensions(hint.text)
        val w = dims.width + hint.padding * 2
        val h = dims.height + hint.padding

        // Центрируем хинт по вертикали относительно базовой линии
        val yBase = textOrigin.y
        // Смещение вверх, чтобы центр хинта совпадал с центром строки (примерно)
        val y = yBase - (dims.yBaseline) // yBaseline

        // Фон хинта
        if (hint.backgroundColor != null) {
            // Рисуем прямоугольник фона. Координаты подбираются "на глаз" под размер шрифта
            getUiPrimitives().localRoundRect(x, paddingTopPx + (innerHeightPx - h)/2f, w, h, sizes.smallGap.px * 0.5f, hint.backgroundColor)
        }

        // Текст хинта.
        // ВАЖНО: Мы используем тот же шрифт, но Kool не позволяет легко скейлить addTextGeometry внутри одного батча без шейдеров.
        // Поэтому здесь мы рисуем текст как есть, но это место для оптимизации (нужен отдельный мелкий шрифт).
        // Для демонстрации я просто добавлю текст. Если шрифт поддерживает scale через uniform, это сработает.

        // Чтобы реально уменьшить текст, нужно либо использовать Matrix stack, либо отдельный Font объект с меньшим size.
        // Здесь используем хак: предполагаем, что пользователь передаст мелкий шрифт, или смиримся с размером.
        // Для примера оставим размер как есть, но учтем отступы.

        val builder = getTextBuilder(mainFont)
        textProps.font = mainFont.copy(mainFont.sizePts*(0.75f * UiScale.windowScale.use()))
        textProps.text = hint.text
        // Небольшая корректировка позиции текста внутри фона
        textProps.origin.set(x + hint.padding, textOrigin.y - y / 3f, 0f)

        // Если бы мы могли задать scale: textProps.transform.scale((0.75f * UiScale.windowScale.use())) - но TextProps так не умеет напрямую в batcher
        builder.configured(hint.color) { text(textProps) }

        textProps.font = mainFont
        return w
    }

    override fun charIndexToLocalX(charIndex: Int): Float {
        if (layout.charPositions.isEmpty()) return textOrigin.x
        val idx = charIndex.coerceIn(0, layout.charPositions.size - 1)
        return textOrigin.x + layout.charPositions[idx]
    }

    override fun charIndexFromLocalX(localX: Float): Int {
        if (layout.charPositions.isEmpty()) return 0
        val relX = localX - textOrigin.x

        // Бинарный поиск ближайшего индекса
        val idx = layout.charPositions.binarySearch(relX)
        if (idx >= 0) return idx

        val insertionPoint = -(idx + 1)
        if (insertionPoint == 0) return 0
        if (insertionPoint >= layout.charPositions.size) return layout.charPositions.lastIndex

        val prevX = layout.charPositions[insertionPoint - 1]
        val nextX = layout.charPositions[insertionPoint]

        // Выбираем, к какому символу ближе клик.
        // Учтите: если между prev и next стоит большой Hint, клик по Хинту отнесет курсор к ближайшему краю.
        return if (abs(relX - prevX) < abs(relX - nextX)) insertionPoint - 1 else insertionPoint
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