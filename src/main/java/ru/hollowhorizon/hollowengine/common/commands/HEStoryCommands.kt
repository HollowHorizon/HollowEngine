package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import dev.ftb.mods.ftbteams.FTBTeamsAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.register
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
                    CopyTextPacket("pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})").send(PacketDistributor.PLAYER.with {player})
                }

                "start-script"(
                    arg("players", EntityArgument.players()),
                    arg("script", StringArgumentType.greedyString(), DirectoryManager.getStoryEvents().map { it.toReadablePath() })
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

                "stop-script"(
                    arg("players", EntityArgument.players()),
                    arg("script", StringArgumentType.greedyString(), DirectoryManager.getStoryEvents().map { it.toReadablePath() })
                ) {
                    val players = EntityArgument.getPlayers(this, "players")
                    val eventPath = StringArgumentType.getString(this, "script")
                    players.forEach {
                        val storyTeam = FTBTeamsAPI.getPlayerTeam(it)
                        StoryHandler.stopEvent(storyTeam, eventPath)
                    }
                }

                "active-events" {
                    val player = source.playerOrException
                    val storyTeam = FTBTeamsAPI.getPlayerTeam(player)
                    player.sendSystemMessage(Component.translatable("hollowengine.commands.active_events"))
                    StoryHandler.getActiveEvents(storyTeam)
                        .ifEmpty{ mutableListOf("No active events") }
                        .forEach(
                            Consumer { name: String ->
                                player.sendSystemMessage(
                                    Component.literal(
                                        "§6 - §7$name"
                                    )
                                )
                            }
                        )
                }
            }
        }
    }
}