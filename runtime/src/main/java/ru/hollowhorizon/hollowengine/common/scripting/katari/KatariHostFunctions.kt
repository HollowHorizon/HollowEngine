package ru.hollowhorizon.hollowengine.common.scripting.katari

import kotlinx.coroutines.delay
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding

@ScriptBinding
suspend fun waitChat(): KatariChatMessage {
    val event = ServerChatEvent.await()
    delay(50)
    return KatariChatMessage(event.player, event.message.string)
}

