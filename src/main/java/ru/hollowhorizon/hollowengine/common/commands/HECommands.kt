package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.register
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.common.events.StoryHandler.getActiveEvents
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.getAllStoryEvents
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.CopyTextPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript
import ru.hollowhorizon.hollowengine.common.sendMessage
import thedarkcolour.kotlinforforge.forge.vectorutil.component1
import thedarkcolour.kotlinforforge.forge.vectorutil.component2
import thedarkcolour.kotlinforforge.forge.vectorutil.component3
import java.util.function.Consumer

object HECommands {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register {
            "hollowengine" {
                "hand" {

                    val player = source.playerOrException
                    val item = player.mainHandItem
                    val location = "\"" + ForgeRegistries.ITEMS.getKey(item.item).toString() + "\""
                    val count = item.count
                    val nbt = if (item.hasTag()) item.getOrCreateTag() else null
                    val itemCommand = when {
                        nbt == null && count > 1 -> "item($location, $count)"
                        nbt == null && count == 1 -> "item($location)"
                        else -> {
                            "item($location, $count, \"${nbt.toString()
                                .replace("\"", "\\\"")
                            }\")"
                        }
                    }
                    CopyTextPacket().send(itemCommand, player)
                }

                "pos" {
                    val player = source.playerOrException
                    val (x, y, z) = player.pick(100.0, 0.0f, true).location
                    CopyTextPacket().send("pos($x, $y, $z)", player)
                }

                "start-script"(
                    arg("players", EntityArgument.players()),
                    arg("script", StringArgumentType.greedyString(), getAllStoryEvents().map { it.toReadablePath() })
                ) {
                    val players = EntityArgument.getPlayers(this, "players")
                    val raw = StringArgumentType.getString(this, "script")
                    val script = raw.fromReadablePath()
                    players.forEach { player ->
                        val storyTeam = FTBTeamsAPI.api().manager.getTeamForPlayer(player).orElseThrow()
                        runScript(player.server, storyTeam, script, true)
                    }
                    HollowCore.LOGGER.info("Started script $script")
                }

                "active-events" {
                    val player = source.playerOrException
                    val storyTeam = FTBTeamsAPI.api().manager.getTeamForPlayer(player)
                    player.sendMessage(TranslatableComponent("hollowengine.commands.actiove_events"), player.uuid)
                    getActiveEvents(storyTeam.get())
                        .ifEmpty{ mutableListOf("No active events") }
                        .forEach(
                        Consumer { name: String ->
                            player.sendMessage(
                                TextComponent(
                                    "§6 - §7$name"
                                ),
                                player.uuid
                            )
                        }
                    )
                }
            }
        }
    }
}
