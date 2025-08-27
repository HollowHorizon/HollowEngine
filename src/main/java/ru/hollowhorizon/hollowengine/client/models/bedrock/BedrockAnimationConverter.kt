package ru.hollowhorizon.hollowengine.client.models.bedrock

import de.fabmax.kool.math.EulerOrder
import de.fabmax.kool.math.MutableQuatF
import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.rotateByEulers
import ru.hollowhorizon.hollowengine.client.models.internal.Channel
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.Node
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation
import ru.hollowhorizon.hollowengine.client.models.internal.animations.AnimationData
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.Catmullrom
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.CatmullromQuat
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.Linear
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.SphericalLinear
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.MolangContext
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Query
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.VariablesMap

object BedrockAnimationConverter {
    fun convert(model: Model, file: BedrockAnimationFile): List<Animation> {
        return file.animations.mapNotNull { (name, animation) ->
            val animationData = convertAnimation(model, animation)
            if(animationData.isEmpty()) return@mapNotNull null
            Animation(name, animationData, animation.animationLength ?: animationData.values.maxOf { it.duration })
        }
    }

    private fun convertAnimation(model: Model, animation: BedrockAnimationFile.Animation): Map<Node, AnimationData> {
        return animation.bones.entries.associate { (node, channels) ->
            val bone = model.findNodeByName(node) ?: error("Bone $node not found!")

            bone to channels.convert(bone)
        }
    }

    private fun Channels.convert(node: Node): AnimationData {
        val positions = position?.frames?.let {
            val interpolation = it.values.firstOrNull()?.smooth ?: return@let null

            val keys = it.keys.toFloatArray()
            val values = it.values.map { it.post.eval(MolangContext(Query.GLFW_TIME, VariablesMap())) }.toTypedArray()

            return@let when(interpolation) {
                Interpolation.LINEAR -> Linear(keys, values)
                Interpolation.CATMULLROM -> Linear(keys, values)
                else -> error("Unsupported interpolation: ${interpolation.name}")
            }
        }
        val rotations = rotation?.frames?.let {
            val interpolation = it.values.firstOrNull()?.smooth ?: return@let null

            val keys = it.keys.toFloatArray()
            val values: Array<QuatF> = it.values.map {
                val quat = it.post.eval(MolangContext(Query.GLFW_TIME, VariablesMap())).let { MutableQuatF().rotateByEulers(it) }
            quat
            //MutableQuatF(node.baseTransform.rotation).inverted().mul(quat)
            }.toTypedArray()

            return@let when(interpolation) {
                Interpolation.LINEAR -> SphericalLinear(keys, values)
                Interpolation.CATMULLROM -> SphericalLinear(keys, values)
                else -> error("Unsupported interpolation: ${interpolation.name}")
            }
        }
        val scales = scale?.frames?.let {
            val interpolation = it.values.firstOrNull()?.smooth ?: return@let null

            val keys = it.keys.toFloatArray()
            val values = it.values.map { it.post.eval(MolangContext(Query.GLFW_TIME, VariablesMap()))  }.toTypedArray()

            return@let when(interpolation) {
                Interpolation.LINEAR -> Linear(keys, values)
                Interpolation.CATMULLROM -> Linear(keys, values)
                else -> error("Unsupported interpolation: ${interpolation.name}")
            }
        }


        return AnimationData(node, positions, rotations, scales, null)
    }
}