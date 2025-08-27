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

package ru.hollowhorizon.hc.client.render

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.api.ParticlesProvider
import ru.hollowhorizon.hc.client.gui.DebugOverlay
import ru.hollowhorizon.hc.client.kool.KoolManager
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hc.client.particles.ParticleVertexConsumerProvider
import ru.hollowhorizon.hc.client.render.entity.HollowEntityRenderer
import ru.hollowhorizon.hc.client.render.entity.CustomPlayerRenderer
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hc.common.events.client.render.RenderPlayerEvent
import ru.hollowhorizon.hc.common.events.client.render.RenderStage
import ru.hollowhorizon.hc.common.utils.get

object RenderManager {
    fun onInitialize() {
        HollowModelManager.initialize()
        KoolManager
        DebugOverlay.init()
    }

    @SubscribeEvent
    fun onRenderParticles(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_PARTICLES) return

        val level = Minecraft.getInstance().level as? ParticlesProvider ?: return
        val camera = event.camera

        val system = level.system
        if (system.isEmpty()) return

        system.update()

        if (!system.hasAnythingToRender()) return

        val cameraUuid = camera.entity.uuid
        val cameraRotMc = camera.rotation()
        val position = camera.position

        val isFirstPerson = Minecraft.getInstance().options.cameraType == CameraType.FIRST_PERSON
        system.render(
            event.poseStack,
            Vec3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat()),
            QuatF(cameraRotMc.x(), cameraRotMc.y(), cameraRotMc.z(), cameraRotMc.w()),
            ParticleVertexConsumerProvider,
            cameraUuid,
            isFirstPerson
        )
    }

    @SubscribeEvent
    fun onRenderPlayer(event: RenderPlayerEvent) {
        if(event.player[AnimatedEntityCapability::class].model != HollowEntityRenderer.NO_MODEL) {
            event.isCanceled = true
            CustomPlayerRenderer.render(event.player, event.partialTicks, event.poseStack, event.buffer, event.packedLight)
        }
    }
}