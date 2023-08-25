package ru.hollowhorizon.hollowengine.common.scripting.story

import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class StoryExecutorThread @JvmOverloads constructor(
    val team: StoryTeam,
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
            val story = ScriptingCompiler.compileFile<StoryScript>(
                file
            )

            val res = story.execute {
                constructorArgs(team, file.toReadablePath())
                jvm {
                    loadDependencies(false)
                }
            }

            val resScript = res.valueOrThrow().returnValue.scriptInstance as StoryEvent

            resScript.clearEvent()

            res.reports.forEach {
                it.render().lines().forEach { line ->
                    team.sendMessage("§c[ERROR]§r $line")
                }
                hasErrors = true
            }
        } catch (e: Exception) {
            team.sendMessage("§c Error while loading event \"${file.toReadablePath()}\".")
            team.sendMessage("${e.message}")
            team.sendMessage("§eCheck logs for more details.")
            e.printStackTrace()
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