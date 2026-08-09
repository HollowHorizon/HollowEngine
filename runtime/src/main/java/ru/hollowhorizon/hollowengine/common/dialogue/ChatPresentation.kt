package ru.hollowhorizon.hollowengine.common.dialogue

import kotlinx.coroutines.delay
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
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

    override suspend fun beginLine(session: DialogueSession, speaker: DialogueCharacter?) {
        buffer.clear()
        this.speaker = speaker
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
            val line: MutableComponent = Component.literal("  ▶ ${option.text}").withStyle { style: Style ->
                style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hollowengine dialogue choose ${option.index}"))
            }
            session.onlineParticipants.forEach { it.sendSystemMessage(line) }
        }
    }

    override suspend fun hideChoices(session: DialogueSession) = Unit

    private fun flush(session: DialogueSession) {
        if (buffer.isEmpty()) return
        val text = buffer.toString()
        buffer.clear()
        val message = Component.empty().apply {
            speaker?.let { append(Component.literal("${it.name}: ").withStyle(speakerColor)) }
            append(Component.literal(text))
        }
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
