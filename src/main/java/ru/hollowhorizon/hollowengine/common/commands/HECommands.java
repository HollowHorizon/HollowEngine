package ru.hollowhorizon.hollowengine.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2;
import ru.hollowhorizon.hollowengine.common.capabilities.ReplayStorageCapability;
import ru.hollowhorizon.hollowengine.common.capabilities.ReplayStorageCapabilityKt;
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability;
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager;
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueExecutorThread;
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryExecutorThread;
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam;
import ru.hollowhorizon.hollowengine.cutscenes.replay.Replay;
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer;
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayRecorder;

import java.io.File;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

public class HECommands {
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("hollowengine")
                .then(Commands.literal("open-dialogue")
                        .then(Commands.argument("dialogue", StringArgumentType.greedyString()).suggests((context, builder) -> {
                            DirectoryManager.INSTANCE.getAllDialogues().forEach(file -> builder.suggest(DirectoryManager.toReadablePath(file)));
                            return builder.buildFuture();
                        }).executes(context -> {
                            String dialogue = StringArgumentType.getString(context, "dialogue");

                            context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(StoryTeamCapability.class)).ifPresent(team -> {
                                try {
                                    StoryTeam storyTeam = team.getTeam(context.getSource().getPlayerOrException());

                                    storyTeam.getAllOnline().forEach(storyPlayer -> new DialogueExecutorThread(Objects.requireNonNull(storyPlayer.getMcPlayer()), DirectoryManager.fromReadablePath(dialogue)).start());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });

                            return 1;
                        }))
                )
                .then(Commands.literal("cutscene").executes(context -> {
                    //Minecraft.getInstance().setScreen(new CutsceneWorldEditScreen());
                    return 1;
                }))
                .then(Commands.literal("create-npc").executes(context -> {
                    //Minecraft.getInstance().setScreen(new NPCBuilderScreen());
                    return 1;
                }))
                .then(Commands.literal("reset").executes(context -> {
                    context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(StoryTeamCapability.class)).ifPresent(team -> {
                        try {
                            StoryTeam storyTeam = team.getTeam(context.getSource().getPlayerOrException());
                            storyTeam.getCompletedEvents().clear();
                            context.getSource().getPlayerOrException().sendMessage(new StringTextComponent("§6[§bHollow Story§6] §bCompleted events reset successfully!"), context.getSource().getPlayerOrException().getUUID());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return 1;
                }))
                .then(Commands.literal("start-script")
                        .then(Commands.argument("player", EntityArgument.players())
                                .then(Commands.argument("script", StringArgumentType.greedyString()).suggests((context, builder) -> {
                                    DirectoryManager.INSTANCE.getAllStoryEvents().forEach(file -> builder.suggest(DirectoryManager.toReadablePath(file)));
                                    return builder.buildFuture();
                                }).executes(context -> {
                                    String raw = StringArgumentType.getString(context, "script");
                                    ServerPlayerEntity player = EntityArgument.getPlayer(context, "player");
                                    File script = DirectoryManager.fromReadablePath(raw);


                                    context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(StoryTeamCapability.class)).ifPresent(team -> {
                                        try {
                                            StoryTeam storyTeam = team.getTeam(player);
                                            new StoryExecutorThread(storyTeam, script, true).start();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    HollowCore.LOGGER.info("Started script " + script);

                                    return 1;
                                }))))
                .then(Commands.literal("active-events").executes(context -> {
                    PlayerEntity player = context.getSource().getPlayerOrException();
                    context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(StoryTeamCapability.class)).ifPresent(team -> {
                        try {
                            StoryTeam storyTeam = team.getTeam(player);
                            Set<String> entries = storyTeam.getCurrentEvents().keySet();
                            player.sendMessage(new StringTextComponent("§6[§bActive Events§6]"), player.getUUID());
                            for (String entry : entries) {
                                player.sendMessage(new StringTextComponent("§6- §b" + entry), player.getUUID());
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return 1;
                }))
                .then(Commands.literal("stop-event").then(Commands.argument("event", StringArgumentType.greedyString()).suggests((context, builder) -> {
                    PlayerEntity player = context.getSource().getPlayerOrException();
                    context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(StoryTeamCapability.class)).ifPresent(team -> {
                        try {
                            StoryTeam storyTeam = team.getTeam(player);
                            Set<String> entries = storyTeam.getCurrentEvents().keySet();
                            for (String entry : entries) builder.suggest(entry);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return builder.buildFuture();
                }).executes(context -> {
                    PlayerEntity player = context.getSource().getPlayerOrException();
                    String event = StringArgumentType.getString(context, "event");
                    context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(StoryTeamCapability.class)).ifPresent(team -> {
                        try {
                            HashMap<String, StoryExecutorThread> currentEvents = team.getTeam(player).getCurrentEvents();

                            currentEvents.get(event).interrupt();

                            player.sendMessage(new StringTextComponent("§6[§bHollow Story§6] §bForcedly stopped event §6" + event), player.getUUID());
                        } catch (Exception e) {
                            e.printStackTrace();
                            player.sendMessage(new StringTextComponent("§6[§bHollow Story§6] §bFailed to stop event §6" + event), player.getUUID());
                            player.sendMessage(new StringTextComponent("§6[§bHollow Story§6] §bSee logs for more info."), player.getUUID());
                        }
                    });
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
