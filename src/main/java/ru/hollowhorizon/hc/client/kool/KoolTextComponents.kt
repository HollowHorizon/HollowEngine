package ru.hollowhorizon.hc.client.kool

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentContents

import net.minecraft.network.chat.contents.TranslatableContents
import ru.hollowhorizon.hc.client.kool.KoolManager.MONOCRAFT

//? if >=1.21 {
import net.minecraft.network.chat.contents.PlainTextContents.LiteralContents
//?} else {
/*import net.minecraft.network.chat.contents.LiteralContents
*///?}

fun UiScope.Component(
    vararg components: Component,
    block: TextAreaScope.() -> Unit,
) {
    TextArea(
        ListTextLineProvider(components.map { TextLine(it.coloredText()) }.toMutableList()),
        hScrollbarModifier = { it.margin(start = sizes.gap, end = sizes.gap * 2f, bottom = sizes.gap) },
        vScrollbarModifier = { it.margin(sizes.gap) },
        scopeName = "Component",
        block = block
    )
}

fun Component.textLine() = TextLine(coloredText())

private fun Component.coloredText(parentColor: Int = 0xFFFFFF): MutableList<Pair<String, TextAttributes>> {
    val list = mutableListOf(attributes(parentColor))
    list += siblings.flatMap { it.coloredText(style.color?.value ?: parentColor) }.toMutableList()
    return list
}

private fun Component.attributes(parentColor: Int): Pair<String, TextAttributes> {

    val color = style.color?.value ?: parentColor
    val isUnderlined = style.isUnderlined
    val isStrikethrough = style.isStrikethrough
    val isObfuscated = style.isObfuscated

    val red = (color shr 16 and 0xFF).toFloat() / 255.0f
    val green = (color shr 8 and 0xFF).toFloat() / 255.0f
    val blue = (color and 0xFF).toFloat() / 255.0f

    var text = when (val content = contents) {
        is LiteralContents -> content.text
        is TranslatableContents -> {
            String.format(
                Language.getInstance().getOrDefault(content.key),
                *content.args.map { if (it is Component) it.string else it }.toTypedArray()
            )
        }

        //? if <=1.20.5 {
        /*ComponentContents.EMPTY -> ""
        *///?}
        else -> error("Unknown text component: $content")
    }

    if (isObfuscated) text = obfuscatedString(text.length)

    return text to TextAttributes(MsdfFont(MONOCRAFT, 30f), Color(red, green, blue))
}

private fun obfuscatedString(length: Int): String {
    val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + ('А'..'Я') + ('а'..'я')
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}