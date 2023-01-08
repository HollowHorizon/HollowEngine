package ru.hollowhorizon.hollowstory;

import net.minecraft.entity.monster.ZombieEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hc.api.registy.HollowMod;
import ru.hollowhorizon.hollowstory.client.render.enitites.NPCRenderer;
import ru.hollowhorizon.hollowstory.client.sound.HSSounds;
import ru.hollowhorizon.hollowstory.common.actions.PointTypes;
import ru.hollowhorizon.hollowstory.common.commands.HSCommands;
import ru.hollowhorizon.hollowstory.common.data.DialogueReloadListener;
import ru.hollowhorizon.hollowstory.common.data.HollowStoryPack;
import ru.hollowhorizon.hollowstory.common.entities.ModEntities;
import ru.hollowhorizon.hollowstory.common.hollowscript.story.StoryHandler;
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings;
import ru.hollowhorizon.hollowstory.common.npcs.NPCStorage;

import static ru.hollowhorizon.hollowstory.HollowStory.MODID;

@HollowMod(MODID)
@Mod(MODID)
public class HollowStory {
    public static final String MODID = "hollowstory";

    public HollowStory() {
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        HollowCore.LOGGER.info("HollowStory mod loading...");

        ModEntities.ENTITIES.register(modBus);
        //ResourcePackAdapter.registerResourcePack(HollowStoryPack.Companion.getPackInstance());

        forgeBus.addListener(this::registerCommands);
        forgeBus.addListener(this::addReloadListenerEvent);
        forgeBus.addListener(StoryHandler::onPlayerJoin);
        forgeBus.addListener(StoryHandler::onServerStart);
        forgeBus.addListener(StoryHandler::onPlayerClone);

        modBus.addListener(this::setup);
        modBus.addListener(this::clientInit);
        modBus.addListener(this::onAttributeCreation);
        modBus.addListener(this::onLoadingComplete);

        PointTypes.init();
        HSSounds.init();
    }

    public void addReloadListenerEvent(AddReloadListenerEvent event) {
        event.addListener(new DialogueReloadListener());
    }

    private void clientInit(FMLClientSetupEvent event) {
        RenderingRegistry.registerEntityRenderingHandler(ModEntities.NPC_ENTITY.get(), NPCRenderer::new);
    }

    private void setup(FMLCommonSetupEvent event) {
        NPCStorage.addNPC("Монстр", new NPCSettings());
    }

    private void onLoadingComplete(FMLLoadCompleteEvent event) {

    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NPC_ENTITY.get(), ZombieEntity.createAttributes().build());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        HSCommands.register(event.getDispatcher());
    }
}