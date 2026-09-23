package ru.hollowhorizon.hollowengine.client.vfx

import net.minecraft.client.Minecraft
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.v2.Attachment
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RigAttachmentFactories
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix4f
import ru.hollowhorizon.hollowengine.common.utils.math.MutableMat4f
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxBoneAttachmentSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxTransform

/**
 * An effect attached to bone.
 */
class VfxBoneAttachment(
    private val spec: VfxBoneAttachmentSpec,
    private val node: RuntimeNode,
) : Attachment(node) {
    private var instance: VfxInstance? = null
    private var lastGameTime = Float.NaN
    private var lastFrame = Long.MIN_VALUE

    /** Bone space to camera-relative space, captured while the model was drawn. */
    private val placement = Matrix4f()

    override fun collectCommands(pipeline: RenderPipeline) {
        if (!spec.autoPlay || spec.effect.isBlank()) return

        pipeline.addBatchedRenderable {
            if (!node.isVisible) return@addBatchedRenderable

            val frame = TickHandler.renderFrame
            if (frame == lastFrame) return@addBatchedRenderable
            lastFrame = frame

            placement.set(stack.last().pose()).mul(globalMatrix.asMatrix4f())
            VfxBoneBindings.submit(this@VfxBoneAttachment)
        }
    }

    /** Advances the effect and says where it is; called once a frame by the world renderer. */
    fun update(cameraPosition: Vec3): VfxBoneBinding? {
        val level = Minecraft.getInstance().level ?: return null
        val playing = instance ?: create(level) ?: return null

        val now = TickHandler.gameTime
        val dt = when {
            lastGameTime.isNaN() -> 0f
            else -> ((now - lastGameTime) / TICKS_PER_SECOND).coerceIn(0f, MAX_STEP_SECONDS)
        }
        lastGameTime = now

        val local = Matrix4f(placement)
        applyOffset(local)

        val translation = local.getTranslation(Vector3f())
        playing.moveTo(
            Vec3(
                cameraPosition.x + translation.x,
                cameraPosition.y + translation.y,
                cameraPosition.z + translation.z,
            )
        )

        playing.partialTick = TickHandler.partialTick
        playing.gameTime = now
        playing.budget = VfxBudget.share(1)
        playing.update(dt)

        return VfxBoneBinding(playing, local)
    }

    /** The offset the author gave the attachment, on top of the bone. */
    private fun applyOffset(matrix: Matrix4f) {
        val transform = VfxTransform(spec.offset, spec.rotation, Vec3f(spec.scale, spec.scale, spec.scale))
        val frame = VfxFrame().setCombined(VfxFrame().setIdentity(), transform)
        matrix.mul(frame.toMatrix(MutableMat4f()).asMatrix4f())
    }

    private fun create(level: Level): VfxInstance? {
        val effect = VfxAssets[spec.effect] ?: return null
        return VfxInstance(effect, spec.effect, VfxWorldEnvironment(level)).also { instance = it }
    }

    private companion object {
        const val TICKS_PER_SECOND = 20f
        const val MAX_STEP_SECONDS = 0.25f
    }
}

/** One bone effect, already advanced, with the matrix that places it in front of the camera. */
class VfxBoneBinding(val instance: VfxInstance, val placement: Matrix4f)

/**
 * The bone effects drawn this frame.
 */
object VfxBoneBindings {
    private val submitted = ArrayList<VfxBoneAttachment>()
    private val bindings = ArrayList<VfxBoneBinding>()

    fun submit(attachment: VfxBoneAttachment) {
        submitted += attachment
    }

    fun drain(cameraPosition: Vec3): List<VfxBoneBinding> {
        bindings.clear()
        submitted.forEach { attachment -> attachment.update(cameraPosition)?.let(bindings::add) }
        submitted.clear()
        return bindings
    }

    fun register() {
        RigAttachmentFactories.register("hollowengine:rig/vfx") { spec, context ->
            VfxBoneAttachment(spec as VfxBoneAttachmentSpec, context.node)
        }
    }
}
