package ru.hollowhorizon.hollowengine.common.scripting

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.commands.startKoolScript
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.events.EVENT_SCRIPTS
import ru.hollowhorizon.hollowengine.common.scripting.events.startEventScript
import ru.hollowhorizon.hollowengine.common.scripting.kool.KoolClientManager
import ru.hollowhorizon.hollowengine.common.scripting.scene.SceneScriptManager
import java.io.File

fun startScript(file: File) {
    val extension = file.name.substringAfter('.').substringBeforeLast('.')

    when (extension) {
        "scene" -> SceneScriptManager.startScene(file.toReadablePath(), "main")
        "kool" -> startKoolScript(file)
        "event" -> {
            runBlocking { startEventScript(file).await() }
        }
    }
}

fun stopScript(file: File) {
    val extension = file.name.substringAfter('.').substringBeforeLast('.')

    when (extension) {
        "scene" -> SceneScriptManager.stopScene(file.toReadablePath())
        "event" -> EVENT_SCRIPTS.remove(file)
        "kool" -> KoolClientManager.removeScene(file.toReadablePath())
    }
}
