package ru.hollowhorizon.hollowengine.client.kool.minecraft

import de.fabmax.kool.ViewData
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.scene.PerspectiveCamera
import de.fabmax.kool.scene.Scene
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import kotlin.math.abs
import kotlin.math.min

class MinecraftCamera : PerspectiveCamera() {
    override fun updateProjectionMatrix(viewData: ViewData) {
        super.updateProjectionMatrix(viewData)

        if (Minecraft.getInstance().player == null || Minecraft.getInstance().level == null) return

        bobHurt(proj, TickHandler.partialTick)
        if (Minecraft.getInstance().options.bobView().get()) {
            bobView(proj, TickHandler.partialTick)
        }
    }
}

fun Scene.mcCamera() {
    val camera = MinecraftCamera()
    mainRenderPass.defaultView.camera = camera

    onUpdate {
        camera.syncFromMinecraft()
    }
}

fun MinecraftCamera.syncFromMinecraft() {
    val minecraft = Minecraft.getInstance()
    if (minecraft.player == null || minecraft.level == null) return

    val gameRenderer = minecraft.gameRenderer
    clipNear = 0.05f
    clipFar = gameRenderer.depthFar
    fovY = gameRenderer.getFov(gameRenderer.mainCamera, TickHandler.partialTick, true).toFloat().deg

    val pos = gameRenderer.mainCamera.position
    position.set(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())

    val look = gameRenderer.mainCamera.lookVector
    lookAt.set(
        (pos.x + look.x).toFloat(),
        (pos.y + look.y).toFloat(),
        (pos.z + look.z).toFloat()
    )

    val upVec = gameRenderer.mainCamera.upVector
    up.set(upVec.x, upVec.y, upVec.z)
}

private fun bobHurt(mat4f: MutableMat4f, partialTicks: Float) {
    val minecraft = Minecraft.getInstance()
    if (minecraft.getCameraEntity() is LivingEntity) {
        val livingEntity = minecraft.getCameraEntity() as LivingEntity
        var f = livingEntity.hurtTime.toFloat() - partialTicks
        var g: Float
        if (livingEntity.isDeadOrDying) {
            g = min((livingEntity.deathTime.toFloat() + partialTicks).toDouble(), 20.0).toFloat()
            mat4f.rotate(QuatF.rotation((40.0f - 8000.0f / (g + 200.0f)).deg, Vec3f.Z_AXIS))
        }

        if (f < 0.0f) {
            return
        }

        f /= livingEntity.hurtDuration.toFloat()
        f = Mth.sin(f * f * f * f * 3.1415927f)
        g = livingEntity.hurtDir
        mat4f.rotate(QuatF((-g).deg, Vec3f.Y_AXIS))
        val h = ((-f).toDouble() * 14.0 * minecraft.options.damageTiltStrength().get() as Double).toFloat()
        mat4f.rotate(QuatF(h.deg, Vec3f.Z_AXIS))
        mat4f.rotate(QuatF(g.deg, Vec3f.Y_AXIS))
    }
}

private fun bobView(mat4f: MutableMat4f, partialTicks: Float) {
    val minecraft = Minecraft.getInstance()
    if (minecraft.getCameraEntity() is Player) {
        val player = minecraft.getCameraEntity() as Player
        val f = player.walkDist - player.walkDistO
        val g = -(player.walkDist + f * partialTicks)
        val h = Mth.lerp(partialTicks, player.oBob, player.bob)
        mat4f.translate(
            (Mth.sin(g * 3.1415927f) * h * 0.5f),
            -abs((Mth.cos(g * 3.1415927f) * h)),
            0.0f
        )
        mat4f.rotate(QuatF((Mth.sin(g * 3.1415927f) * h * 3.0f).deg, Vec3f.Z_AXIS))
        mat4f.rotate(QuatF((abs(Mth.cos(g * 3.1415927f - 0.2f) * h) * 5.0f).deg, Vec3f.X_AXIS))
    }

    val f = minecraft.options.screenEffectScale().get().toFloat()
    val player = minecraft.player!!
    val g = Mth.lerp(
        partialTicks,
        player.oSpinningEffectIntensity,
        player.spinningEffectIntensity
    ) * f * f
    if (g > 0.0f) {
        val i = if (player.hasEffect(MobEffects.CONFUSION)) 7 else 20
        var h = 5.0f / (g * g + 5.0f) - g * 0.04f
        h *= h
        val axis = Vec3f(0.0f, Mth.SQRT_OF_TWO / 2.0f, Mth.SQRT_OF_TWO / 2.0f)
        mat4f.rotate(((player.tickCount + partialTicks) * i.toFloat()).deg, axis)
        mat4f.scale(Vec3f(1.0f / h, 1.0f, 1.0f))
        val j: Float = -(player.tickCount + partialTicks) * i.toFloat()
        mat4f.rotate(j.deg, axis)
    }
}
