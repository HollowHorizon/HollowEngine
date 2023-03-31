package ru.hollowhorizon.hollowengine;

import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.entity.monster.ZombieEntity;
import net.minecraft.resources.IReloadableResourceManager;
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
import ru.hollowhorizon.hollowengine.client.ClientEvents;
import ru.hollowhorizon.hollowengine.client.render.enitites.NPCRenderer;
import ru.hollowhorizon.hollowengine.client.render.enitites.ThomasRenderer;
import ru.hollowhorizon.hollowengine.client.sound.HSSounds;
import ru.hollowhorizon.hollowengine.common.actions.PointTypes;
import ru.hollowhorizon.hollowengine.common.commands.HSCommands;
import ru.hollowhorizon.hollowengine.common.entities.ModEntities;
import ru.hollowhorizon.hollowengine.common.events.StoryHandler;
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager;
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings;
import ru.hollowhorizon.hollowengine.common.npcs.NPCStorage;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

@HollowMod(MODID)
@Mod(MODID)
public class HollowEngine {
    public static final String MODID = "hollowengine";

    public HollowEngine() {
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        HollowCore.LOGGER.info("HollowEngine mod loading...");

        ModEntities.ENTITIES.register(modBus);
        //ResourcePackAdapter.registerResourcePack(HollowStoryPack.Companion.getPackInstance());

        forgeBus.addListener(this::registerCommands);
        forgeBus.addListener(this::addReloadListenerEvent);
        forgeBus.addListener(ClientEvents::renderLast);
        forgeBus.addListener(ClientEvents::onKeyPressed);
        forgeBus.addListener(StoryHandler::onPlayerJoin);
        forgeBus.addListener(StoryHandler::onPlayerTick);
        forgeBus.addListener(StoryHandler::onPlayerClone);

        modBus.addListener(this::setup);
        modBus.addListener(this::clientInit);
        modBus.addListener(this::onAttributeCreation);
        modBus.addListener(this::onLoadingComplete);

        PointTypes.init();
        HSSounds.init();
    }

    public void addReloadListenerEvent(AddReloadListenerEvent event) {
        //event.addListener(new DialogueReloadListener());
    }

    private void clientInit(FMLClientSetupEvent event) {
        ClientEvents.INSTANCE.initKeys();

        RenderingRegistry.registerEntityRenderingHandler(ModEntities.NPC_ENTITY.get(), manager -> {
            try {
                return new NPCRenderer(manager);
            } catch (Exception ex) {
                HollowCore.LOGGER.error("ERROR_ERROR");
                ex.printStackTrace();
            }
            return null;
        });

        RenderingRegistry.registerEntityRenderingHandler(ModEntities.THOMAS.get(), (manager) -> new ThomasRenderer(manager, (IReloadableResourceManager) event.getMinecraftSupplier().get().getResourceManager()));
    }

    private void setup(FMLCommonSetupEvent event) {
        DirectoryManager.init();
        NPCStorage.addNPC("Монстр", new NPCSettings());
    }

    private void onLoadingComplete(FMLLoadCompleteEvent event) {

    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.NPC_ENTITY.get(), ZombieEntity.createAttributes().build());
        event.put(ModEntities.THOMAS.get(), VillagerEntity.createAttributes().build());
        event.put(ModEntities.SINDY, VillagerEntity.createAttributes().build());
    }

    private void registerCommands(RegisterCommandsEvent event) {
        HSCommands.register(event.getDispatcher());
    }
}