package ru.hollowhorizon.hollowengine.common.events

interface Event

interface Cancellable {
    var isCanceled: Boolean
}

interface ClientEvent : Event
