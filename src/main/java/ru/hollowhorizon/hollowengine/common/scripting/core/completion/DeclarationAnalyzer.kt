package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.scripting.core.AfterCodeAnalysisEvent

@SubscribeEvent
fun onAnalysisEvent(event: AfterCodeAnalysisEvent) {
    val line = 368 - 1

    val provider = CompletionProvider(
        event.sources.toMutableList(), event.script.name ?: "", 9, 22
    )

    val r = provider.getResult(event)

    println(r)
}
