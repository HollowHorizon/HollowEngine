package ru.hollowhorizon.hollowstory.common.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import kotlin.io.TextStreamsKt;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hc.api.registy.HollowPacket;
import ru.hollowhorizon.hc.client.utils.HollowNBTSerializer;
import ru.hollowhorizon.hc.client.utils.NBTUtils;
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2;
import ru.hollowhorizon.hc.common.network.UniversalPacket;
import ru.hollowhorizon.hollowstory.client.gui.DialogueScreen;
import ru.hollowhorizon.hollowstory.client.gui.NPCCreationScreen;
import ru.hollowhorizon.hollowstory.client.gui.VisualEditorScreen;
import ru.hollowhorizon.hollowstory.common.capabilities.ReplayStorageCapability;
import ru.hollowhorizon.hollowstory.common.capabilities.ReplayStorageCapabilityKt;
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity;
import ru.hollowhorizon.hollowstory.common.hollowscript.story.StoryExecutorThread;
import ru.hollowhorizon.hollowstory.common.hollowscript.story.StoryStorage;
import ru.hollowhorizon.hollowstory.common.npcs.NPCData;
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings;
import ru.hollowhorizon.hollowstory.cutscenes.replay.*;
import ru.hollowhorizon.hollowstory.story.StoryVariables;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class HSCommands {
    @HollowPacket
    public static final UniversalPacket<Void> OPEN_NPC_EDITOR_GUI = new UniversalPacket<Void>() {
        @Override
        public HollowNBTSerializer<Void> serializer() {
            return NBTUtils.NONE;
        }

        @Override
        public void onReceived(PlayerEntity playerEntity, Void unused) {
            NPCCreationScreen.openGUI();
        }
    };
    @HollowPacket
    public static final UniversalPacket<Void> OPEN_ACTION_EDITOR_GUI = new UniversalPacket<Void>() {
        @Override
        public HollowNBTSerializer<Void> serializer() {
            return NBTUtils.NONE;
        }

        @Override
        public void onReceived(PlayerEntity playerEntity, Void unused) {
            VisualEditorScreen.Companion.openGUI();
        }
    };
    @HollowPacket
    public static final UniversalPacket<String> OPEN_DIALOGUE_GUI = new UniversalPacket<String>() {
        @Override
        public HollowNBTSerializer<String> serializer() {
            return NBTUtils.STRING_SERIALIZER;
        }

        @Override
        public void onReceived(PlayerEntity playerEntity, String dialogue) {
            execute(dialogue);
        }

        @OnlyIn(Dist.CLIENT)
        private void execute(String dialogue) {
            ResourceLocation location = new ResourceLocation(dialogue);
            Minecraft.getInstance().setScreen(new DialogueScreen(location, () -> null));
        }
    };

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        dispatcher.register(Commands.literal("hollow-story")
                .then(Commands.literal("create-npc").executes(context -> {
                    OPEN_NPC_EDITOR_GUI.sendToClient(context.getSource().getPlayerOrException(), null);
                    return 1;
                }))
                .then(Commands.literal("open-dialogue")
                        .then(Commands.argument("dialogue", StringArgumentType.string()).suggests((context, builder) -> {
                            Minecraft.getInstance().getResourceManager()
                                    .listResources("dialogues", name -> name.endsWith(".hsd.kts"))
                                    .forEach(resourceLocation ->
                                            builder.suggest(resourceLocation.getNamespace() + "." +
                                                    resourceLocation.getPath()
                                                            .replace(".hsd.kts", "")
                                                            .replace("/", ".")
                                            )
                                    );
                            return builder.buildFuture();
                        }).executes(context -> {
                            String dialogue = StringArgumentType.getString(context, "dialogue");
                            OPEN_DIALOGUE_GUI.sendToClient(context.getSource().getPlayerOrException(), dialogue.replaceFirst("\\.", ":").replace(".", "/") + ".hsd.kts");


                            return 1;
                        }))
                )
                .then(Commands.literal("start-script")
                        .then(Commands.argument("script", StringArgumentType.string()).executes(context -> {
                            String raw = StringArgumentType.getString(context, "script");
                            String script = raw.replaceFirst("\\.", ":").replace(".", "/") + ".se.kts";

                            try {
                                InputStream s = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(script)).getInputStream();

                                String code = TextStreamsKt.readText(new InputStreamReader(s));

                                try {
                                    new StoryExecutorThread(StoryStorage.INSTANCE.getTeam(context.getSource().getPlayerOrException()), new StoryVariables(), raw, code).start();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                HollowCore.LOGGER.info("Started script " + script);

                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

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
                    context.getSource().getLevel().getCapability(HollowCapabilityV2.Companion.get(ReplayStorageCapability.class)).ifPresent(storage -> storage.getValue().getAllReplays().keySet().forEach(replay -> {
                        player.sendMessage(new StringTextComponent(replay), player.getUUID());
                    }));
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
                            OPEN_ACTION_EDITOR_GUI.sendToClient(context.getSource().getPlayerOrException(), null);
                            return 1;
                        }
                ))
        );
    }
}
