package ru.hollowhorizon.hollowengine.common.dialogue

import kotlinx.coroutines.delay
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import ru.hollowhorizon.hollowengine.common.dialogue.text.FormattedTextDocument
import ru.hollowhorizon.hollowengine.common.dialogue.text.FormattedTextParser
import ru.hollowhorizon.hollowengine.common.dialogue.text.FormattedTextSpan
import ru.hollowhorizon.hollowengine.common.dialogue.text.FormattedTextStyle
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate
import kotlin.time.Duration.Companion.milliseconds

/**
 * The simplest presenter: the dialogue happens in chat. Lines are collected while they are built and
 * sent as one message, and choices are clickable.
 */
class ChatPresentation(
    private val advanceDelay: Long? = 1_500L,
    private val speakerColor: ChatFormatting = ChatFormatting.YELLOW,
) : DialoguePresenter {
    private val buffer = StringBuilder()
    private var speaker: DialogueCharacter? = null
    private var emittedCharacters = 0

    override suspend fun beginLine(session: DialogueSession, speaker: DialogueCharacter?) {
        buffer.clear()
        this.speaker = speaker
        emittedCharacters = 0
    }

    override suspend fun appendText(session: DialogueSession, text: String) {
        buffer.append(text)
    }

    override suspend fun waitForInput(session: DialogueSession) {
        flush(session)
        awaitAdvance(session)
    }

    override suspend fun endLine(session: DialogueSession) {
        flush(session)
        if (advanceDelay != null) delay(advanceDelay.milliseconds) else awaitAdvance(session)
    }

    override suspend fun showChoices(session: DialogueSession, options: List<PresentedChoice>) {
        for (option in options) {
            val line: MutableComponent = Component.literal("  ▶ ").withStyle { style: Style ->
                style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hollowengine dialogue choose ${option.index}"))
            }
            line.appendFormatted(FormattedTextParser.parse(option.text))
            session.onlineParticipants.forEach { it.sendSystemMessage(line) }
        }
    }

    override suspend fun hideChoices(session: DialogueSession, chosen: Int) = Unit

    private fun flush(session: DialogueSession) {
        if (buffer.isEmpty()) return
        val formatted = FormattedTextParser.parse(buffer.toString())
        if (formatted.visibleLength <= emittedCharacters) return
        val message = Component.empty().apply {
            speaker?.let { append(Component.literal("${it.name}: ").withStyle(speakerColor)) }
            appendFormatted(formatted, emittedCharacters)
        }
        emittedCharacters = formatted.visibleLength
        session.onlineParticipants.forEach { it.sendSystemMessage(message) }
    }

    /** Chat has no click-to-continue, so the prompt is the click target. */
    private fun sendAdvancePrompt(session: DialogueSession) {
        val prompt = Component.empty()
            .append("  ")
            .append("hollowengine.dialogue.advance".mcTranslate)
            .withStyle { style: Style ->
                style.withColor(ChatFormatting.GRAY)
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hollowengine dialogue advance"))
            }
        session.onlineParticipants.forEach { it.sendSystemMessage(prompt) }
    }

    private suspend fun awaitAdvance(session: DialogueSession) {
        val controller = session as? DialogueController
        if (controller == null) {
            delay(1_000.milliseconds)
            return
        }
        sendAdvancePrompt(session)
        controller.awaitAdvance()
    }
}

private fun MutableComponent.appendFormatted(document: FormattedTextDocument, fromCharacter: Int = 0) {
    var consumed = 0
    document.spans.forEach { span ->
        val length = span.text.codePointCount(0, span.text.length)
        val skip = (fromCharacter - consumed).coerceIn(0, length)
        if (skip < length) append(span.component(skip))
        consumed += length
    }
}

private fun FormattedTextSpan.component(skipCharacters: Int): MutableComponent {
    val start = text.offsetByCodePoints(0, skipCharacters)
    return Component.literal(text.substring(start)).withStyle(styles.toChatStyle())
}

/** Chat keeps static formatting. Motion-only effects gracefully reduce to ordinary text. */
private fun List<FormattedTextStyle>.toChatStyle(): Style {
    var result = Style.EMPTY
    for (style in this) {
        result = when (style) {
            FormattedTextStyle.Bold -> result.withBold(true)
            FormattedTextStyle.Italic -> result.withItalic(true)
            FormattedTextStyle.Underline -> result.withUnderlined(true)
            FormattedTextStyle.Strikethrough -> result.withStrikethrough(true)
            is FormattedTextStyle.Color -> result.withColor(style.rgb)
            is FormattedTextStyle.Gradient -> result.withColor(style.from.rgb)
            is FormattedTextStyle.Animation -> result
        }
    }
    return result
}

private val FormattedTextStyle.Color.rgb: Int get() = (red shl 16) or (green shl 8) or blue
