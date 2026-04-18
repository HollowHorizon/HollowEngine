package ru.hollowhorizon.hollowengine.common.events

interface Event

interface Cancelable {
    var isCanceled: Boolean
}

interface ClientEvent : Event


fun <T: Event> T.post() = apply { EventBus.post(this) }