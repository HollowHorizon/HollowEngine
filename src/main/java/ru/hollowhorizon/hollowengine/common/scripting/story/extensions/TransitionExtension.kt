package ru.hollowhorizon.hollowengine.common.scripting.story.extensions

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.screen.OverlayScreen
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent

fun StoryEvent.makeTransition(text: String? = null, time: Float, task: () -> Unit) {
    val overlay = OverlayScreen(text)
    Minecraft.getInstance().setScreen(overlay)
    overlay.makeBlack(time / 2F)
    wait(time / 2F)
    task()
    overlay.makeTransparent(time / 2F)
    wait(time / 2F)
    Minecraft.getInstance().setScreen(null)
}