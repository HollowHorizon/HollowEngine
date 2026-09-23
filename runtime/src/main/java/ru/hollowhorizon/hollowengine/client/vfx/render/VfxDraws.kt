package ru.hollowhorizon.hollowengine.client.vfx.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderContext
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix4f
import ru.hollowhorizon.hollowengine.client.vfx.VfxEmitter
import ru.hollowhorizon.hollowengine.client.vfx.VfxInstance
import ru.hollowhorizon.hollowengine.common.vfx.VfxMeshEmitterSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxQuadEmitterSpec

/**
 * Where the particles are looked at from.
 *
 * Everything the quad renderer places is expressed in one space, the one [modelView] starts from:
 * camera-relative world space in the world, effect space in the editor preview. The camera axes and
 * position are given in that same space, which is all a billboard needs to face the viewer.
 */
class VfxView(
    val modelView: Matrix4f,
    val projection: Matrix4f,
    val right: Vector3f,
    val up: Vector3f,
    val eye: Vector3f,
) {
    companion object {
        fun ofCamera(camera: Camera, modelView: Matrix4f, projection: Matrix4f) = VfxView(
            modelView = Matrix4f(modelView),
            projection = Matrix4f(projection),
            right = Vector3f(camera.leftVector).negate(),
            up = Vector3f(camera.upVector),
            eye = Vector3f(),
        )
    }
}

/** One emitter worth of billboard particles, ready to draw. */
class VfxQuadDraw(
    val emitter: VfxEmitter,
    /** Simulation space to the space of the [VfxView] it is drawn with. */
    val matrix: Matrix4f,
)

/**
 * Walks playing effects and turns them into draw work.
 */
object VfxDrawCollector {
    /**
     * Adds the billboard emitters of [instance] to [into].
     *
     * [placement] maps effect space to the space of the view the batch is drawn with, which in the
     * world is camera-relative world space.
     */
    fun collectQuads(instance: VfxInstance, placement: Matrix4f, into: MutableList<VfxQuadDraw>) {
        instance.emitters.forEach { emitter ->
            if (emitter.spec !is VfxQuadEmitterSpec) return@forEach
            if (emitter.particles.count == 0 || !emitter.node.isActive) return@forEach

            into += VfxQuadDraw(emitter, Matrix4f(placement).mul(emitter.simToRender.asMatrix4f()))
        }
    }

    /**
     * Draws the model particles of [instance].
     *
     * Each particle is submitted as one instance of the model, so the model renderer batches them
     * the same way it batches entities. Two things a quad can do that a model particle cannot yet:
     * carry its own color, and use a skinned model - the instance stream has no room for either.
     */
    fun renderMeshes(instance: VfxInstance, placement: Matrix4f) {
        val source = Minecraft.getInstance().renderBuffers().bufferSource()

        val stack = PoseStack()
        stack.mulPose(placement)

        instance.emitters.forEach { emitter ->
            val spec = emitter.spec as? VfxMeshEmitterSpec ?: return@forEach
            if (emitter.particles.count == 0 || !emitter.node.isActive) return@forEach

            val attachment = emitter.node.meshModel ?: return@forEach
            attachment.ensureReady()
            attachment.beginPose()
            attachment.endPose()

            val particles = emitter.particles
            val simToRender = emitter.simToRender.asMatrix4f()

            for (slot in 0 until particles.count) {
                stack.pushPose()
                stack.mulPose(simToRender)
                stack.translate(particles.positionX[slot], particles.positionY[slot], particles.positionZ[slot])

                if (spec.alignToVelocity) {
                    stack.mulPose(
                        facing(
                            particles.velocityX[slot],
                            particles.velocityY[slot],
                            particles.velocityZ[slot],
                        )
                    )
                }
                stack.mulPose(
                    Quaternionf().rotateZYX(
                        particles.rotationZ[slot] * Mth.DEG_TO_RAD,
                        particles.rotationY[slot] * Mth.DEG_TO_RAD,
                        particles.rotationX[slot] * Mth.DEG_TO_RAD,
                    )
                )
                stack.scale(particles.sizeX[slot], particles.sizeY[slot], particles.sizeZ[slot])

                attachment.pipeline.render(
                    RenderContext(
                        stack = stack,
                        source = source,
                        light = if (spec.emissive) LightTexture.FULL_BRIGHT else particles.light[slot],
                        overlay = OverlayTexture.NO_OVERLAY,
                        allowInstancing = true,
                    )
                )
                stack.popPose()
            }
        }
    }

    /** Turns a velocity into a rotation that points the model along it. */
    private fun facing(x: Float, y: Float, z: Float): Quaternionf {
        val lengthSquared = x * x + y * y + z * z
        if (lengthSquared < 1.0e-6f) return Quaternionf()

        val yaw = Mth.atan2(x.toDouble(), z.toDouble()).toFloat()
        val pitch = Mth.atan2(y.toDouble(), Mth.sqrt(x * x + z * z).toDouble()).toFloat()
        return Quaternionf().rotateY(yaw).rotateX(-pitch)
    }
}
