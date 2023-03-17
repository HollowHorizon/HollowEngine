package ru.hollowhorizon.hollowstory.story.features

import ru.hollowhorizon.hollowstory.client.screen.OverlayScreen

interface ITransitionFeature {
    fun makeTransition(text: String? = null, time: Float, task: () -> Unit) {
        val overlay = OverlayScreen(text)
        overlay.makeBlack(time / 2F)
        task()
        overlay.makeTransparent(time / 2F)
    }
}