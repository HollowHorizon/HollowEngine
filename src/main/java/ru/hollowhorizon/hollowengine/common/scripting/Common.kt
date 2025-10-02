package ru.hollowhorizon.hollowengine.common.scripting

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.scripting.events.EVENT_SCRIPTS
import ru.hollowhorizon.hollowengine.common.scripting.events.startEventScript
import java.io.File

fun startScript(file: File) {
    val extension = file.name.substringAfter('.').substringBeforeLast('.')

    when (extension) {

        "event" -> {
            runBlocking { startEventScript(file).await() }
        }
    }
}

fun stopScript(file: File) {
    val extension = file.name.substringAfter('.').substringBeforeLast('.')

    when (extension) {
        "event" -> EVENT_SCRIPTS.remove(file)
    }
}
