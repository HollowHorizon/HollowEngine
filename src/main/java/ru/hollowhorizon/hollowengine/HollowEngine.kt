package ru.hollowhorizon.hollowengine

import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.world.entity.monster.Zombie
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AddPackFindersEvent
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.loading.FMLEnvironment
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.api.registy.HollowMod
import ru.hollowhorizon.hc.client.render.entity.GLTFEntityRenderer
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.registry.HollowModProcessor
import ru.hollowhorizon.hc.common.registry.RegistryLoader
import ru.hollowhorizon.hollowengine.client.ClientEvents
import ru.hollowhorizon.hollowengine.client.ClientEvents.initKeys
import ru.hollowhorizon.hollowengine.common.commands.HECommands
import ru.hollowhorizon.hollowengine.common.data.HollowStoryPack
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.getModScripts
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings
import ru.hollowhorizon.hollowengine.common.npcs.NPCStorage
import ru.hollowhorizon.hollowengine.common.recipes.RecipeReloadListener
import ru.hollowhorizon.hollowengine.common.registry.ModDimensions
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.scripting.mod.runModScript

@HollowMod(HollowEngine.MODID)
@Mod(HollowEngine.MODID)
class HollowEngine {
    init {
        HollowModProcessor.initMod()
        getModScripts().forEach(::runModScript)
        val forgeBus = MinecraftForge.EVENT_BUS
        val modBus = FMLJavaModLoadingContext.get().modEventBus
        HollowCore.LOGGER.info("HollowEngine mod loading...")
        forgeBus.addListener { event: RegisterCommandsEvent -> registerCommands(event) }
        forgeBus.addListener(this::addReloadListenerEvent)
        forgeBus.addListener(StoryHandler::onPlayerJoin)
        forgeBus.addListener(StoryHandler::onPlayerTick)
        forgeBus.addListener(StoryHandler::onPlayerClone)
        modBus.addListener(this::setup)
        modBus.addListener(this::onAttributeCreation)
        modBus.addListener(this::onLoadingComplete)
        if (FMLEnvironment.dist.isClient) {
            forgeBus.addListener(ClientEvents::renderOverlay)
            forgeBus.addListener(ClientEvents::onKeyPressed)
            forgeBus.addListener(ClientEvents::onClicked)
            modBus.addListener { event: FMLClientSetupEvent -> clientInit(event) }
        }

        modBus.addListener(this::entityRenderers)
        modBus.addListener(this::registerPacks)
        ModDimensions.CHUNK_GENERATORS.register(modBus)
        ModDimensions.DIMENSIONS.register(modBus)
        RegistryLoader.registerAll()
    }

    fun registerPacks(event: AddPackFindersEvent) {
        event.addRepositorySource { adder, creator ->
            adder.accept(
                creator.create(
                    HollowStoryPack.name, HollowStoryPack.name.mcText, true, { HollowStoryPack },
                    PackMetadataSection(TranslatableComponent("fml.resources.modresources"), 9),
                    Pack.Position.TOP, PackSource.BUILT_IN, false
                )
            )
        }
    }

    private fun addReloadListenerEvent(event: AddReloadListenerEvent) {
        event.addListener(RecipeReloadListener)
        RecipeReloadListener.resources = event.serverResources
    }

    fun entityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(ModEntities.NPC_ENTITY.get(), ::GLTFEntityRenderer)
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
        event.put(ModEntities.NPC_ENTITY.get(), Zombie.createAttributes().build())
    }

    private fun registerCommands(event: RegisterCommandsEvent) {
        HECommands.register(event.dispatcher)
    }

    companion object {
        const val MODID = "hollowengine"
    }
}