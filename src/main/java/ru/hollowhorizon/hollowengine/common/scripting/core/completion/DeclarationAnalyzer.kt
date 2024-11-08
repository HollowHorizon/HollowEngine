package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.completionsList
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.currentColumn
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.currentLine
import ru.hollowhorizon.hollowengine.common.scripting.core.AfterCodeAnalysisEvent

@SubscribeEvent
fun onAnalysisEvent(event: AfterCodeAnalysisEvent) {
    val provider = CompletionProvider(
        event.sources.toMutableList(), event.sources.firstOrNull()?.name ?: "", currentLine, currentColumn
    )

    val r = provider.getResult(event)

    completionsList.clear()
    completionsList.addAll(r)
}
