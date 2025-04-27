package ru.hollowhorizon.hollowengine.common.scripting

import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompilerPluginEvent
import ru.hollowhorizon.hollowengine.common.scripting.events.EVENT_SCRIPTS
import ru.hollowhorizon.hollowengine.common.scripting.events.startEventScript
import ru.hollowhorizon.hollowengine.common.scripting.kool.KoolClientManager
import ru.hollowhorizon.hollowengine.common.scripting.story.STORY_EVENTS_SCRIPTS
import ru.hollowhorizon.hollowengine.common.scripting.story.startKoolScript
import ru.hollowhorizon.hollowengine.common.scripting.story.startStoryEvent
import java.io.File

fun startScript(file: File) {
    val extension = file.name.substringAfter('.').substringBeforeLast('.')

    when (extension) {
        "story" -> startStoryEvent(file)
        "kool" -> startKoolScript(file)
        "event" -> {
            runBlocking { startEventScript(file).await() }
        }
    }
}

fun stopScript(file: File) {
    val extension = file.name.substringAfter('.').substringBeforeLast('.')

    when (extension) {
        "story" -> STORY_EVENTS_SCRIPTS.removeIf { it.file == file.toReadablePath() }
        "event" -> EVENT_SCRIPTS.remove(file)
        "kool" -> KoolClientManager.removeScene(file.toReadablePath())
    }
}

@OptIn(ExperimentalCompilerApi::class)
@SubscribeEvent
fun compilerPlugins(event: ScriptingCompilerPluginEvent) {
    //event.addExtension(HollowEngineCompilerRegistrar())
}