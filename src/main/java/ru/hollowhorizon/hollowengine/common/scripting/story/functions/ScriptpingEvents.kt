package ru.hollowhorizon.hollowengine.common.scripting.story.functions

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent

@Serializable
class ScriptingEventListener(val eventType: String) {
    init {
        var listener: EventListener<Event>? = null
        listener = EventListener {
            result = it
            EventBus.unregister(listener ?: error("listener is null"))
        }
        EventBus.registerNoInline(Class.forName(eventType) as Class<Event>, listener)
    }

    @Transient
    var result: Event? = null
}

suspend inline fun <reified T : Event> await(): T = await<T>()

suspend fun Player.input(): String {
    while (true) {
        val event = await<ServerChatEvent>()
        if (event.player == this) return event.message.string
    }
}
