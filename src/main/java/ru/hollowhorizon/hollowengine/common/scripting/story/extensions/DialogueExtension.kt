package ru.hollowhorizon.hollowengine.common.scripting.story.extensions

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueExecutorThread
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueScriptBaseV2
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.executors.ServerDialogueExecutor
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryEvent

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

    while (threads.any { it.isAlive }) {
    }
}