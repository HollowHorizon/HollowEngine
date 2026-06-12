package ru.hollowhorizon.hollowengine.common.scripting.katari

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.utils.currentServer

@ScriptBinding
suspend fun waitChat(): KatariChatMessage {
    return withContext(currentServer.dispatcher) {
        val event = ServerChatEvent.await()
        delay(50)
        KatariChatMessage(event.player, event.message.string)
    }
}

