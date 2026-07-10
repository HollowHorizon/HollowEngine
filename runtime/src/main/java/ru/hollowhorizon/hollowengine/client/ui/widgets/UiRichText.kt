package ru.hollowhorizon.hollowengine.client.ui.widgets

import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.text.*

data class UiRichText(
    val items: List<UiInlineItem>,
) {
    companion object {
        fun plain(text: String): UiRichText = UiRichText(listOf(UiInlineItem.Text(text)))
    }
}

data class UiInlineWidgetMetrics(
    val width: Float,
    val height: Float,
)

sealed interface UiInlineItem {
    data class Text(
        val value: String,
        val style: UiInlineStyle = UiInlineStyle.Empty,
    ) : UiInlineItem

    data class Image(
        val source: String,
        val width: Float,
        val height: Float,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiInlineItem

    data class Widget(
        val id: String,
        val width: Float,
        val height: Float,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
        val sourceLength: Int = alt.ifBlank { "\uFFFC" }.length,
    ) : UiInlineItem
}

data class UiInlineStyle(
    val effects: List<UiTextEffect> = emptyList(),
    val background: UiColor? = null,
) {

    companion object {
        val Empty = UiInlineStyle()
    }

    fun resolvedFontSize(baseFontSize: Float): Float = fontSize ?: baseFontSize.coerceAtLeast(0.0001f)
}

val UiInlineStyle.bold: Boolean get() = effects.any { it is Bold }
val UiInlineStyle.italic: Boolean get() = effects.any { it is Italic }
val UiInlineStyle.underline: Boolean get() = effects.any { it is Underline }
val UiInlineStyle.strikethrough: Boolean get() = effects.any { it is Strikethrough }
val UiInlineStyle.code: Boolean get() = effects.any { it is Code }
val UiInlineStyle.link: String? get() = effects.lastEffect<Link>()?.url
val UiInlineStyle.color: UiColor? get() = effects.lastEffect<TextColor>()?.value
val UiInlineStyle.fontSize: Float? get() = effects.lastEffect<TextSize>()?.value
val UiInlineStyle.fontFamily: String? get() = effects.lastEffect<TextFont>()?.name

private inline fun <reified T : UiTextEffect> List<UiTextEffect>.lastEffect(): T? {
    for (index in indices.reversed()) {
        val effect = this[index]
        if (effect is T) return effect
    }
    return null
}

fun UiInlineStyle.withBold(): UiInlineStyle = copy(effects = effects + Bold)
fun UiInlineStyle.withItalic(): UiInlineStyle = copy(effects = effects + Italic)
fun UiInlineStyle.withUnderline(): UiInlineStyle = copy(effects = effects + Underline)
fun UiInlineStyle.withStrikethrough(): UiInlineStyle = copy(effects = effects + Strikethrough)
fun UiInlineStyle.withCode(): UiInlineStyle = copy(effects = effects + Code)
fun UiInlineStyle.withLink(url: String): UiInlineStyle = copy(effects = effects + Link(url))
fun UiInlineStyle.withColor(value: UiColor): UiInlineStyle = copy(effects = effects + TextColor(value))
fun UiInlineStyle.withBackground(value: UiColor): UiInlineStyle = copy(background = value)
fun UiInlineStyle.withFontSize(value: Float): UiInlineStyle = copy(effects = effects + TextSize(value))
fun UiInlineStyle.withFontFamily(name: String): UiInlineStyle = copy(effects = effects + TextFont(name))

enum class UiInlineAlign {
    BASELINE,
    MIDDLE,
    TOP,
    BOTTOM
}
