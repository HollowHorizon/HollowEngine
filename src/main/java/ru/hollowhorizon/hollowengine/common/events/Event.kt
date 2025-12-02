package ru.hollowhorizon.hollowengine.common.events

interface Event

interface Cancelable {
    var isCanceled: Boolean
}

interface ClientEvent: Event


fun Event.post() {
    EventBus.post(this)
}