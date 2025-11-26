package ru.hollowhorizon.hollowengine.client.models.internal.animations

import de.fabmax.kool.math.*
import de.fabmax.kool.scene.TrsTransformF
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import org.joml.Quaternionf
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler.partialTick
import ru.hollowhorizon.hollowengine.client.models.internal.Node
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ListRenderPipeline
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix3f
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix4f


abstract class NodeInstance(var parent: NodeInstance? = null, val name: String? = null) {
    val children: MutableList<NodeInstance> = ArrayList<NodeInstance>()
    open val transform: TrsTransformF = TrsTransformF()
    open val globalMatrix = MutableMat4f()

    abstract fun collectCommands(pipeline: RenderPipeline)

    operator fun plusAssign(node: NodeInstance) {
        node.parent = this
        children += node
    }

    operator fun minusAssign(node: NodeInstance) {
        node.parent = null
        children -= node
    }

    fun add(node: NodeInstance) = plusAssign(node)
    fun remove(node: NodeInstance) = minusAssign(node)
}

class ModelNode: NodeInstance() {
    override fun collectCommands(pipeline: RenderPipeline) {}

    val pipeline = ListRenderPipeline()

    fun setup() {
        collectGlobalCommands(pipeline)
    }

    fun clear() {
        pipeline.clear()
    }
}

operator fun NodeInstance.get(path: String): NodeInstance? {
    val parts = path.split(".")
    var current: NodeInstance = this
    for (part in parts) {
        val next = current.children.find { it.name == part } ?: return null
        current = next
    }
    return current
}

fun NodeInstance.collectGlobalCommands(pipeline: RenderPipeline) {
    val parent = this.parent
    if (parent == null) {
        pipeline.addInitializable {
            globalMatrix.set(transform.matrixF)
        }
    } else {
        pipeline.addInitializable {
            globalMatrix.set(parent.globalMatrix)
            globalMatrix.mul(transform.matrixF)
        }
    }
    children.forEach { it.collectGlobalCommands(pipeline) }
    collectCommands(pipeline)
}

open class NodeImpl(parent: NodeInstance? = null, val node: Node) : NodeInstance(parent, node.name) {
    init {
        children += node.children.map { NodeImpl(this, it) }
    }

    override val transform get() = node.transform
    override val globalMatrix get() = node.globalMatrix

    override fun collectCommands(pipeline: RenderPipeline) {
        node.mesh?.primitives?.forEach { it.setupPipeline(node, pipeline) }
    }
}

class ItemNode(val entity: LivingEntity, val slot: EquipmentSlot) : NodeInstance() {
    override fun collectCommands(pipeline: RenderPipeline) {
        pipeline.addBatchedRenderable {
            stack.pushPose()

            stack.mulPoseMatrix(globalMatrix.asMatrix4f())
            stack.last().normal().mul(globalMatrix.getUpperLeft(MutableMat3f()).asMatrix3f())

            stack.mulPose(Quaternionf().rotateX(-90 * Mth.DEG_TO_RAD))

            Minecraft.getInstance().itemRenderer.renderStatic(
                entity,
                entity.getItemBySlot(slot),
                when (slot) {
                    EquipmentSlot.MAINHAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    EquipmentSlot.OFFHAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    EquipmentSlot.HEAD -> ItemDisplayContext.HEAD
                    else -> ItemDisplayContext.FIXED
                },
                slot == EquipmentSlot.OFFHAND,
                stack,
                source,
                entity.level(),
                light, overlay, 0
            )

            stack.popPose()
        }
    }
}

private val LivingEntity.headRotation: QuatF
    get() {
        val bodyYaw = -Mth.rotLerp(partialTick, yBodyRotO, yBodyRot)
        val headYaw = -Mth.rotLerp(partialTick, yHeadRotO, yHeadRot)
        val netHeadYaw = headYaw - bodyYaw
        val headPitch = -Mth.rotLerp(partialTick, xRotO, xRot)

        return MutableQuatF()
            .rotate(netHeadYaw.deg, Vec3f.Y_AXIS)
            .rotate(headPitch.deg, Vec3f.X_AXIS)
    }
private val Node.fullName: String
    get() {
        // Если первая кость не имеет названия, то можно её пропустить
        if (parent == root && parent?.name == null) return (name ?: "unnamed")

        return (parent?.let { it.fullName + "." } ?: "") + (name ?: "unnamed")
    }

class AnimationInstance(private val animation: Animation) {
    private val blendTime = BlendTime(0f, 0f)
    private var reversed = false
    private var endTime = 0f

    var blendCurve = Interpolation.LINEAR
    private var _enabled = false
    var enabled: Boolean
        get() = _enabled
        set(value) {
            if (value && !_enabled) {
                _enabled = true
                state = State.STARTING
                return
            }
            if (!value && _enabled && state == State.PLAYING) {
                endTime = time
                state = State.ENDING
            }
        }
    var wrapMode = WrapMode.Once
    var overrides = Overrides(translation = false, rotation = false, scale = false)
    var priority = 0
    var speed = 1f
    var duration: Float
        set(value) {
            animation.duration = value
        }
        get() = animation.duration

    var time = 0f
    var weight = 1f
    var state = State.STARTING
        set(value) {
            if (field == value) return
            field = value
            time = 0f
        }

    fun blendTime(input: Float = 0f, output: Float = 0f) {
        blendTime.input = input
        blendTime.output = output
    }

    fun update(dt: Float) {
        time += if (state == State.PLAYING) speed * dt else dt
        when (state) {
            State.STARTING -> updateStarting()
            State.PLAYING -> updatePlaying()
            State.ENDING -> updateEnding()
        }
    }


    private fun updateStarting() {
        val factor = time / blendTime.input
        val weight = blendCurve(factor) * weight
        animation.nodes.forEach { (node, channels) ->
            val transform = node.transform

            channels.translation?.let {
                val translation = Vec3f.ZERO.mix(it.compute(0f), weight)
                if (overrides.translation) transform.translation.set(translation)
                else transform.translate(translation)
            }
            channels.rotation?.let {
                val rotation = QuatF.IDENTITY.mix(it.compute(0f), weight)
                if (overrides.rotation) transform.rotation.set(rotation)
                else transform.rotate(rotation)
            }
            channels.scale?.let {
                val scale = Vec3f.ONES.mix(it.compute(0f), weight)
                if (overrides.scale) transform.scale.set(scale)
                else transform.scale(scale)
            }
        }
        if (factor >= 1f || blendTime.input == 0f) {
            state = State.PLAYING
        }
    }

    private fun updateEnding() {
        val factor = (time / blendTime.output).coerceAtMost(1f)
        val weight = blendCurve(factor) * weight
        animation.nodes.forEach { (node, channels) ->
            val transform = node.transform

            channels.translation?.let {
                val translation = it.compute(endTime).mix(Vec3f.ZERO, weight)
                if (overrides.translation) transform.translation.set(translation)
                else transform.translate(translation)
            }
            channels.rotation?.let {
                val rotation = it.compute(endTime).mix(QuatF.IDENTITY, weight)
                if (overrides.rotation) transform.rotation.set(rotation)
                else transform.rotate(rotation)
            }
            channels.scale?.let {
                val scale = it.compute(endTime).mix(Vec3f.ONES, weight)
                if (overrides.scale) transform.scale.set(scale)
                else transform.scale(scale)
            }
        }

        if (factor >= 1f || blendTime.output == 0f) {
            _enabled = false
        }
    }


    private fun updatePlaying() {
        applyWrapMode()

        val time = if (reversed) duration - time else time

        animation.nodes.forEach { (node, channels) ->
            val transform = node.transform

            channels.translation?.let {
                val translation = Vec3f.ZERO.mix(it.compute(time), weight)
                if (overrides.translation) transform.translation.set(translation)
                else transform.translate(translation)
            }
            channels.rotation?.let {
                val rotation = QuatF.IDENTITY.mix(it.compute(time), weight)
                if (overrides.rotation) transform.rotation.set(rotation)
                else transform.rotate(rotation)
            }
            channels.scale?.let {
                val scale = Vec3f.ONES.mix(it.compute(time), weight)
                if (overrides.scale) transform.scale.set(scale)
                else transform.scale(scale)
            }
        }
    }

    private fun applyWrapMode() {
        when (wrapMode) {
            WrapMode.Once -> {
                if (time >= animation.duration) {
                    endTime = time
                    if (blendTime.output != 0f) state = State.ENDING
                    else _enabled = false
                }
            }

            WrapMode.Loop -> {
                if (time >= animation.duration) time -= animation.duration
                else if (time < 0) time += animation.duration
            }

            WrapMode.ClampForever -> if (time >= animation.duration) time = animation.duration
            WrapMode.PingPong -> {
                if (time >= animation.duration) {
                    reversed = !reversed
                    time -= animation.duration
                }

            }
        }
        time = time.coerceIn(0f, animation.duration)
    }

    data class BlendTime(var input: Float, var output: Float)
    data class Overrides(var translation: Boolean, var rotation: Boolean, var scale: Boolean)
    enum class State {
        STARTING, PLAYING, ENDING
    }
}
