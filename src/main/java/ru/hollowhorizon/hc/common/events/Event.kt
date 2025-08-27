package ru.hollowhorizon.hc.common.events

interface Event

interface Cancelable {
    var isCanceled: Boolean
}

fun Event.post() {
    EventBus.post(this)
}