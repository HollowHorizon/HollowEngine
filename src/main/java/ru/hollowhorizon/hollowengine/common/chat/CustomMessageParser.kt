package ru.hollowhorizon.hollowengine.common.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.minecraft.client.GuiMessage
import net.minecraft.network.chat.Style
import net.minecraft.util.FormattedCharSequence
import org.jetbrains.kotlin.daemon.common.trimQuotes

fun main() {
    val buttons = CustomMessageParser.parse(
        FormattedCharSequence.forward(
            "\${[{\"text\":\"Привет всем))\",\"texture\":\"hollowengine:textures/gui/dialogues/choice_button.png\"}]}",
            Style.EMPTY
        )
    )

    println(buttons.contentToString())
}

object CustomMessageParser {
    fun parse(mcText: FormattedCharSequence): Array<Button> {
        var text = mcText.asString().trim()
        text = text
            .substring(2, text.length - 1)

        return Json.decodeFromString(text)
    }
}

@Serializable
class Button(val text: String, val texture: String) {
    companion object {
        fun fromString(text: String): Button {
            if (!text.startsWith("button")) throw IllegalStateException("Not a button!")
            val args = text.substring(6).split(",")
            return Button(args[0].trimQuotes(), args[1].trimQuotes())
        }
    }

    override fun toString(): String {
        return "{\"text\":\"$text\",\"texture\":\"$texture\"}"
    }
}

fun FormattedCharSequence.asString() =
    StringBuilder().apply { accept { _, _, char -> appendCodePoint(char); true } }.toString()

