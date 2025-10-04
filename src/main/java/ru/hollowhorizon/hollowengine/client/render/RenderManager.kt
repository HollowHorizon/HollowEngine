package ru.hollowhorizon.hollowengine.client.render

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.api.ParticlesProvider
import ru.hollowhorizon.hollowengine.client.kool.KoolManager
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.ParticleVertexConsumerProvider
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage

object RenderManager {
    fun onInitialize() {
        HollowModelManager.initialize()
        KoolManager
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
}