package ru.hollowhorizon.hollowengine.client.models.internal.animations

import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler.partialTick
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.Node
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.models.internal.controller.calculateSpeedViaDeltaMovement
import ru.hollowhorizon.hollowengine.client.models.internal.controller.isMoving
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.client.utils.math.xz
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.coroutines.CoroutineContext

open class ModelInstance(private val original: AnimatedModel) {
    constructor(location: String): this(HollowModelManager.getOrCreate(location.rl))

    fun reset() {
        original.nodes.forEach { node -> node.transform.set(node.baseTransform) }
    }

    fun update(dt: Float) {
        animations.asSequence()
            .filter { it.duration >= 0 }
            .filter { it.enabled }
            .sortedBy { it.priority }
            .forEach {
                it.update(dt)
            }
    }

    val animations = Animations()
    val nodes = Nodes()

    inner class Animations : Iterable<AnimationInstance> {
        private val animations = original.animations.mapValues { AnimationInstance(it.value) }

        override fun iterator() = animations.values.iterator()

        operator fun get(name: String) = animations[name] ?: error("Unknown animation $name")
    }

    inner class Nodes : Iterable<NodeInstance> {
        private val nodes = original.nodes.associateBy { it.fullName }.mapValues { NodeInstance(it.value) }

        override fun iterator() = nodes.values.iterator()

        operator fun get(name: String) = nodes[name] ?: error("Unknown bone $name")
    }

}

open class NodeInstance(val node: Node) {
    val transform get() = node.transform
    var isVisible: Boolean
        get() = node.isVisible
        set(value) {
            node.isVisible = value
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
        time += if(state == State.PLAYING) speed * dt else dt
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
                    if(blendTime.output != 0f) state = State.ENDING
                    else _enabled = false
                }
            }

            WrapMode.Loop -> {
                if (time >= animation.duration) time -= animation.duration
                else if(time < 0) time += animation.duration
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

class Animator(
    model: AnimatedModel,
    val entity: LivingEntity,
) : CoroutineScope {
    val model = ModelInstance(model)
    override val coroutineContext: CoroutineContext = SupervisorJob(null) + Minecraft.getInstance().dispatcher + Job()

    fun reset() {
        model.reset()
    }

    fun update(dt: Float) {
        model.update(dt)
    }

    fun NodeInstance.child(name: String) = model.nodes["${node.fullName}.$name"]

}

fun Animator.configure() {
    model.animations.forEach { animation ->
        animation.blendTime(input = 0.15f, output = 0.15f)
        animation.blendCurve = Interpolation.SINE_IN
        animation.wrapMode = WrapMode.Loop
    }
}

fun Animator.onUpdate() {
    val head = model.nodes["Model.Body.BodyUp.Head"]
    val leftEye = head.child("GolovaAnimated.Face.Eyes.LeftEye.LeftLib")
    val rightEye = head.child("GolovaAnimated.Face.Eyes.RightEye.RightLib")

    val localHeadRotY = ((entity.yBodyRot - entity.yHeadRot).coerceIn(-30f, 30f) / 30f + 1f) / 2f * 0.05f

    val isCrouching = entity.isCrouching
    val isWalking = entity.isMoving()
    val isSprinting = entity.isSprinting

    model.animations["idle"].enabled = !isWalking && !isCrouching
    model.animations["walk"].enabled = isWalking && !isCrouching && !isSprinting
    model.animations["walk"].speed = calculateSpeedViaDeltaMovement(entity) * 0.75f
    model.animations["run"].enabled = isSprinting && !isCrouching
    model.animations["run"].speed = calculateSpeedViaDeltaMovement(entity) * 0.75f

    head.transform.rotate(entity.headRotation)

    leftEye.transform.translate(-localHeadRotY, 0f, 0f)
    rightEye.transform.translate(-localHeadRotY + 0.05f, 0f, 0f)
}