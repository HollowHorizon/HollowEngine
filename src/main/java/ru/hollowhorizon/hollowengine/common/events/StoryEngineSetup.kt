package ru.hollowhorizon.hollowengine.common.events

import dev.ftb.mods.ftbteams.event.TeamEvent
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.common.ForgeMod
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import ru.hollowhorizon.hollowengine.client.ClientEvents
import ru.hollowhorizon.hollowengine.client.render.entity.NPCRenderer
import ru.hollowhorizon.hollowengine.common.registry.ModDimensions
import ru.hollowhorizon.hollowengine.common.registry.ModEntities
import ru.hollowhorizon.hollowengine.common.util.ModUtil
import thedarkcolour.kotlinforforge.forge.MOD_BUS

object StoryEngineSetup {
    @JvmStatic
    fun init() {
        val forgeBus = MinecraftForge.EVENT_BUS
        forgeBus.addListener(StoryHandler::onPlayerJoin)
        forgeBus.addListener(StoryHandler::onServerTick)
        forgeBus.addListener(StoryHandler::onServerShutdown)
        forgeBus.addListener(StoryHandler::onWorldSave)
        forgeBus.addListener(StoryHandler::playerSkin)
        MOD_BUS.addListener(::onAttributeCreation)
        MOD_BUS.addListener(this::entityRenderers)

        ModDimensions.CHUNK_GENERATORS.register(MOD_BUS)
        ModDimensions.DIMENSIONS.register(MOD_BUS)

        TeamEvent.LOADED.register(StoryHandler::onTeamLoaded)
        ModUtil.updateModNames()
    }

    private fun entityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(
            ModEntities.NPC_ENTITY.get(),
            ::NPCRenderer
        )
    }

    private fun onAttributeCreation(event: EntityAttributeCreationEvent) {
        event.put(ModEntities.NPC_ENTITY.get(), Mob.createMobAttributes().apply {
            add(Attributes.ATTACK_DAMAGE, 0.2)
            add(Attributes.MOVEMENT_SPEED, 0.2)
            add(Attributes.FOLLOW_RANGE, 128.0)
            add(ForgeMod.STEP_HEIGHT_ADDITION.get(), 1.25)
        }.build())
    }
}