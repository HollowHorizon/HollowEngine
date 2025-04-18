package ru.hollowhorizon.hollowengine.common.scripting.story.functions

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.events.EventListener
import ru.hollowhorizon.hc.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.scripting.Suspendable

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