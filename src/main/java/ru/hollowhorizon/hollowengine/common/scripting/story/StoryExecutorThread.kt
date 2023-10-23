package ru.hollowhorizon.hollowengine.common.scripting.story

import dev.ftb.mods.ftbteams.api.Team
import kotlinx.coroutines.async
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.story.coroutines.ScriptContext
import ru.hollowhorizon.hollowengine.common.sendMessage
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

fun runScript(server: MinecraftServer, team: Team, file: File) = ScriptContext.scope.async(ScriptContext.scriptContext) {
    try {
        val story = ScriptingCompiler.compileFile<StoryScript>(file)

        val res = story.execute {
            constructorArgs(server, team)
            jvm {
                loadDependencies(false)
            }
        }

        res.reports.forEach {
            it.render().lines().forEach { line ->
                team.onlineMembers.forEach { it.sendMessage("§c[ERROR]§r $line".mcText) }
            }
        }

        val resScript = res.valueOrThrow().returnValue.scriptInstance as StoryStateMachine
        StoryHandler.addStoryEvent(file.toReadablePath(), resScript)
    } catch (e: Exception) {
        team.onlineMembers.forEach {
            it.sendMessage("§cError while executing event \"${file.toReadablePath()}\".".mcText)
            it.sendMessage("${e.message}".mcText)
            it.sendMessage("§eCheck logs for more details.".mcText)
        }
        HollowCore.LOGGER.error("Error while executing event \"${file.toReadablePath()}\"", e)
    }
}