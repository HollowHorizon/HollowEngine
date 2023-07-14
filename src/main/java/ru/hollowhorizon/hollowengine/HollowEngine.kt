package ru.hollowhorizon.hollowengine

import net.minecraft.entity.monster.ZombieEntity
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.fml.loading.FMLPaths
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.api.registy.HollowMod
import ru.hollowhorizon.hc.client.gltf.GltfModelSources.addSource
import ru.hollowhorizon.hc.client.gltf.PathSource
import ru.hollowhorizon.hollowengine.client.ClientEvents
import ru.hollowhorizon.hollowengine.client.ClientEvents.initKeys
import ru.hollowhorizon.hollowengine.client.sound.HSSounds
import ru.hollowhorizon.hollowengine.common.commands.HECommands
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.getModScripts
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.NPCStorage
import ru.hollowhorizon.hollowengine.common.registry.ModDimensions
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.scripting.mod.runModScript

@HollowMod(HollowEngine.MODID)
@Mod(HollowEngine.MODID)
class HollowEngine {
    init {
        getModScripts().forEach(::runModScript)
        val forgeBus = MinecraftForge.EVENT_BUS
        val modBus = FMLJavaModLoadingContext.get().modEventBus
        HollowCore.LOGGER.info("HollowEngine mod loading...")
        forgeBus.addListener { event: RegisterCommandsEvent -> registerCommands(event) }
        forgeBus.addListener { event: AddReloadListenerEvent? -> addReloadListenerEvent(event) }
        forgeBus.addListener(StoryHandler::onPlayerJoin)
        forgeBus.addListener(StoryHandler::onPlayerTick)
        forgeBus.addListener(StoryHandler::onPlayerClone)
        modBus.addListener { event: FMLCommonSetupEvent -> setup(event) }
        modBus.addListener { event: EntityAttributeCreationEvent -> onAttributeCreation(event) }
        modBus.addListener { event: FMLLoadCompleteEvent -> onLoadingComplete(event) }
        addSource(PathSource(FMLPaths.GAMEDIR.get().resolve("hollowengine")))
        addSource(PathSource(FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve("models")))
        if (FMLEnvironment.dist.isClient) {
            forgeBus.addListener(ClientEvents::renderLast)
            forgeBus.addListener(ClientEvents::onKeyPressed)
            modBus.addListener { event: FMLClientSetupEvent -> clientInit(event) }
            HSSounds.init()
        }

        ModDimensions
    }

    fun addReloadListenerEvent(event: AddReloadListenerEvent?) {
        //event.addListener(new DialogueReloadListener());
    }

    @OnlyIn(Dist.CLIENT)
    private fun clientInit(event: FMLClientSetupEvent) {
        initKeys()
    }

    private fun setup(event: FMLCommonSetupEvent) {
        DirectoryManager.init()
        NPCStorage.addNPC("Монстр", NPCSettings())
    }

    private fun onLoadingComplete(event: FMLLoadCompleteEvent) {}
    private fun onAttributeCreation(event: EntityAttributeCreationEvent) {
        event.put(ModEntities.NPC_ENTITY, ZombieEntity.createAttributes().build())
    }

    private fun registerCommands(event: RegisterCommandsEvent) {
        HECommands.register(event.dispatcher)
    }

    companion object {
        const val MODID = "hollowengine"
    }
}