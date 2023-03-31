package ru.hollowhorizon.hollowengine.story.features

import ru.hollowhorizon.hollowengine.client.screen.OverlayScreen
import ru.hollowhorizon.hollowengine.story.StoryEvent

fun StoryEvent.makeTransition(text: String? = null, time: Float, task: () -> Unit) {
    val overlay = OverlayScreen(text)
    overlay.makeBlack(time / 2F)
    task()
    overlay.makeTransparent(time / 2F)
}