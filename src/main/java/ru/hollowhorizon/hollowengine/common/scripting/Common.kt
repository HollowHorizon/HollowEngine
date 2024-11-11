package ru.hollowhorizon.hollowengine.common.scripting

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.events.EVENT_SCRIPTS
import ru.hollowhorizon.hollowengine.common.scripting.events.startEventScript
import ru.hollowhorizon.hollowengine.common.scripting.story.STORY_EVENTS_SCRIPTS
import ru.hollowhorizon.hollowengine.common.scripting.story.startGuiScript
import ru.hollowhorizon.hollowengine.common.scripting.story.startStoryEvent
import java.io.File

fun startScript(file: File) {
    val extension = file.name.substringAfter('.').substringBeforeLast('.')

    when (extension) {
        "story" -> startStoryEvent(file)
        "gui" -> startGuiScript(file)
        "event" -> {
            runBlocking { startEventScript(file).await() }
        }
    }
}

fun stopScript(file: File) {
    val extension = file.name.substringAfter('.').substringAfterLast('.')

    when (extension) {
        "story" -> STORY_EVENTS_SCRIPTS.removeIf { it.file == file.toReadablePath() }
        "event" -> EVENT_SCRIPTS.remove(file)
    }
}