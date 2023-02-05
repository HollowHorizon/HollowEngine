package ru.hollowhorizon.hollowstory.common.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlinx.serialization.Serializable;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.server.command.EnumArgument;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2;
import ru.hollowhorizon.hc.common.network.HollowPacketV2;
import ru.hollowhorizon.hc.common.network.Packet;
import ru.hollowhorizon.hc.common.story.events.StoryEventStarter;
import ru.hollowhorizon.hollowstory.client.screen.CutsceneWorldEditScreen;
import ru.hollowhorizon.hollowstory.client.screen.DialogueScreen;
import ru.hollowhorizon.hollowstory.client.screen.NPCBuilderScreen;
import ru.hollowhorizon.hollowstory.common.capabilities.ReplayStorageCapability;
import ru.hollowhorizon.hollowstory.common.capabilities.ReplayStorageCapabilityKt;
import ru.hollowhorizon.hollowstory.common.capabilities.StoryTeamCapabilityKt;
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity;
import ru.hollowhorizon.hollowstory.common.files.HollowStoryDirHelper;
import ru.hollowhorizon.hollowstory.common.hollowscript.story.StoryExecutorThread;
import ru.hollowhorizon.hollowstory.common.npcs.NPCData;
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings;
import ru.hollowhorizon.hollowstory.cutscenes.replay.Replay;
import ru.hollowhorizon.hollowstory.cutscenes.replay.ReplayPlayer;
import ru.hollowhorizon.hollowstory.cutscenes.replay.ReplayRecorder;
import ru.hollowhorizon.hollowstory.story.StoryTeam;
import ru.hollowhorizon.hollowstory.story.StoryVariables;

import java.io.File;

public class HSCommands {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("hollow-story")
                .then(Commands.literal("open-dialogue")
                        .then(Commands.argument("dialogue", StringArgumentType.greedyString()).suggests((context, builder) -> {
                            HollowStoryDirHelper.INSTANCE.getAllDialogues().forEach(file -> builder.suggest(HollowStoryDirHelper.INSTANCE.toReadablePath(file)));
                            return builder.buildFuture();
                        }).executes(context -> {
                            String dialogue = StringArgumentType.getString(context, "dialogue");
                            new OpenDialoguePacket().send(dialogue, context.getSource().getPlayerOrException());


                            return 1;
                        }).then(Commands.argument("player", EntityArgument.players()).executes((command) -> {
                            for (ServerPlayerEntity p : EntityArgument.getPlayers(command, "player")) {
                                String dialogue = StringArgumentType.getString(command, "dialogue");
                                new OpenDialoguePacket().send(dialogue, p);
                            }
                            return Command.SINGLE_SUCCESS;
                        })))
                )
                .then(Commands.literal("cutscene").executes(context -> {
                    Minecraft.getInstance().setScreen(new CutsceneWorldEditScreen());
                    return 1;
                }))
                .then(Commands.literal("create-npc").executes(context -> {
                    Minecraft.getInstance().setScreen(new NPCBuilderScreen());
                    return 1;
                }))
                .then(Commands.literal("start-script")
                        .then(Commands.argument("script", StringArgumentType.greedyString()).suggests((context, builder) -> {
                            HollowStoryDirHelper.INSTANCE.getAllStoryEvents().forEach(file -> builder.suggest(HollowStoryDirHelper.INSTANCE.toReadablePath(file)));
                            return builder.buildFuture();
                        }).executes(context -> {
                            String raw = StringArgumentType.getString(context, "script");
                            File script = HollowStoryDirHelper.INSTANCE.fromReadablePath(raw);

                            try {
                                StoryTeam team = new StoryTeam();
                                team.add(context.getSource().getPlayerOrException());
                                new StoryExecutorThread(team, new StoryVariables(), script).start();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            HollowCore.LOGGER.info("Started script " + script);

                            return 1;
                        })))
                .then(CommandBuilderKt.buildStringCommand("replay-create", (context, name) -> {
                    PlayerEntity player;
                    try {
                        player = context.getPlayerOrException();
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException(e);
                    }
                    ReplayRecorder recorder = ReplayRecorder.Companion.getRecorder(player);
                    if (!recorder.isRecording()) {
                        recorder.startRecording(name);
                        player.sendMessage(new StringTextComponent("Started recording"), player.getUUID());
                    } else {
                        player.sendMessage(new StringTextComponent("Already recording"), player.getUUID());
                    }
                    return null;
                }))
                .then(Commands.literal("replay-stop").executes(context -> {
                    PlayerEntity player = context.getSource().getPlayerOrException();
                    ReplayRecorder recorder = ReplayRecorder.Companion.getRecorder(player);
                    if (recorder.isRecording()) {
                        recorder.stopRecording();
                        player.sendMessage(new StringTextComponent("Stopped recording, total frames: " + recorder.getReplay().getPoints().size()), player.getUUID());
                    } else {
                        player.sendMessage(new StringTextComponent("Not recording"), player.getUUID());
                    }
                    return 1;
                }))
                .then(Commands.literal("replay-play")
                        .then(Commands.argument("replay", StringArgumentType.string())
                                .then(Commands.argument("npc", StringArgumentType.greedyString()).executes(context -> {
                                    String replayName = StringArgumentType.getString(context, "replay");
                                    String npcName = StringArgumentType.getString(context, "npc");

                                    context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(ReplayStorageCapability.class)).ifPresent(storage -> {
                                        Replay replay = storage.getReplay(replayName);

                                        NPCEntity npc = new NPCEntity(new NPCSettings("Какой-то крутой NPC", npcName, new NPCData()), context.getSource().getLevel());

                                        ReplayPlayer player = new ReplayPlayer(npc);
                                        player.play(context.getSource().getLevel(), replay);
                                    });

                                    return 1;
                                }))))
                .then(Commands.literal("stop-all-replays").executes(context -> {
                    ReplayStorageCapabilityKt.getACTIVE_REPLAYS().forEach(ReplayPlayer::destroy);
                    ReplayStorageCapabilityKt.getACTIVE_REPLAYS().clear();
                    return 1;
                }))
                .then(Commands.literal("show-all-replays").executes(context -> {
                    PlayerEntity player = context.getSource().getPlayerOrException();
                    player.sendMessage(new StringTextComponent("Найдено записей: " + ReplayStorageCapabilityKt.getACTIVE_REPLAYS().size()), player.getUUID());

                    return 1;
                }))
                .then(Commands.literal("summon-npc").then(Commands.argument("entity", StringArgumentType.greedyString()).executes(context -> {
                    String entity = StringArgumentType.getString(context, "entity");

                    NPCEntity npc = new NPCEntity(new NPCSettings("ЫыыЫыы", entity, new NPCData()), context.getSource().getLevel());

                    Vector3d playerPos = context.getSource().getPosition();

                    npc.setPos(playerPos.x, playerPos.y, playerPos.z);

                    context.getSource().getLevel().addFreshEntity(npc);
                    return 1;
                })))
                .then(Commands.literal("edit-action").executes(context -> {
                            //OPEN_ACTION_EDITOR_GUI.sendToClient(context.getSource().getPlayerOrException(), null);
                            return 1;
                        }
                ))
        );
    }
}
