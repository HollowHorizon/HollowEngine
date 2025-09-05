package ru.hollowhorizon.hollowengine.common.events

import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher

interface Event

interface Cancelable {
    var isCanceled: Boolean
}

interface ClientEvent: Event

interface ComponentDispatcherEvent<T>: Event {
    val owner: ComponentDispatcher
}

fun Event.post() {
    EventBus.post(this)
}