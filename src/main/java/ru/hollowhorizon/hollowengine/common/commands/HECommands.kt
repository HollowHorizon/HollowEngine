package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelector
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.world.entity.player.Player
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage.getCapability
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.common.capabilities.ACTIVE_REPLAYS
import ru.hollowhorizon.hollowengine.common.capabilities.ReplayStorageCapability
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.getAllDialogues
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.getAllStoryEvents
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.CopyItemPacket
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueExecutorThread
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryExecutorThread
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryPlayer
import ru.hollowhorizon.hollowengine.common.sendMessage
import ru.hollowhorizon.hollowengine.common.sendTranslate
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayRecorder.Companion.getRecorder
import java.io.File
import java.util.function.Consumer
import java.util.stream.Collectors

object HECommands {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack?>) {
        dispatcher.register(
            Commands.literal("hollowengine")
                .then(
                    Commands.literal("open-dialogue")
                        .then(
                            Commands.argument("dialogue", StringArgumentType.greedyString())
                                .suggests { _: CommandContext<CommandSourceStack?>?, builder: SuggestionsBuilder ->
                                    getAllDialogues().stream()
                                        .map { obj -> obj.toReadablePath() }
                                        .collect(Collectors.toSet())
                                        .forEach(
                                            Consumer { text -> builder.suggest(text) })
                                    builder.buildFuture()
                                }.executes { context ->
                                    val dialogue = StringArgumentType.getString(context, "dialogue")
                                    context.source.level.getCapability(
                                        getCapability(
                                            StoryTeamCapability::class.java
                                        )
                                    ).ifPresent { team: StoryTeamCapability ->
                                        try {
                                            val storyTeam = team.getOrCreateTeam(context.source.playerOrException)
                                            storyTeam.getAllOnline()
                                                .forEach(Consumer { storyPlayer: StoryPlayer ->
                                                    DialogueExecutorThread(
                                                        storyPlayer.mcPlayer!!,
                                                        dialogue.fromReadablePath()
                                                    ).start()
                                                })
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                    1
                                })
                )
                .then(Commands.literal("hand").executes { ctx: CommandContext<CommandSourceStack> ->
                    val player = ctx.source.playerOrException
                    val item = player.mainHandItem
                    val location = "\"" + ForgeRegistries.ITEMS.getKey(item.item).toString() + "\""
                    val count = item.count
                    val nbt = if (item.hasTag()) item.getOrCreateTag() else null
                    val itemCommand: String = if (nbt == null) {
                        if (count > 1) "item($location, $count)" else "item($location)"
                    } else {
                        "item($location, $count, \"" + nbt.toString()
                            .replace("\"".toRegex(), "\\\"") + "\")"
                    }
                    CopyItemPacket().send(itemCommand, player)
                    1
                })
                .then(Commands.literal("cutscene").executes { context: CommandContext<CommandSourceStack?>? -> 1 })
                .then(Commands.literal("create-npc").executes { context: CommandContext<CommandSourceStack?>? -> 1 })
                .then(Commands.literal("reset").executes { context: CommandContext<CommandSourceStack> ->
                    context.source.level.getCapability(
                        getCapability(
                            StoryTeamCapability::class.java
                        )
                    ).ifPresent { team: StoryTeamCapability ->
                        try {
                            val storyTeam = team.getOrCreateTeam(context.source.playerOrException)
                            storyTeam.completedEvents.clear()
                            context.source.playerOrException.sendMessage(TranslatableComponent(""))
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    1
                })
                .then(
                    Commands.literal("start-script")
                        .then(
                            Commands.argument<EntitySelector>("player", EntityArgument.players())
                                .then(
                                    Commands.argument<String>("script", StringArgumentType.greedyString())
                                        .suggests { context: CommandContext<CommandSourceStack?>?, builder: SuggestionsBuilder ->
                                            getAllStoryEvents().forEach(
                                                Consumer { file: File -> builder.suggest(file.toReadablePath()) })
                                            builder.buildFuture()
                                        }.executes { context: CommandContext<CommandSourceStack> ->
                                            val raw = StringArgumentType.getString(context, "script")
                                            val player = EntityArgument.getPlayer(context, "player")
                                            val script = raw.fromReadablePath()
                                            context.source.level.getCapability(
                                                getCapability(
                                                    StoryTeamCapability::class.java
                                                )
                                            ).ifPresent { team: StoryTeamCapability ->
                                                try {
                                                    val storyTeam = team.getOrCreateTeam(player)
                                                    StoryExecutorThread(storyTeam, script, true).start()
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            HollowCore.LOGGER.info("Started script $script")
                                            1
                                        })
                        )
                )
                .then(Commands.literal("active-events").executes { context: CommandContext<CommandSourceStack> ->
                    val player: Player = context.source.playerOrException
                    context.source.level.getCapability(
                        getCapability(
                            StoryTeamCapability::class.java
                        )
                    ).ifPresent { team: StoryTeamCapability ->
                        try {
                            val storyTeam = team.getOrCreateTeam(player)
                            val entries: Set<String> = storyTeam.currentEvents.keys
//                            player.sendSystemMessage(Component.literal("§6[§bActive Events§6]"))
                            player.sendTranslate("core.hollowengine.event")
                            for (entry in entries) {
                                player.sendMessage(TextComponent("§6- §b$entry"))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    1
                })
                .then(
                    Commands.literal("stop-event").then(
                        Commands.argument<String>("event", StringArgumentType.greedyString())
                            .suggests { context: CommandContext<CommandSourceStack>, builder: SuggestionsBuilder ->
                                val player: Player = context.source.playerOrException
                                context.source.level.getCapability(
                                    getCapability(
                                        StoryTeamCapability::class.java
                                    )
                                ).ifPresent { team: StoryTeamCapability ->
                                    try {
                                        val storyTeam = team.getOrCreateTeam(player)
                                        val entries: Set<String> = storyTeam.currentEvents.keys
                                        for (entry in entries) builder.suggest(entry)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                builder.buildFuture()
                            }.executes { context: CommandContext<CommandSourceStack> ->
                                val player: Player = context.source.playerOrException
                                val event = StringArgumentType.getString(context, "event")
                                context.source.level.getCapability(
                                    getCapability(
                                        StoryTeamCapability::class.java
                                    )
                                ).ifPresent { team: StoryTeamCapability ->
                                    try {
                                        val currentEvents =
                                            team.getOrCreateTeam(player).currentEvents
                                        currentEvents[event]!!.interrupt()
                                        player.sendTranslate("core.hollowengine.replay.recording.stop", event)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        player.sendTranslate("core.hollowengine.replay.recording.stop.failed.1", event)
                                        player.sendTranslate("core.hollowengine.replay.recording.stop.failed.2")
                                    }
                                }
                                1
                            })
                )
                .then(buildStringCommand("replay-create") { context: CommandSourceStack, name: String? ->
                    val player = try {
                        context.playerOrException
                    } catch (e: CommandSyntaxException) {
                        throw IllegalStateException(e)
                    }
                    val recorder = getRecorder(player)
                    if (!recorder.isRecording) {
                        recorder.startRecording(name!!)
                        player.sendTranslate("core.hollowengine.replay.recording.started")
                    } else {
                        player.sendTranslate("core.hollowengine.replay.recording.already")
                    }
                })
                .then(Commands.literal("replay-stop").executes { context: CommandContext<CommandSourceStack> ->
                    val player: Player = context.source.playerOrException
                    val recorder = getRecorder(player)
                    if (recorder.isRecording) {
                        recorder.stopRecording()
                        player.sendTranslate("core.hollowengine.replay.recording.stopped", recorder.replay.points.size)
                    } else {
                        player.sendTranslate("core.hollowengine.replay.recording.no_active")
                    }
                    1
                })
                .then(
                    Commands.literal("replay-play")
                        .then(
                            Commands.argument("replay", StringArgumentType.string())
                                .then(
                                    Commands.argument("npc", StringArgumentType.greedyString())
                                        .executes { context: CommandContext<CommandSourceStack> ->
                                            val replayName = StringArgumentType.getString(context, "replay")
                                            val npcName = StringArgumentType.getString(context, "npc")
                                            context.source.level.getCapability(
                                                getCapability(
                                                    ReplayStorageCapability::class.java
                                                )
                                            ).ifPresent { storage: ReplayStorageCapability ->
                                                val replay = storage.getReplay(replayName)
                                            }
                                            1
                                        })
                        )
                )
                .then(Commands.literal("stop-all-replays").executes { context: CommandContext<CommandSourceStack?>? ->
                    ACTIVE_REPLAYS.forEach(Consumer { obj: ReplayPlayer -> obj.destroy() })
                    ACTIVE_REPLAYS.clear()
                    1
                })
                .then(Commands.literal("show-all-replays").executes { context: CommandContext<CommandSourceStack> ->
                    val player: Player = context.source.playerOrException
                    player.sendTranslate("core.hollowengine.replay.founded", ACTIVE_REPLAYS.size)
                    1
                })
                .then(
                    Commands.literal("summon-npc").then(
                        Commands.argument("entity", StringArgumentType.greedyString())
                            .executes { context ->
                                val entity = StringArgumentType.getString(context, "entity")
                                context.source.playerOrException.sendTranslate("core.hollowengine.indev")
                                1
                            })
                )
                .then(Commands.literal("edit-action").executes { context: CommandContext<CommandSourceStack?>? -> 1 })
        )
    }
}
