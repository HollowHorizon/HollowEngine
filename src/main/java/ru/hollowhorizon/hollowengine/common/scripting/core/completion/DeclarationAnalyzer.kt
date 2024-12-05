package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ActionManager
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.currentColumn
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.currentLine
import ru.hollowhorizon.hollowengine.common.scripting.core.AfterCodeAnalysisEvent

private val COMPLETION_CHARS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '.'

@SubscribeEvent
fun onAnalysisEvent(event: AfterCodeAnalysisEvent) {

    val line = event.sources.first().text.lines().getOrNull(currentLine) ?: return
    val char = line.substring(0, currentColumn.coerceIn(0, line.length)).lastOrNull() ?: ""


    try {
        val provider = CompletionProvider(
            event.sources.toMutableList(),
            event.sources.firstOrNull()?.name ?: "",
            currentLine, currentColumn
        )

        val r = ActionManager.future {
            provider.getResult(event)
        }.get()

        if (char !in COMPLETION_CHARS) return

        OnCompletionsEvent(event.sources.first().name, r).post()
    } catch (_: Exception) {
        // Если этого не делать, то память забьётся почти моментально :)
    }
}
