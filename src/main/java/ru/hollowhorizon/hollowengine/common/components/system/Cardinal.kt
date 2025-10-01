package ru.hollowhorizon.hollowengine.common.components.system

import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.events.ComponentDispatcherEvent
import ru.hollowhorizon.hollowengine.common.events.EventBus
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.eventListenerOf
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.reflect.full.findAnnotation

object Cardinal {

    inline fun <reified T : ComponentDispatcherEvent<*>, reified C : Component<*>> on(priority: Int = 0, noinline handler: T.(C) -> Unit): EventListener<T> {
        val location = C::class.findAnnotation<ComponentMeta>()?.location?.rl
            ?: error("ComponentMeta annotation not found on ${C::class}")
        val eventListener: EventListener<T> = eventListenerOf(priority) { event ->
            val dispatcher = event.owner
            val component = dispatcher.`hollowcore$components`[location]
            (component as? C)?.let { handler(event, it) }
        }
        EventBus.register(eventListener)
        return eventListener
    }
}