package ru.hollowhorizon.hollowengine.client.models.internal.animator

import de.fabmax.kool.math.QuatF
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import ru.hollowhorizon.hollowengine.client.models.internal.animations.Animation
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.client.models.internal.v2.walk
import ru.hollowhorizon.hollowengine.common.geary.components.*

class AnimatorRuntime {
    private val evaluator = AnimationExpressionEvaluator()
    private val layerStates = linkedMapOf<String, LayerRuntimeState>()
    private var bakedForNodes: List<RuntimeNode>? = null
    private val bakedMasks = linkedMapOf<BoneMask, Set<Int>>()

    fun apply(
        animator: AnimatorComponent,
        rootNodes: List<RuntimeNode>,
        animations: Map<String, Animation>,
        context: AnimatorEvaluationContext,
    ) {
        if (!animator.enabled || animator.layers.isEmpty()) {
            retainLayerStates(emptySet())
            return
        }

        ensureMaskCache(rootNodes)
        retainLayerStates(animator.layers.map { it.id }.toSet())
        val runtimeNodes = rootNodes.flatMap { it.walk() }.associateBy { it.definition.index }

        animator.layers
            .asSequence()
            .sortedBy { it.priority }
            .forEach { layer ->
                val state = layerStates.getOrPut(layer.id) { LayerRuntimeState() }
                val layerContext = context.with(
                    "layer_time" to state.time,
                    "layer_weight" to evaluator.float(layer.weight, context, 1f),
                )
                val weight = evaluator.float(layer.weight, layerContext, 1f).coerceIn(0f, 1f)
                if (weight <= 0f || state.ended) return@forEach

                val allowedNodes = bakedMask(layer.mask, rootNodes)
                val pose = when (layer) {
                    is ClipAnimationLayerSpec -> sampleClipLayer(layer, state, animations, allowedNodes, layerContext)
                    is AnimationControllerLayerSpec -> sampleControllerLayer(
                        layer,
                        state,
                        animations,
                        allowedNodes,
                        layerContext
                    )

                    is ProceduralLayerSpec -> sampleProceduralLayer(layer, rootNodes, allowedNodes, layerContext)
                } ?: return@forEach

                val reference = (layer as? ClipAnimationLayerSpec)
                    ?.referencePose
                    ?.let(animations::get)
                    ?.let { AnimationPose.sample(it, 0f, allowedNodes) }

                applyAnimationPose(pose, runtimeNodes, layer.blendMode, weight, reference)
            }
    }

    fun stateFor(layerId: String): LayerRuntimeState? = layerStates[layerId]

    private fun sampleClipLayer(
        layer: ClipAnimationLayerSpec,
        state: LayerRuntimeState,
        animations: Map<String, Animation>,
        allowedNodes: Set<Int>,
        context: AnimatorEvaluationContext,
    ): AnimationPose? {
        val animation = animations[layer.animation] ?: return null
        val speed = evaluator.float(layer.speed, context, 1f)
        val sampleTime = state.advance(animation.duration, layer.playMode, speed, context.deltaTime)
        if (state.ended && layer.removeOnEnd) return null
        return AnimationPose.sample(animation, sampleTime, allowedNodes)
    }

    private fun sampleControllerLayer(
        layer: AnimationControllerLayerSpec,
        state: LayerRuntimeState,
        animations: Map<String, Animation>,
        allowedNodes: Set<Int>,
        context: AnimatorEvaluationContext,
    ): AnimationPose? {
        if (layer.states.isEmpty()) return null
        if (state.currentState == null) state.currentState = layer.entryState ?: layer.states.first().id
        val current = layer.states.firstOrNull { it.id == state.currentState } ?: layer.states.first()

        val selected = selectTransition(layer, current, state, context)
        if (selected != null && state.transition == null) {
            val duration = evaluator.float(selected.duration, context, 0f).coerceAtLeast(0f)
            state.transition = ControllerTransitionRuntime(
                from = current.id,
                to = selected.to,
                elapsed = 0f,
                duration = duration,
            )
            state.stateTimes.getOrPut(selected.to) { 0f }
        }

        state.transition?.let { transition ->
            transition.elapsed += context.deltaTime
            val factor =
                if (transition.duration <= 0f) 1f else (transition.elapsed / transition.duration).coerceIn(0f, 1f)
            val from = layer.states.firstOrNull { it.id == transition.from } ?: current
            val to = layer.states.firstOrNull { it.id == transition.to } ?: current
            val fromPose = sampleControllerState(from, state, animations, allowedNodes, context)
            val toPose = sampleControllerState(to, state, animations, allowedNodes, context)

            if (factor >= 1f) {
                state.currentState = to.id
                state.transition = null
            }

            return AnimationPose.mix(fromPose, toPose, factor)
        }

        return sampleControllerState(current, state, animations, allowedNodes, context)
    }

    private fun sampleControllerState(
        spec: AnimationControllerStateSpec,
        state: LayerRuntimeState,
        animations: Map<String, Animation>,
        allowedNodes: Set<Int>,
        context: AnimatorEvaluationContext,
    ): AnimationPose {
        val animation = animations[spec.animation] ?: return AnimationPose()
        val speed = evaluator.float(spec.speed, context.with("state_time" to (state.stateTimes[spec.id] ?: 0f)), 1f)
        val time = state.advanceState(spec.id, animation.duration, spec.playMode, speed, context.deltaTime)
        return AnimationPose.sample(animation, time, allowedNodes)
    }

    private fun selectTransition(
        layer: AnimationControllerLayerSpec,
        current: AnimationControllerStateSpec,
        state: LayerRuntimeState,
        context: AnimatorEvaluationContext,
    ): AnimationControllerTransitionSpec? {
        if (state.transition != null) return null
        val currentTime = state.stateTimes[current.id] ?: 0f

        val selected = layer.transitions
            .asSequence()
            .filter { it.from == current.id || it.from == ANY_STATE }
            .filter { transition ->
                val exitTime = transition.exitTime
                exitTime == null || currentTime >= exitTime
            }
            .filter { evaluator.boolean(it.condition, context.with("state_time" to currentTime), false) }
            .sortedWith(compareByDescending<AnimationControllerTransitionSpec> { it.priority }.thenBy { it.to })
            .firstOrNull()

        return selected?.takeIf { it.to != current.id }
    }

    private fun sampleProceduralLayer(
        layer: ProceduralLayerSpec,
        rootNodes: List<RuntimeNode>,
        allowedNodes: Set<Int>,
        context: AnimatorEvaluationContext,
    ): AnimationPose {
        val nodesByName = rootNodes
            .flatMap { it.walk() }
            .flatMap { node -> listOfNotNull(node.name to node, node.definition.path to node) }
            .toMap()
        val pose = AnimationPose()

        layer.transforms.forEach { transform ->
            val node = nodesByName[transform.bone] ?: return@forEach
            if (node.definition.index !in allowedNodes) return@forEach
            val bone = pose.bone(node.definition.index)
            transform.translation?.let { bone.translation = evaluator.vector(it, context) }
            transform.rotation?.let { rotation ->
                val euler = evaluator.vector(rotation, context)
                bone.rotation = QuatF(euler.z.deg, Vec3f.Z_AXIS) *
                        QuatF(euler.y.deg, Vec3f.Y_AXIS) *
                        QuatF(euler.x.deg, Vec3f.X_AXIS)

            }
            transform.scale?.let { bone.scale = evaluator.vector(it, context) }
        }

        return pose
    }

    private fun retainLayerStates(layerIds: Set<String>) {
        layerStates.keys.removeIf { it !in layerIds }
    }

    private fun ensureMaskCache(rootNodes: List<RuntimeNode>) {
        if (bakedForNodes === rootNodes) return
        bakedForNodes = rootNodes
        bakedMasks.clear()
    }

    private fun bakedMask(mask: BoneMask, rootNodes: List<RuntimeNode>): Set<Int> =
        bakedMasks.getOrPut(mask) {
            val all = rootNodes.flatMap { it.walk() }
            all.asSequence()
                .filter { node ->
                    val name = node.name
                    val path = node.definition.path
                    (mask.includes.isEmpty() || mask.includes.any { it == name || path.endsWith(it) }) &&
                            mask.excludes.none { it == name || path.endsWith(it) }
                }
                .map { it.definition.index }
                .toSet()
        }
}

class LayerRuntimeState {
    var time = 0f
    var reversed = false
    var ended = false
    var currentState: String? = null
    var transition: ControllerTransitionRuntime? = null
    val stateTimes = linkedMapOf<String, Float>()
    private val stateReversed = linkedMapOf<String, Boolean>()

    fun advance(duration: Float, playMode: AnimationPlayMode, speed: Float, deltaTime: Float): Float {
        if (duration <= 0f) return 0f
        if (ended && playMode == AnimationPlayMode.Once) return duration
        time += speed * deltaTime * if (reversed) -1f else 1f
        val result = wrapTime(time, duration, playMode, reversed)
        time = result.time
        reversed = result.reversed
        ended = result.ended
        return result.sampleTime
    }

    fun advanceState(
        state: String,
        duration: Float,
        playMode: AnimationPlayMode,
        speed: Float,
        deltaTime: Float,
    ): Float {
        if (duration <= 0f) return 0f
        val previousTime = stateTimes[state] ?: 0f
        val previousReversed = stateReversed[state] ?: false
        val rawTime = previousTime + speed * deltaTime * if (previousReversed) -1f else 1f
        val result = wrapTime(rawTime, duration, playMode, previousReversed)
        stateTimes[state] = result.time
        stateReversed[state] = result.reversed
        return result.sampleTime
    }
}

data class ControllerTransitionRuntime(
    val from: String,
    val to: String,
    var elapsed: Float,
    val duration: Float,
)

private data class WrappedTime(
    val time: Float,
    val sampleTime: Float,
    val reversed: Boolean,
    val ended: Boolean,
)

private fun wrapTime(time: Float, duration: Float, playMode: AnimationPlayMode, reversed: Boolean): WrappedTime =
    when (playMode) {
        AnimationPlayMode.Once -> {
            val clamped = time.coerceIn(0f, duration)
            WrappedTime(clamped, clamped, reversed = false, ended = time >= duration)
        }

        AnimationPlayMode.Loop -> {
            val wrapped = time.modPositive(duration)
            WrappedTime(wrapped, wrapped, reversed = false, ended = false)
        }

        AnimationPlayMode.ClampForever -> {
            val clamped = time.coerceIn(0f, duration)
            WrappedTime(clamped, clamped, reversed = false, ended = false)
        }

        AnimationPlayMode.PingPong -> {
            var nextTime = time
            var nextReversed = reversed
            while (nextTime > duration || nextTime < 0f) {
                if (nextTime > duration) {
                    nextTime = duration - (nextTime - duration)
                    nextReversed = !nextReversed
                } else if (nextTime < 0f) {
                    nextTime = -nextTime
                    nextReversed = !nextReversed
                }
            }
            WrappedTime(nextTime, nextTime, nextReversed, ended = false)
        }
    }

private fun Float.modPositive(divisor: Float): Float =
    ((this % divisor) + divisor) % divisor
