package ru.hollowhorizon.hollowengine.client.gui.scripting.panels.console

import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.MsdfFont
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.apache.logging.log4j.spi.StandardLevel
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.common.utils.mutableLazy

class LogMessage(
    val level: StandardLevel,
    val tag: String?,
    message: String,
    time: Instant,
) {
    val fmtTime: String = formatTime(time)

    var isTextValid = false

    val fullMessage = message
    val message: String = if (message.length < maxMessageLen) message else message.substring(0, maxMessageLen) + "…"
    var text: TextLine by mutableLazy {
        makeTextLine()
    }

    val isAccepted: Boolean
        get() {
            if (level.ordinal > minLevel.value.ordinal) return false
            val filter = messageFilter ?: return true
            return filter.containsMatchIn(message) || (tag != null && filter.containsMatchIn(tag))
        }


    fun updateText() {
        text = makeTextLine()
    }

    private fun makeTextLine(): TextLine {
        isTextValid = level in levelFonts
        val spans = mutableListOf<Pair<String, TextAttributes>>()
        spans += "[$fmtTime] " to timeFont
        spans += " ${level.baseName} " to (levelFonts[level] ?: defaultTextAttrs)
        tag?.let {
            spans += " [${tag.substringAfterLast(".")}] " to longTagFont
        }
        spans += message to (messageFonts[level] ?: defaultTextAttrs)

        return TextLine(spans)
    }

    val StandardLevel.baseName: String
        get() = buildString {
            append(name)
            while (length < 5) append(" ")
        }

    private fun formatTime(time: Instant): String {
        val date = time.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${fmtInt(date.hour)}:${fmtInt(date.minute)}:${fmtInt(date.second)}"
    }

    private fun fmtInt(i: Int, len: Int = 2): String {
        var fmt = "$i"
        while (fmt.length < len) {
            fmt = "0$fmt"
        }
        return fmt
    }

    override fun toString(): String {
        return "$fmtTime ${level.name}: ${fullMessage}${if (tag != null) " [${tag}]" else ""}"
    }

    companion object {
        var minLevel = mutableStateOf(StandardLevel.DEBUG)
        var messageFilter: Regex? = null


        const val maxMessageLen = 500

        val defaultTextAttrs by mutableLazy { TextAttributes(MsdfFont(KoolManager.MONOCRAFT), Color.MAGENTA) }
        var timeFont: TextAttributes by mutableLazy { defaultTextAttrs }
        var longTagFont: TextAttributes by mutableLazy { defaultTextAttrs }
        val levelFonts = mutableMapOf<StandardLevel, TextAttributes>()
        val messageFonts = mutableMapOf<StandardLevel, TextAttributes>()

        fun updateFonts(baseFont: MsdfFont = MsdfFont(HACK_FONT), baseSize: Float = 16f) {
            val font = baseFont.copy(baseSize)

            timeFont = TextAttributes(font, MdColor.CYAN tone 400)
            longTagFont = TextAttributes(font, MdColor.GREY tone 600)

            levelFonts[StandardLevel.TRACE] =
                TextAttributes(font.copy(weight = MsdfFont.WEIGHT_BOLD), MdColor.GREY tone 600, MdColor.GREY tone 850)
            levelFonts[StandardLevel.DEBUG] =
                TextAttributes(font.copy(weight = MsdfFont.WEIGHT_BOLD), MdColor.GREY tone 400, MdColor.GREY tone 800)
            levelFonts[StandardLevel.INFO] =
                TextAttributes(font.copy(weight = MsdfFont.WEIGHT_BOLD), Color.WHITE, MdColor.LIGHT_GREEN)
            levelFonts[StandardLevel.WARN] =
                TextAttributes(font.copy(weight = MsdfFont.WEIGHT_BOLD), Color.WHITE, MdColor.AMBER)
            levelFonts[StandardLevel.ERROR] =
                TextAttributes(font.copy(weight = MsdfFont.WEIGHT_BOLD), Color.WHITE, MdColor.RED)

            messageFonts[StandardLevel.TRACE] = TextAttributes(font, MdColor.GREY tone 600)
            messageFonts[StandardLevel.DEBUG] = TextAttributes(font, MdColor.GREY tone 400)
            messageFonts[StandardLevel.INFO] = TextAttributes(font, MdColor.GREY tone 200)
            messageFonts[StandardLevel.WARN] = TextAttributes(font, MdColor.AMBER tone 200)
            messageFonts[StandardLevel.ERROR] = TextAttributes(font.copy(weight = MsdfFont.WEIGHT_BOLD), MdColor.RED)
        }
    }
}