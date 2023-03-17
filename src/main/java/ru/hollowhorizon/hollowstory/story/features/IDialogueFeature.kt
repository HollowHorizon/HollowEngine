package ru.hollowhorizon.hollowstory.story.features

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowstory.client.screen.DialogueScreen
import ru.hollowhorizon.hollowstory.common.files.HollowStoryDirHelper.fromReadablePath

interface IDialogueFeature {
    fun openDialogue(path: String, onEnd: () -> Unit = {}) {
        Minecraft.getInstance().setScreen(DialogueScreen(path.fromReadablePath(), onEnd))
    }

    fun waitDialogue(path: String) {
        val waiter = Object()
        openDialogue(path) {
            synchronized(waiter) {
                waiter.notifyAll()
            }
        }
        synchronized(waiter) {
            waiter.wait()
        }
    }
}