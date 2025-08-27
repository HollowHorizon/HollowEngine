package ru.hollowhorizon.hollowengine.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.common.utils.nbt.ListOrSingle

@Serializable
data class GltfAnimation(
    val channels: ListOrSingle<Channel>,
    val samplers: ListOrSingle<Sampler>,
    val name: String? = null,
    val speed: Float = 1f
) {

    @Serializable
    data class Channel(
        val sampler: Int,
        val target: Target,
    ) {
        @Transient
        lateinit var samplerRef: Sampler
    }

    @Serializable
    data class Target(
        val node: Int = -1,
        val path: String,
    ) {
        @Transient
        var nodeRef: GltfNode? = null

        companion object {
            const val PATH_TRANSLATION = "translation"
            const val PATH_ROTATION = "rotation"
            const val PATH_SCALE = "scale"
            const val PATH_WEIGHTS = "weights"
        }
    }

    @Serializable
    data class Sampler(
        val input: Int,
        val interpolation: String = INTERPOLATION_LINEAR,
        val output: Int,
    ) {
        @Transient
        lateinit var inputAccessorRef: GltfAccessor

        @Transient
        lateinit var outputAccessorRef: GltfAccessor

        companion object {
            const val INTERPOLATION_LINEAR = "LINEAR"
            const val INTERPOLATION_STEP = "STEP"
            const val INTERPOLATION_CUBICSPLINE = "CUBICSPLINE"
        }
    }
}