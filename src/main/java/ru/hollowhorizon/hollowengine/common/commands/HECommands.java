package ru.hollowhorizon.hollowengine.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage;
import ru.hollowhorizon.hc.common.network.HollowPacketV2Kt;
import ru.hollowhorizon.hollowengine.common.capabilities.ReplayStorageCapability;
import ru.hollowhorizon.hollowengine.common.capabilities.ReplayStorageCapabilityKt;
import ru.hollowhorizon.hollowengine.common.capabilities.StoryTeamCapability;
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager;
import ru.hollowhorizon.hollowengine.common.network.CopyItemPacket;
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.DialogueExecutorThread;
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryTeam;
import ru.hollowhorizon.hollowengine.cutscenes.replay.Replay;
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer;
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayRecorder;

import java.io.File;
import java.util.Objects;
import java.util.stream.Collectors;

import static ru.hollowhorizon.hollowengine.common.scripting.story.StoryExecutorThreadKt.runScript;

public class HECommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("hollowengine")
                .then(Commands.literal("open-dialogue")
                        .then(Commands.argument("dialogue", StringArgumentType.greedyString()).suggests((context, builder) -> {
                            DirectoryManager.INSTANCE.getAllDialogues().stream().map(DirectoryManager::toReadablePath).collect(Collectors.toSet()).forEach(builder::suggest);
                            return builder.buildFuture();
                        }).executes(context -> {
                            String dialogue = StringArgumentType.getString(context, "dialogue");

                            context.getSource().getLevel().getCapability(CapabilityStorage.getCapability(StoryTeamCapability.class)).ifPresent(team -> {
                                try {
                                    StoryTeam storyTeam = team.getOrCreateTeam(context.getSource().getPlayerOrException());

                                    storyTeam.getAllOnline().forEach(storyPlayer -> new DialogueExecutorThread(Objects.requireNonNull(storyPlayer.getMcPlayer()), DirectoryManager.fromReadablePath(dialogue)).start());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });

                            return 1;
                        }))
                )
                .then(Commands.literal("hand").executes(ctx -> {
                    var player = ctx.getSource().getPlayerOrException();
                    var item = player.getMainHandItem();

                    var location = "\"" + ForgeRegistries.ITEMS.getKey(item.getItem()).toString() + "\"";
                    var count = item.getCount();
                    var nbt = item.hasTag() ? item.getOrCreateTag() : null;

                    String itemCommand;
                    if (nbt == null) {
                        if (count > 1) itemCommand = "item(" + location + ", " + count + ")";
                        else itemCommand = "item(" + location + ")";
                    } else {

                        itemCommand = "item(" + location + ", " + count + ", \"" + nbt.toString().replaceAll("\"", "\\\"") + "\")";
                    }

                    HollowPacketV2Kt.send(new CopyItemPacket(), itemCommand, player);

                    return 1;
                }))
                .then(Commands.literal("cutscene").executes(context -> {
                    //Minecraft.getInstance().setScreen(new CutsceneWorldEditScreen());
                    return 1;
                }))
                .then(Commands.literal("create-npc").executes(context -> {
                    //Minecraft.getInstance().setScreen(new NPCBuilderScreen());
                    return 1;
                }))
                .then(Commands.literal("reset").executes(context -> {
                    context.getSource().getLevel().getCapability(CapabilityStorage.getCapability(StoryTeamCapability.class)).ifPresent(team -> {
                        try {
                            StoryTeam storyTeam = team.getOrCreateTeam(context.getSource().getPlayerOrException());
                            storyTeam.getCompletedEvents().clear();
                            context.getSource().getPlayerOrException().sendSystemMessage(Component.literal("§6[§bHollow Story§6] §bCompleted events reset successfully!"));
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
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    File script = DirectoryManager.fromReadablePath(raw);


                                    context.getSource().getLevel().getCapability(CapabilityStorage.getCapability(StoryTeamCapability.class)).ifPresent(team -> {
                                        try {
                                            Team storyTeam = FTBTeamsAPI.getPlayerTeam(player);
                                            runScript(player.server, storyTeam, script);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    });
                                    HollowCore.LOGGER.info("Started script " + script);

                                    return 1;
                                }))))
                .then(Commands.literal("active-events").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    context.getSource().getLevel().getCapability(CapabilityStorage.getCapability(StoryTeamCapability.class)).ifPresent(team -> {
                        try {

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return 1;
                }))
                .then(Commands.literal("stop-event").then(Commands.argument("event", StringArgumentType.greedyString()).suggests((context, builder) -> {
                    Player player = context.getSource().getPlayerOrException();
                    context.getSource().getLevel().getCapability(CapabilityStorage.getCapability(StoryTeamCapability.class)).ifPresent(team -> {
                        try {
                            StoryTeam storyTeam = team.getOrCreateTeam(player);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    return builder.buildFuture();
                }).executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    String event = StringArgumentType.getString(context, "event");
                    context.getSource().getLevel().getCapability(CapabilityStorage.getCapability(StoryTeamCapability.class)).ifPresent(team -> {
                        try {
                            player.sendSystemMessage(Component.literal("§6[§bHollow Story§6] §bForcely stopped event §6" + event));
                        } catch (Exception e) {
                            e.printStackTrace();
                            player.sendSystemMessage(Component.literal("§6[§bHollow Story§6] §bFailed to stop event §6" + event));
                            player.sendSystemMessage(Component.literal("§6[§bHollow Story§6] §bSee logs for more info."));
                        }
                    });
                    return 1;
                })))
                .then(CommandBuilderKt.buildStringCommand("replay-create", (context, name) -> {
                    Player player;
                    try {
                        player = context.getPlayerOrException();
                    } catch (CommandSyntaxException e) {
                        throw new IllegalStateException(e);
                    }
                    ReplayRecorder recorder = ReplayRecorder.Companion.getRecorder(player);
                    if (!recorder.isRecording()) {
                        recorder.startRecording(name);
                        player.sendSystemMessage(Component.literal("Started recording"));
                    } else {
                        player.sendSystemMessage(Component.literal("Already recording"));
                    }
                    return null;
                }))
                .then(Commands.literal("replay-stop").executes(context -> {
                    Player player = context.getSource().getPlayerOrException();
                    ReplayRecorder recorder = ReplayRecorder.Companion.getRecorder(player);
                    if (recorder.isRecording()) {
                        recorder.stopRecording();
                        player.sendSystemMessage(Component.literal("Stopped recording, total frames: " + recorder.getReplay().getPoints().size()));
                    } else {
                        player.sendSystemMessage(Component.literal("Not recording"));
                    }
                    return 1;
                }))
                .then(Commands.literal("replay-play")
                        .then(Commands.argument("replay", StringArgumentType.string())
                                .then(Commands.argument("npc", StringArgumentType.greedyString()).executes(context -> {
                                    String replayName = StringArgumentType.getString(context, "replay");
                                    String npcName = StringArgumentType.getString(context, "npc");

                                    context.getSource().getLevel().getCapability(CapabilityStorage.getCapability(ReplayStorageCapability.class)).ifPresent(storage -> {
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
                    Player player = context.getSource().getPlayerOrException();
                    player.sendSystemMessage(Component.literal("Найдено записей: " + ReplayStorageCapabilityKt.getACTIVE_REPLAYS().size()));

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
