/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.events

import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraftforge.client.event.EntityRenderersEvent
import net.minecraftforge.common.ForgeMod
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import ru.hollowhorizon.hc.client.utils.isPhysicalClient
import ru.hollowhorizon.hollowengine.client.render.AimMarkRenderer
import ru.hollowhorizon.hollowengine.client.render.entity.NPCRenderer
import ru.hollowhorizon.hollowengine.client.render.entity.SeatRenderer
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
        forgeBus.addListener(StoryHandler::onServerStart)
        forgeBus.addListener(StoryHandler::onScriptError)
        forgeBus.addListener(StoryHandler::onScriptStarted)
        if(isPhysicalClient) forgeBus.register(AimMarkRenderer)
        MOD_BUS.addListener(::onAttributeCreation)
        MOD_BUS.addListener(this::entityRenderers)

        ModDimensions.CHUNK_GENERATORS.register(MOD_BUS)
        ModDimensions.DIMENSIONS.register(MOD_BUS)

        ModUtil.updateModNames()
    }

    private fun entityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerEntityRenderer(ModEntities.NPC_ENTITY.get(), ::NPCRenderer)

        event.registerEntityRenderer(ModEntities.SEAT.get(), ::SeatRenderer)
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