package ru.hollowhorizon.hollowstory.common.hollowscript.story

import net.minecraft.entity.player.PlayerEntity
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.common.scripting.HSCompiler
import ru.hollowhorizon.hollowstory.common.capabilities.storyTeam
import ru.hollowhorizon.hollowstory.common.files.HollowStoryDirHelper
import ru.hollowhorizon.hollowstory.story.StoryEvent
import ru.hollowhorizon.hollowstory.story.StoryScript
import ru.hollowhorizon.hollowstory.story.StoryTeam
import ru.hollowhorizon.hollowstory.story.StoryVariables
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class StoryExecutorThread(val team: StoryTeam, val variables: StoryVariables, val file: File) : Thread() {
    override fun run() {

        try {
            if(FMLLoader.isProduction()) System.setProperty("kotlin.java.stdlib.jar", ModList.get().getModFileById("hc").file.filePath.toFile().absolutePath)

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
            }
        } catch (e: Exception) {
            team.sendMessage("[DEBUG] Error while compiling dialogue: ${e.stackTraceToString()}")
        }
    }
}

fun executeStory(player: PlayerEntity, storyPath: File) {
    val team = player.storyTeam()

    StoryExecutorThread(team, StoryVariables(), storyPath).start()
}