package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import dev.ftb.mods.ftbteams.FTBTeamsAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.register
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.utils.roundTo
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.CopyTextPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript
import java.util.function.Consumer

object HEStoryCommands {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register {
            "hollowengine" {
                "pos" {
                    val player = source.playerOrException
                    val loc = player.pick(100.0, 0.0f, true).location
                    CopyTextPacket().send("pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})", player)
                }

                "start-script"(
                    arg("players", EntityArgument.players()),
                    arg(
                        "script",
                        StringArgumentType.greedyString(),
                        DirectoryManager.getAllStoryEvents().map { it.toReadablePath() })
                ) {
                    val players = EntityArgument.getPlayers(this, "players")
                    val raw = StringArgumentType.getString(this, "script")
                    val script = raw.fromReadablePath()
                    players.forEach { player ->
                        val storyTeam = FTBTeamsAPI.getPlayerTeam(player)
                        runScript(player.server, storyTeam, script, true)
                    }
                    HollowCore.LOGGER.info("Started script $script")
                }

                "active-events" {
                    val player = source.playerOrException
                    val storyTeam = FTBTeamsAPI.getPlayerTeam(player)
                    player.sendMessage("hollowengine.commands.active_events".mcTranslate, player.uuid)
                    StoryHandler.getActiveEvents(storyTeam)
                        .ifEmpty { mutableListOf("No active events") }
                        .forEach(
                            Consumer { name: String ->
                                player.sendMessage("§6 - §7$name".mcText, player.uuid)
                            }
                        )
                }
            }
        }
    }
}