package ru.hollowhorizon.hollowengine.common.events

interface Event

interface Cancelable {
    var isCanceled: Boolean
}

fun Event.post() {
    EventBus.post(this)
}