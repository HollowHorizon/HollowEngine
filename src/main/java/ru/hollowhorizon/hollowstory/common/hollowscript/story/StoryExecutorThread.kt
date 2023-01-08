package ru.hollowhorizon.hollowstory.common.hollowscript.story

import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.client.utils.toIS
import ru.hollowhorizon.hc.common.scripting.HSCompiler
import ru.hollowhorizon.hollowstory.story.StoryEvent
import ru.hollowhorizon.hollowstory.story.StoryScript
import ru.hollowhorizon.hollowstory.story.StoryTeam
import ru.hollowhorizon.hollowstory.story.StoryVariables
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class StoryExecutorThread(val team: StoryTeam, val variables: StoryVariables, private val storyName: String, private val code: String) : Thread() {
    override fun run() {
        team.sendMessage("[DEBUG] Compiling Story Event")

        try {
            if(FMLLoader.isProduction()) System.setProperty("kotlin.java.stdlib.jar", ModList.get().getModFileById("hc").file.filePath.toFile().absolutePath)

            val story = HSCompiler().compile<StoryScript>(
                storyName.replace(".", "/"),
                code
            )

            team.sendMessage("[DEBUG] Story Event compiled")

            val res = story.execute {
                constructorArgs(team, variables, storyName)
                jvm {
                    loadDependencies(false)
                }
            }

            val resScript = res.valueOrThrow().returnValue.scriptInstance as StoryEvent

            resScript.clearEvent()

            team.sendMessage("[DEBUG] Story Event executed")

            res.reports.forEach {
                team.sendMessage("[DEBUG] ${it.render()}")
            }
        } catch (e: Exception) {
            team.sendMessage("[DEBUG] Error while compiling dialogue: ${e.stackTraceToString()}")
        }
    }
}

fun executeStory(player: PlayerEntity, storyPath: ResourceLocation) {
    val stream = storyPath.toIS()

    val team = StoryStorage.getTeam(player)

    val storyName = storyPath.namespace+ "."+ storyPath.path.substringBefore(".se.kts").replace("/", ".")

    StoryExecutorThread(team, StoryVariables(), storyName, stream.bufferedReader().readText()).start()
}