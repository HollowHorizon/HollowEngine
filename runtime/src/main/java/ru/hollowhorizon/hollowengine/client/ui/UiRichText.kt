package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.effects.*

data class UiRichText(
    val items: List<UiInlineItem>,
) {
    companion object {
        fun plain(text: String): UiRichText = UiRichText(listOf(UiInlineItem.Text(text)))
    }
}

sealed interface UiInlineItem {
    data class Text(
        val value: String,
        val style: UiInlineStyle = UiInlineStyle(),
    ) : UiInlineItem

    data class Image(
        val source: String,
        val width: Float,
        val height: Float,
        val align: UiInlineAlign = UiInlineAlign.BASELINE,
        val alt: String = "",
    ) : UiInlineItem
}

data class UiInlineStyle(
    val effects: List<UiTextEffect> = emptyList(),
) {
    fun merge(other: UiInlineStyle): UiInlineStyle = UiInlineStyle(
        effects = effects + other.effects,
    )

    fun resolvedFontSize(baseFontSize: Float): Float = fontSize ?: baseFontSize.coerceAtLeast(0.0001f)
}

val UiInlineStyle.bold: Boolean get() = effects.any { it is Bold }
val UiInlineStyle.italic: Boolean get() = effects.any { it is Italic }
val UiInlineStyle.underline: Boolean get() = effects.any { it is Underline }
val UiInlineStyle.strikethrough: Boolean get() = effects.any { it is Strikethrough }
val UiInlineStyle.code: Boolean get() = effects.any { it is Code }
val UiInlineStyle.link: String? get() = effects.filterIsInstance<Link>().lastOrNull()?.url
val UiInlineStyle.color: UiColor? get() = effects.filterIsInstance<TextColor>().lastOrNull()?.value
val UiInlineStyle.fontSize: Float? get() = effects.filterIsInstance<TextSize>().lastOrNull()?.value
val UiInlineStyle.fontFamily: String? get() = effects.filterIsInstance<TextFont>().lastOrNull()?.name

fun UiInlineStyle.withBold(): UiInlineStyle = copy(effects = effects + Bold)
fun UiInlineStyle.withItalic(): UiInlineStyle = copy(effects = effects + Italic)
fun UiInlineStyle.withUnderline(): UiInlineStyle = copy(effects = effects + Underline)
fun UiInlineStyle.withStrikethrough(): UiInlineStyle = copy(effects = effects + Strikethrough)
fun UiInlineStyle.withCode(): UiInlineStyle = copy(effects = effects + Code)
fun UiInlineStyle.withLink(url: String): UiInlineStyle = copy(effects = effects + Link(url))
fun UiInlineStyle.withColor(value: UiColor): UiInlineStyle = copy(effects = effects + TextColor(value))
fun UiInlineStyle.withFontSize(value: Float): UiInlineStyle = copy(effects = effects + TextSize(value))
fun UiInlineStyle.withFontFamily(name: String): UiInlineStyle = copy(effects = effects + TextFont(name))

enum class UiInlineAlign {
    BASELINE,
    MIDDLE,
    TOP,
    BOTTOM
}
