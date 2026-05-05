package ru.hollowhorizon.hollowengine.common.scripting.katari

import ru.hollowhorizon.hollowengine.common.events.factory.await


internal suspend fun awaitInput(
    playerId: String,
    predicate: (KatariInputSnapshot) -> Boolean,
): KatariInputSnapshot {
    return KatariInputEvent.await { event ->
        event.player.uuid.toString() == playerId && predicate(event.input)
    }.input
}
