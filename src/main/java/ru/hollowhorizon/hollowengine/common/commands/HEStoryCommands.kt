/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.screens.ImGuiScreen
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.onRegisterCommands
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.gui.dialogue.SonharDialogueGui
import ru.hollowhorizon.hollowengine.client.utils.roundTo
import ru.hollowhorizon.hollowengine.common.capabilities.PlayerStoryCapability
import ru.hollowhorizon.hollowengine.common.capabilities.StoriesCapability
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
        dispatcher.onRegisterCommands {
            "hollowengine" {
                "pos" {
                    val player = source.playerOrException
                    val loc = player.pick(100.0, 0.0f, true).location
                    CopyTextPacket("pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})").send(
                        PacketDistributor.PLAYER.with { player })
                }

                "start-script"(
                    arg("players", EntityArgument.players()),
                    arg(
                        "script",
                        StringArgumentType.greedyString(),
                        DirectoryManager.getStoryEvents().map { it.toReadablePath() })
                ) {
                    val players = EntityArgument.getPlayers(this, "players")
                    val raw = StringArgumentType.getString(this, "script")
                    val script = raw.fromReadablePath()
                    players.forEach { player ->
                        runScript(player.server, script, true)
                    }
                    HollowCore.LOGGER.info("Started script $script")
                }

                "stop-script"(
                    arg("players", EntityArgument.players()),
                    arg(
                        "script",
                        StringArgumentType.greedyString(),
                        DirectoryManager.getStoryEvents().map { it.toReadablePath() })
                ) {
                    val players = EntityArgument.getPlayers(this, "players")
                    val eventPath = StringArgumentType.getString(this, "script")
                    players.forEach {
                        StoryHandler.stopEvent(eventPath)
                    }
                }

                "clear-marks" {
                    val player = source.playerOrException

                    player[PlayerStoryCapability::class].aimMarks.clear()
                }

                "active-events" {
                    val player = source.playerOrException
                    player.sendSystemMessage(Component.translatable("hollowengine.commands.active_events"))
                    StoryHandler.getActiveEvents()
                        .ifEmpty { mutableListOf("No active events.") }
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

                "active-npcs" {
                    val player = source.playerOrException
                    val npcs = source.server.overworld()[StoriesCapability::class].activeNpcs.values

                    if (npcs.isNotEmpty()) {
                        player.sendSystemMessage("§6На данный момент в игре есть нпс:".mcText)
                        npcs.forEach { name ->
                            player.sendSystemMessage("§6 - §7$name".mcText)
                        }
                    } else {
                        player.sendSystemMessage("No active npcs.".mcText)
                    }
                }

                "remove-npc"(
                    arg(
                        "npc",
                        StringArgumentType.greedyString(),
                        ServerLifecycleHooks.getCurrentServer()?.overworld()
                            ?.get(StoriesCapability::class)?.activeNpcs?.values ?: emptyList()
                    )
                ) {
                    source.server.overworld()[StoriesCapability::class].activeNpcs.values.remove(
                        StringArgumentType.getString(
                            this,
                            "npc"
                        )
                    )
                }
            }
        }
    }
}