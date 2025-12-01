package ru.hollowhorizon.hollowengine.client.models.internal.animations

import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.scene.TrsTransformF
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler.partialTick
import ru.hollowhorizon.hollowengine.client.models.internal.NodeDefinition
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation


val LivingEntity.headRotation: QuatF
    get() {
        val bodyYaw = -Mth.rotLerp(partialTick, yBodyRotO, yBodyRot)
        val headYaw = -Mth.rotLerp(partialTick, yHeadRotO, yHeadRot)
        val netHeadYaw = headYaw - bodyYaw
        val headPitch = -Mth.rotLerp(partialTick, xRotO, xRot)

        return MutableQuatF()
            .rotate(netHeadYaw.deg, Vec3f.Y_AXIS)
            .rotate(headPitch.deg, Vec3f.X_AXIS)
    }
private val NodeDefinition.fullName: String
    get() {
        // Если первая кость не имеет названия, то можно её пропустить
        if (parent == root && parent?.name == null) return (name ?: "unnamed")

        return (parent?.let { it.fullName + "." } ?: "") + (name ?: "unnamed")
    }

class AnimationInstance(private val animation: Animation) {
    private val blendTime = BlendTime(0.2f, 0.2f)
    private var reversed = false
    private var endTime = 0f

    var blendCurve = Interpolation.QUINT_IN
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

    fun update(model: Map<Int, TrsTransformF>, dt: Float) {
        time += if (state == State.PLAYING) speed * dt else dt
        when (state) {
            State.STARTING -> updateStarting(model)
            State.PLAYING -> updatePlaying(model)
            State.ENDING -> updateEnding(model)
        }
    }


    private fun updateStarting(model: Map<Int, TrsTransformF>) {
        val factor = time / blendTime.input
        val weight = blendCurve(factor) * weight
        animation.nodes.forEach { (node, channels) ->
            val transform = model[node] ?: return@forEach

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

    private fun updateEnding(model: Map<Int, TrsTransformF>) {
        val factor = (time / blendTime.output).coerceAtMost(1f)
        val weight = blendCurve(factor) * weight
        animation.nodes.forEach { (node, channels) ->
            val transform = model[node] ?: return@forEach

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


    private fun updatePlaying(model: Map<Int, TrsTransformF>) {
        applyWrapMode()

        val time = if (reversed) duration - time else time

        animation.nodes.forEach { (node, channels) ->
            val transform = model[node] ?: return@forEach

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
