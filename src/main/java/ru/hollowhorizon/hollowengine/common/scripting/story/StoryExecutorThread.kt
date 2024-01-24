package ru.hollowhorizon.hollowengine.common.scripting.story

import dev.ftb.mods.ftbteams.data.Team
import kotlinx.coroutines.async
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.kotlinscript.common.scripting.ScriptingCompiler
import ru.hollowhorizon.kotlinscript.common.scripting.errors
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.StoryLogger
import ru.hollowhorizon.hollowengine.common.scripting.story.coroutines.ScriptContext
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.util.isError

fun runScript(server: MinecraftServer, team: Team, file: File, isCommand: Boolean = false) =
    ScriptContext.scope.async(ScriptContext.scriptContext) {
        try {
            StoryLogger.LOGGER.info("Starting event \"{}\", for team \"{}\".", file.toReadablePath(), team.name.string)
            val shouldRecompile = ScriptingCompiler.shouldRecompile(file) || isCommand
            val story = ScriptingCompiler.compileFile<StoryScript>(file)

            story.errors?.let { errors ->
                errors.forEach { error ->
                    team.onlineMembers.forEach {
                        StoryLogger.LOGGER.error(error.replace("\\r\\n", "\n"))
                        it.sendSystemMessage("§c[ERROR]§r $error".mcText)
                    }
                }
                return@async
            }

            val res = story.execute {
                constructorArgs(server, team)
                jvm {
                    loadDependencies(false)
                }
            }

            if (res.isError()) {
                (res as ResultWithDiagnostics.Failure).errors().forEach { error ->
                    team.onlineMembers.forEach { it.sendSystemMessage("§c[ERROR]§r $error".mcText) }
                }
            } else {
                val resScript = res.valueOrThrow().returnValue.scriptInstance as StoryStateMachine
                StoryHandler.addStoryEvent(file.toReadablePath(), resScript, shouldRecompile)
            }
        } catch (e: Exception) {
            team.onlineMembers.forEach {
                it.sendSystemMessage(Component.translatable("hollowengine.executing_error", file.toReadablePath()))
                it.sendSystemMessage("${e.message}".mcText)
                it.sendSystemMessage("hollowengine.check_logs".mcTranslate)
            }
            HollowCore.LOGGER.error("(HollowEngine) Error while executing event \"${file.toReadablePath()}\"", e)
        }
    }