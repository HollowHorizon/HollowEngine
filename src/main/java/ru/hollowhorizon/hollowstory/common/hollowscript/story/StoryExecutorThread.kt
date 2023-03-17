package ru.hollowhorizon.hollowstory.common.hollowscript.story

import net.minecraft.entity.player.PlayerEntity
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.common.scripting.HSCompiler
import ru.hollowhorizon.hollowstory.common.capabilities.storyTeam
import ru.hollowhorizon.hollowstory.common.events.StoryHandler
import ru.hollowhorizon.hollowstory.common.files.HollowStoryDirHelper
import ru.hollowhorizon.hollowstory.common.files.HollowStoryDirHelper.toReadablePath
import ru.hollowhorizon.hollowstory.story.StoryEvent
import ru.hollowhorizon.hollowstory.story.StoryScript
import ru.hollowhorizon.hollowstory.story.StoryTeam
import ru.hollowhorizon.hollowstory.story.StoryVariables
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class StoryExecutorThread @JvmOverloads constructor(
    val team: StoryTeam,
    val variables: StoryVariables,
    val file: File,
    val ignoreSequence: Boolean = false,
) : Thread() {
    override fun run() {
        if (!ignoreSequence) {
            val text = file.readText()

            //Вообще говоря правильнее было бы анализировать аннотации при refineConfiguration, но я понятия не имею как оттуда отменить выполнение скрипта
            val startAfter = if (text.contains("@file:StartAfter(")) {
                val startAfterIndex = text.indexOf("@file:StartAfter(")
                val startAfterEndIndex = text.indexOf(")", startAfterIndex)
                text.substring(startAfterIndex + 18, startAfterEndIndex - 1)
            } else {
                ""
            }

            if (startAfter != "" && !team.completedEvents.contains(startAfter)) return
            if (team.currentEvents.containsKey(file.toReadablePath()) || team.completedEvents.contains(file.toReadablePath())) return

            team.currentEvents[file.toReadablePath()] = this

        }
        var hasErrors = false

        try {
            if (FMLLoader.isProduction()) System.setProperty(
                "kotlin.java.stdlib.jar",
                ModList.get().getModFileById("hc").file.filePath.toFile().absolutePath
            )

            val story = HSCompiler.COMPILER.compile<StoryScript>(
                HollowStoryDirHelper.CACHE_DIR,
                file
            )

            val res = story.execute {
                constructorArgs(team, variables, file.name)
                jvm {
                    loadDependencies(false)
                }
            }

            val resScript = res.valueOrThrow().returnValue.scriptInstance as StoryEvent

            resScript.clearEvent()

            res.reports.forEach {
                team.sendMessage("[ERROR] ${it.render()}")
                hasErrors = true
            }
        } catch (e: Exception) {
            team.sendMessage("[DEBUG] Error while compiling event: ${e.stackTraceToString()}")
            hasErrors = true
        }

        if (!ignoreSequence) {
            team.currentEvents.remove(file.toReadablePath())

            if (!hasErrors) {

                team.completedEvents.add(file.toReadablePath())

                StoryHandler.runAllPossible(team)
            }
        }
    }
}

fun executeStory(player: PlayerEntity, storyPath: File) {
    val team = player.storyTeam()

    StoryExecutorThread(team, StoryVariables(), storyPath).start()
}