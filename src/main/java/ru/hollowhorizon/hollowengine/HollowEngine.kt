package ru.hollowhorizon.hollowengine

import net.minecraft.network.chat.TranslatableComponent
import net.minecraft.server.packs.metadata.pack.PackMetadataSection
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AddPackFindersEvent
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.RegisterCommandsEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent
import net.minecraftforge.fml.loading.FMLEnvironment
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.api.registy.HollowMod
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.registry.HollowModProcessor
import ru.hollowhorizon.hc.common.registry.RegistryLoader
import ru.hollowhorizon.hollowengine.client.ClientEvents
import ru.hollowhorizon.hollowengine.client.ClientEvents.initKeys
import ru.hollowhorizon.hollowengine.common.commands.HECommands
import ru.hollowhorizon.hollowengine.common.commands.HEStoryCommands
import ru.hollowhorizon.hollowengine.common.data.HollowStoryPack
import ru.hollowhorizon.hollowengine.common.events.StoryEngineSetup
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.getModScripts
import ru.hollowhorizon.hollowengine.common.recipes.RecipeReloadListener
import ru.hollowhorizon.hollowengine.common.scripting.mod.runModScript
import thedarkcolour.kotlinforforge.forge.MOD_BUS

@HollowMod(HollowEngine.MODID)
@Mod(HollowEngine.MODID)
class HollowEngine {
    init {
        HollowModProcessor.initMod()
        getModScripts().forEach(::runModScript)
        val forgeBus = MinecraftForge.EVENT_BUS
        HollowCore.LOGGER.info("HollowEngine mod loading...")
        forgeBus.addListener(::registerCommands)
        forgeBus.addListener(this::addReloadListenerEvent)
        MOD_BUS.addListener(::setup)
        MOD_BUS.addListener(::onLoadingComplete)

        if (FMLEnvironment.dist.isClient) {
            forgeBus.addListener(ClientEvents::renderOverlay)
            forgeBus.addListener(ClientEvents::onKeyPressed)
            forgeBus.addListener(ClientEvents::onClicked)
            forgeBus.addListener(ClientEvents::onTooltipRender)
            MOD_BUS.addListener(::clientInit)
        }

        if (FTB_INSTALLED) {
            StoryEngineSetup.init()
        }

        MOD_BUS.addListener(this::registerPacks)

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

    @OnlyIn(Dist.CLIENT)
    private fun clientInit(event: FMLClientSetupEvent) {
        initKeys()
    }

    private fun setup(event: FMLCommonSetupEvent) {
        DirectoryManager.init()
    }

    private fun onLoadingComplete(event: FMLLoadCompleteEvent) {}

    private fun registerCommands(event: RegisterCommandsEvent) {
        HECommands.register(event.dispatcher)

        if (FTB_INSTALLED)
            HEStoryCommands.register(event.dispatcher)
    }

    companion object {
        const val MODID = "hollowengine"
        val FTB_INSTALLED = ModList.get().isLoaded("ftbteams")
    }
}