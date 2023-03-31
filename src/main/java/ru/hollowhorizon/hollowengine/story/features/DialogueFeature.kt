package ru.hollowhorizon.hollowengine.story.features

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.story.StoryEvent

fun StoryEvent.openDialogue(path: String, onEnd: () -> Unit = {}) {
    Minecraft.getInstance().setScreen(DialogueScreen(path.fromReadablePath(), onEnd))
}

fun StoryEvent.waitDialogue(path: String) {
    val waiter = Object()
    this.openDialogue(path) {
        synchronized(waiter) {
            waiter.notifyAll()
        }
    }
    synchronized(waiter) {
        waiter.wait()
    }
}