package ru.hollowhorizon.hollowengine.client.models.internal.animations

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.Vec4f
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.Interpolator

class Animation(
    val name: String,
    val nodes: Map<Int, AnimationData>,
    var duration: Float = nodes.values.maxOf { it.duration },
) {

    override fun toString() = name

}

fun Vec3f.array(): FloatArray {
    return floatArrayOf(x, y, z)
}

fun QuatF.array(): FloatArray {
    return floatArrayOf(x, y, z, w)
}

fun Vec4f.array(): FloatArray {
    return floatArrayOf(x, y, z, w)
}

class AnimationData(
    var translation: Interpolator<Vec3f>?,
    var rotation: Interpolator<QuatF>?,
    var scale: Interpolator<Vec3f>?,
    var weights: Interpolator<FloatArray>?,
) {
    val duration by lazy {
        maxOf(
            translation?.duration ?: 0f,
            rotation?.duration ?: 0f,
            scale?.duration ?: 0f,
            weights?.duration ?: 0f
        )
    }
}

enum class AnimationTarget { TRANSLATION, ROTATION, SCALE, WEIGHTS }