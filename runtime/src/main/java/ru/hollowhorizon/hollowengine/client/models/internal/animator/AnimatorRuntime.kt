package ru.hollowhorizon.hollowengine.client.models.internal.animator

import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.utils.math.deg
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
                state.advanceAge(context.deltaTime)
                val layerContext = context.with(
                    "layer_time" to state.time,
                    "layer_age" to state.age,
                    "layer_weight" to evaluator.float(layer.weight, context, 1f),
                )
                val weight = evaluator.float(layer.weight, layerContext, 1f)
                    .coerceIn(0f, 1f)
                    .times(layer.fadeInScale(state))
                if (weight <= 0f) return@forEach

                val allowedNodes = bakedMask(layer.mask, rootNodes)
                val sample = when (layer) {
                    is ClipAnimationLayerSpec -> sampleClipLayer(
                        layer,
                        state,
                        animations,
                        allowedNodes,
                        layerContext,
                    )
                    is AnimationControllerLayerSpec -> sampleControllerLayer(
                        layer,
                        state,
                        animations,
                        allowedNodes,
                        layerContext
                    )

                    is ProceduralLayerSpec -> LayerSample(sampleProceduralLayer(layer, rootNodes, allowedNodes, layerContext))
                    is MaterialOverrideLayerSpec -> null
                } ?: return@forEach
                val finalWeight = weight * sample.weightScale
                if (finalWeight <= 0f) return@forEach

                val reference = (layer as? ClipAnimationLayerSpec)
                    ?.referencePose
                    ?.let(animations::get)
                    ?.let { AnimationPose.sample(it, 0f, allowedNodes) }

                applyAnimationPose(sample.pose, runtimeNodes, layer.blendMode, finalWeight, reference)
            }
    }

    fun stateFor(layerId: String): LayerRuntimeState? = layerStates[layerId]

    private fun sampleClipLayer(
        layer: ClipAnimationLayerSpec,
        state: LayerRuntimeState,
        animations: Map<String, Animation>,
        allowedNodes: Set<Int>,
        context: AnimatorEvaluationContext,
    ): LayerSample? {
        val animation = animations[layer.animation] ?: return null
        val speed = evaluator.float(layer.speed, context, 1f)
        val sampleTime = state.advance(animation.duration, layer.playMode, speed, context.deltaTime)
        val fadeOutScale = layer.onceFadeOutScale(state)
        if (state.ended && layer.removeOnEnd && fadeOutScale <= 0f) return null
        return LayerSample(AnimationPose.sample(animation, sampleTime, allowedNodes), fadeOutScale)
    }

    private fun sampleControllerLayer(
        layer: AnimationControllerLayerSpec,
        state: LayerRuntimeState,
        animations: Map<String, Animation>,
        allowedNodes: Set<Int>,
        context: AnimatorEvaluationContext,
    ): LayerSample? {
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

            return LayerSample(AnimationPose.mix(fromPose, toPose, factor))
        }

        return LayerSample(sampleControllerState(current, state, animations, allowedNodes, context))
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
    var age = 0f
    var time = 0f
    var endElapsed = 0f
    var reversed = false
    var ended = false
    var currentState: String? = null
    var transition: ControllerTransitionRuntime? = null
    val stateTimes = linkedMapOf<String, Float>()
    private val stateReversed = linkedMapOf<String, Boolean>()

    fun advanceAge(deltaTime: Float) {
        age += deltaTime.coerceAtLeast(0f)
    }

    fun advance(duration: Float, playMode: AnimationPlayMode, speed: Float, deltaTime: Float): Float {
        if (duration <= 0f) return 0f
        val delta = speed * deltaTime * if (reversed) -1f else 1f
        if (ended && playMode == AnimationPlayMode.Once) {
            endElapsed += deltaTime.coerceAtLeast(0f) * speed.absoluteValue
            return duration
        }

        val rawTime = time + delta
        val result = wrapTime(rawTime, duration, playMode, reversed)
        time = result.time
        reversed = result.reversed
        ended = result.ended
        endElapsed = if (playMode == AnimationPlayMode.Once && result.ended) {
            (rawTime - duration).coerceAtLeast(0f)
        } else {
            0f
        }
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

private data class LayerSample(
    val pose: AnimationPose,
    val weightScale: Float = 1f,
)

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
    (this % divisor + divisor) % divisor

private fun AnimatorLayerSpec.fadeInScale(state: LayerRuntimeState): Float =
    if (fadeIn <= 0f) 1f else (state.age / fadeIn).coerceIn(0f, 1f)

private fun ClipAnimationLayerSpec.onceFadeOutScale(state: LayerRuntimeState): Float =
    if (playMode != AnimationPlayMode.Once || fadeOut <= 0f || !state.ended) {
        1f
    } else {
        (1f - state.endElapsed / fadeOut).coerceIn(0f, 1f)
    }

private val Float.absoluteValue: Float
    get() = if (this < 0f) -this else this
