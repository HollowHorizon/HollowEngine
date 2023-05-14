package ru.hollowhorizon.hollowengine.story.extensions

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.hollowscript.dialogues.DialogueExecutorThread
import ru.hollowhorizon.hollowengine.dialogues.DialogueScriptBaseV2
import ru.hollowhorizon.hollowengine.dialogues.executors.ServerDialogueExecutor
import ru.hollowhorizon.hollowengine.story.StoryEvent

fun StoryEvent.openDialogue(path: String) {
    val threads = ArrayList<DialogueExecutorThread>()
    team.forAllOnline {
        threads += DialogueExecutorThread(it.mcPlayer!!, path.fromReadablePath())
    }
    threads.forEach(Thread::start)
}

fun StoryEvent.openDialogue(script: DialogueScriptBaseV2.() -> Unit) {
    team.getAllOnline().mapNotNull { it.mcPlayer }.forEach { player ->
        Thread {
            val executor = ServerDialogueExecutor(player)

            script(DialogueScriptBaseV2(executor))

            executor.stop()
        }.start()
    }
}

fun StoryEvent.waitDialogue(path: String) {
    val threads = ArrayList<DialogueExecutorThread>()
    team.forAllOnline {
        threads += DialogueExecutorThread(it.mcPlayer!!, path.fromReadablePath())
    }
    threads.forEach(Thread::start)

    while (threads.any { it.isAlive }) {
    }
}

fun StoryEvent.waitDialogue(script: DialogueScriptBaseV2.() -> Unit) {
    val threads = ArrayList<Thread>()
    team.getAllOnline().mapNotNull { it.mcPlayer }.forEach { player ->
        threads += Thread {
            val executor = ServerDialogueExecutor(player)

            script(DialogueScriptBaseV2(executor))

            executor.stop()
        }
    }
    threads.forEach(Thread::start)

    while (threads.any { it.isAlive }) {}
}