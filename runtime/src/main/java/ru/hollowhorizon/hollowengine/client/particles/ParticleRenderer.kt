package ru.hollowhorizon.hollowengine.client.particles

import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.api.system
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderLevelStageEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderStage
import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f

/** Ticks and draws the Bedrock particle system. */
@ClientOnly
object ParticleRenderer {
    @SubscribeEvent
    fun onRenderParticles(event: RenderLevelStageEvent) {
        if (event.stage != RenderStage.AFTER_PARTICLES) return

        val level = Minecraft.getInstance().level ?: return
        val system = level.system
        if (system.isEmpty()) return

        system.update()
        if (!system.hasAnythingToRender()) return

        val camera = event.camera
        val position = camera.position
        val rotation = camera.rotation()

        system.render(
            event.poseStack,
            Vec3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat()),
            QuatF(rotation.x(), rotation.y(), rotation.z(), rotation.w()),
            ParticleVertexConsumerProvider,
            camera.entity.uuid,
            Minecraft.getInstance().options.cameraType == CameraType.FIRST_PERSON,
        )
    }
}
