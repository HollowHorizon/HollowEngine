package ru.hollowhorizon.hollowengine.client.models.internal.controller

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.scene.TrsTransformF
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation

class AnimationInstance(private val animation: Animation) {
    val name = animation.name
    private var reversed = false

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
    var weight = 0f


    fun update(model: Map<Int, TrsTransformF>, dt: Float) {
        if (weight == 0f) return

        time += speed * dt
        updatePlaying(model)
    }

    private fun updatePlaying(model: Map<Int, TrsTransformF>) {
        applyWrapMode()

        val time = if (reversed) duration - time else time

        animation.nodes.forEach { (node, channels) ->
            val transform = model[node] ?: return@forEach

            channels.translation?.let {
                val translation = Vec3f.Companion.ZERO.mix(it.compute(time), weight)
                if (overrides.translation) transform.translation.set(translation)
                else transform.translate(translation)
            }
            channels.rotation?.let {
                val rotation = QuatF.Companion.IDENTITY.mix(it.compute(time), weight)
                if (overrides.rotation) transform.rotation.set(rotation)
                else transform.rotate(rotation)
            }
            channels.scale?.let {
                val scale = Vec3f.Companion.ONES.mix(it.compute(time), weight)
                if (overrides.scale) transform.scale.set(scale)
                else transform.scale(scale)
            }
        }
    }

    private fun applyWrapMode() {
        when (wrapMode) {
            WrapMode.Once -> {
                if (time >= animation.duration) {
                    weight = 0f
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

    data class Overrides(var translation: Boolean, var rotation: Boolean, var scale: Boolean)
}