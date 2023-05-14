package ru.hollowhorizon.hollowengine;

import net.minecraft.entity.monster.ZombieEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import ru.hollowhorizon.hc.HollowCore;
import ru.hollowhorizon.hc.api.registy.HollowMod;
import ru.hollowhorizon.hc.client.gltf.GltfModelSources;
import ru.hollowhorizon.hc.client.gltf.PathSource;
import ru.hollowhorizon.hollowengine.client.ClientEvents;
import ru.hollowhorizon.hollowengine.client.sound.HSSounds;
import ru.hollowhorizon.hollowengine.common.actions.PointTypes;
import ru.hollowhorizon.hollowengine.common.commands.HECommands;
import ru.hollowhorizon.hollowengine.common.events.StoryHandler;
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager;
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings;
import ru.hollowhorizon.hollowengine.common.npcs.NPCStorage;
import ru.hollowhorizon.hollowengine.common.registry.ModEntities;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

@HollowMod(MODID)
@Mod(MODID)
public class HollowEngine {
    public static final String MODID = "hollowengine";

    public HollowEngine() {
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        HollowCore.LOGGER.info("HollowEngine mod loading...");
        //ResourcePackAdapter.registerResourcePack(HollowStoryPack.Companion.getPackInstance());

        forgeBus.addListener(this::registerCommands);
        forgeBus.addListener(this::addReloadListenerEvent);
        forgeBus.addListener(StoryHandler::onPlayerJoin);
        forgeBus.addListener(StoryHandler::onPlayerTick);
        forgeBus.addListener(StoryHandler::onPlayerClone);

        modBus.addListener(this::setup);
        modBus.addListener(this::onAttributeCreation);
        modBus.addListener(this::onLoadingComplete);

        GltfModelSources.INSTANCE.addSource(new PathSource(FMLPaths.GAMEDIR.get().resolve("hollowengine")));
        GltfModelSources.INSTANCE.addSource(new PathSource(FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve("models")));

        if (FMLEnvironment.dist.isClient()) {
            forgeBus.addListener(ClientEvents::renderLast);
            forgeBus.addListener(ClientEvents::onKeyPressed);
            modBus.addListener(this::clientInit);
            HSSounds.init();
        }

        PointTypes.init();
    }

    public void addReloadListenerEvent(AddReloadListenerEvent event) {
        //event.addListener(new DialogueReloadListener());
    }

    @OnlyIn(Dist.CLIENT)
    private void clientInit(FMLClientSetupEvent event) {
        ClientEvents.INSTANCE.initKeys();

    }

    private void setup(FMLCommonSetupEvent event) {
        DirectoryManager.init();
        NPCStorage.addNPC("Монстр", new NPCSettings());
    }

    private void onLoadingComplete(FMLLoadCompleteEvent event) {

    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NPC_ENTITY, ZombieEntity.createAttributes().build());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        HECommands.register(event.getDispatcher());
    }
}