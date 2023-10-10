package ru.hollowhorizon.hollowengine.common.story

class StoryStateMachine {
    val nodes: ArrayList<EventNode> = ArrayList()
    var current: EventNode? = null

    fun start() {
        nodes.forEach { it.start() }
    }

    fun stop() {
        nodes.forEach { it.stop() }
    }
}

interface EventNode {
    fun start()
    fun stop()
}