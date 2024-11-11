package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.completionsList
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.currentColumn
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.currentLine
import ru.hollowhorizon.hollowengine.common.scripting.core.AfterCodeAnalysisEvent

private val COMPLETION_CHARS = ('a'..'z') + ('A'..'Z') + ('0'..'9') + '.'

@SubscribeEvent
fun onAnalysisEvent(event: AfterCodeAnalysisEvent) {
    val line = event.sources.first().text.lines().getOrNull(currentLine) ?: return
    val char = line.substring(0, currentColumn.coerceAtMost(line.length)).lastOrNull() ?: return

    if(char !in COMPLETION_CHARS) return

    val provider = CompletionProvider(
        event.sources.toMutableList(),
        event.sources.firstOrNull()?.name ?: "",
        currentLine, currentColumn
    )

    val r = provider.getResult(event)

    completionsList.clear()
    completionsList.addAll(r)
}
