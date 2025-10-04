package ru.hollowhorizon.hollowengine.client.models.internal.animations

import de.fabmax.kool.math.*
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import ru.hollowhorizon.hollowengine.client.models.gltf.*
import ru.hollowhorizon.hollowengine.client.models.internal.Model
import ru.hollowhorizon.hollowengine.client.models.internal.Node
import ru.hollowhorizon.hollowengine.client.models.internal.animations.interpolations.*


object AnimationLoader {

    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun createAnimation(model: Model, animationModel: ru.hollowhorizon.hollowengine.client.models.internal.Animation): Animation {
        val animData = animationModel.channels.map { channel ->
            val node = model.findNodeByIndex(channel.node)
                ?: throw AnimationException("Node with index ${channel.node} not found!")

            val timeKeys = channel.times.toFloatArray()
            val target = AnimationTarget.valueOf(channel.path.uppercase())

            val size = if (target == AnimationTarget.WEIGHTS) node.mesh?.weights?.size ?: 0
            else -1

            return@map Pair(target, readAnimationData(
                node,
                channel.interpolation,
                target,
                channel.values,
                timeKeys,
                size
            ).apply {
                this.node = node
            })
        }

        val data = animData.groupBy { it.second.node }
        val result = Object2ObjectOpenHashMap<Node, AnimationData>()

        data.forEach { (key, values) ->
            result[key] = AnimationData(
                key,
                values.find { it.first == AnimationTarget.TRANSLATION }?.second as? Interpolator<Vec3f>,
                values.find { it.first == AnimationTarget.ROTATION }?.second as? Interpolator<QuatF>,
                values.find { it.first == AnimationTarget.SCALE }?.second as? Interpolator<Vec3f>,
                values.find { it.first == AnimationTarget.WEIGHTS }?.second as? Interpolator<FloatArray>
            )
        }

        return Animation(animationModel.name ?: "Unnamed", result)
    }

    private fun readAnimationData(
        node: Node,
        interpolation: String,
        target: AnimationTarget,
        outputData: GltfAccessor,
        timeKeys: FloatArray,
        componentCount: Int = -1,
    ): Interpolator<*> {
        return when (interpolation) {
            GltfAnimation.Sampler.INTERPOLATION_STEP -> loadStep(node, outputData, timeKeys, target, componentCount)
            GltfAnimation.Sampler.INTERPOLATION_LINEAR -> loadLinear(node, outputData, timeKeys, target, componentCount)
            else -> throw UnsupportedOperationException("Animation type $interpolation not supported yet!")
        }
    }

    private fun loadStep(
        node: Node,
        outputData: GltfAccessor,
        keys: FloatArray,
        target: AnimationTarget,
        componentCount: Int = -1,
    ): Interpolator<*> {
        return when (target) {
            AnimationTarget.TRANSLATION -> Vec3Step(keys, Vec3fAccessor(outputData).list.map { it - node.baseTransform.translation }.toTypedArray())
            AnimationTarget.ROTATION -> QuatStep(keys, Vec4fAccessor(outputData).list.map { MutableQuatF(node.baseTransform.rotation).inverted().mul(it.toQuatF()) }.toTypedArray())
            AnimationTarget.SCALE -> Vec3Step(keys, Vec3fAccessor(outputData).list.map { it / node.baseTransform.scale }.toTypedArray())
            AnimationTarget.WEIGHTS -> LinearSingle(
                keys,
                splitListByN(FloatAccessor(outputData).list.toList(), componentCount).toTypedArray()
            )
        }
    }

    private fun loadLinear(
        node: Node,
        outputData: GltfAccessor,
        keys: FloatArray,
        target: AnimationTarget,
        componentCount: Int = -1,
    ): Interpolator<*> {
        return when (target) {
            AnimationTarget.TRANSLATION -> Linear(keys, Vec3fAccessor(outputData).list.map { it - node.baseTransform.translation }.toTypedArray())
            AnimationTarget.ROTATION -> SphericalLinear(keys, Vec4fAccessor(outputData).list.map { MutableQuatF(node.baseTransform.rotation).inverted().mul(it.toQuatF()) }.toTypedArray())
            AnimationTarget.SCALE -> Linear(keys, Vec3fAccessor(outputData).list.map { it / node.baseTransform.scale }.toTypedArray())
            AnimationTarget.WEIGHTS -> LinearSingle(
                keys,
                splitListByN(FloatAccessor(outputData).list.toList(), componentCount).toTypedArray()
            )
        }
    }
}

fun splitListByN(list: List<Float>, n: Int): List<FloatArray> {
    if (n < 1) return listOf(list.toFloatArray())

    val result = mutableListOf<FloatArray>()
    var startIndex = 0
    while (startIndex < list.size) {
        val endIndex = kotlin.math.min(startIndex + n, list.size)
        val subList = list.subList(startIndex, endIndex).toFloatArray()
        result.add(subList)
        startIndex = endIndex
    }
    return result
}

val Vec4f.asQuaternion get() = QuatF(x, y, z, w)