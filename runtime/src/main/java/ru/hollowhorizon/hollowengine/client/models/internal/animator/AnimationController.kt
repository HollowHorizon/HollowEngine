package ru.hollowhorizon.hollowengine.client.models.internal.animator

import ru.hollowhorizon.hollowengine.client.models.internal.animator.AnimatorExpressionEvaluator as evaluator
import ru.hollowhorizon.hollowengine.common.models.ANY_STATE
import ru.hollowhorizon.hollowengine.common.models.AnimationControllerLayerSpec
import ru.hollowhorizon.hollowengine.common.models.AnimationControllerStateSpec
import ru.hollowhorizon.hollowengine.common.models.AnimationControllerTransitionSpec
import ru.hollowhorizon.hollowengine.common.models.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.models.AnimatorLayerSpec

/**
 * A state machine over clips: which state the model is in, and the crossfade while it moves to the next.
 */
class AnimationController(initialSpec: AnimationControllerLayerSpec) {
    private var spec = initialSpec
    private val stateTimes = LinkedHashMap<String, Float>()
    private val stateReversed = LinkedHashMap<String, Boolean>()
    private var transition: Transition? = null

    /** The state the model is in, or null before the first frame decided. */
    var stateId: String? = null
        private set

    /** How far into its own clip the current state is, for callers showing progress. */
    val stateTime: Float get() = stateId?.let { stateTimes[it] } ?: 0f

    /** Updates controller rules while retaining playback for states that still exist. */
    fun configure(next: AnimationControllerLayerSpec) {
        if (spec == next) return
        spec = next

        val stateIds = next.states.mapTo(HashSet(next.states.size)) { it.id }
        stateTimes.keys.removeIf { it !in stateIds }
        stateReversed.keys.removeIf { it !in stateIds }

        if (stateId !in stateIds) {
            stateId = null
            transition = null
        } else if (transition?.let { it.from !in stateIds || it.to !in stateIds } == true) {
            transition = null
        }
    }

    fun sample(target: PoseTarget, allowed: Set<Int>, context: AnimatorEvaluationContext): AnimationPose? {
        if (spec.states.isEmpty()) return null
        if (stateId == null) stateId = spec.entryState ?: spec.states.first().id
        val current = spec.states.firstOrNull { it.id == stateId } ?: spec.states.first()

        beginTransition(current, context)

        val running = transition ?: return sampleState(current, target, allowed, context)

        running.elapsed += context.deltaTime
        val factor = if (running.duration <= 0f) 1f else (running.elapsed / running.duration).coerceIn(0f, 1f)
        val from = spec.states.firstOrNull { it.id == running.from } ?: current
        val to = spec.states.firstOrNull { it.id == running.to } ?: current
        val fromPose = sampleState(from, target, allowed, context)
        val toPose = sampleState(to, target, allowed, context)

        if (factor >= 1f) {
            stateId = to.id
            transition = null
        }

        return AnimationPose.mix(fromPose, toPose, factor)
    }

    private fun beginTransition(current: AnimationControllerStateSpec, context: AnimatorEvaluationContext) {
        if (transition != null) return
        val selected = selectTransition(current, context) ?: return
        val duration = evaluator.float(selected.duration, context, 0f).coerceAtLeast(0f)
        transition = Transition(from = current.id, to = selected.to, duration = duration)

        stateTimes[selected.to] = 0f
        stateReversed[selected.to] = false
    }

    private fun selectTransition(
        current: AnimationControllerStateSpec,
        context: AnimatorEvaluationContext,
    ): AnimationControllerTransitionSpec? {
        val currentTime = stateTimes[current.id] ?: 0f
        context.stateTime = currentTime

        return spec.transitions
            .asSequence()
            .filter { it.from == current.id || it.from == ANY_STATE }
            .filter { transition ->
                val exitTime = transition.exitTime
                exitTime == null || currentTime >= exitTime
            }
            .filter { evaluator.boolean(it.condition, context, false) }
            .sortedWith(compareByDescending<AnimationControllerTransitionSpec> { it.priority }.thenBy { it.to })
            .firstOrNull()
            ?.takeIf { it.to != current.id }
    }

    private fun sampleState(
        state: AnimationControllerStateSpec,
        target: PoseTarget,
        allowed: Set<Int>,
        context: AnimatorEvaluationContext,
    ): AnimationPose {
        val animation = target.animations[state.animation] ?: return AnimationPose()
        context.stateTime = stateTimes[state.id] ?: 0f
        val speed = evaluator.float(state.speed, context, 1f)
        val time = advance(state.id, animation.duration, state.playMode, speed, context.deltaTime)
        return AnimationPose.sample(animation, time, allowed)
    }

    private fun advance(
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

    private class Transition(val from: String, val to: String, val duration: Float) {
        var elapsed: Float = 0f
    }
}

/** The layer a controller poses through. */
class ControllerLayer(controllerSpec: AnimationControllerLayerSpec) : SpecLayer(controllerSpec) {
    val controller = AnimationController(controllerSpec)

    override val time: Float get() = controller.stateTime

    override fun accepts(spec: AnimatorLayerSpec): Boolean = spec is AnimationControllerLayerSpec

    override fun onReconfigured(spec: AnimatorLayerSpec) {
        controller.configure(spec as AnimationControllerLayerSpec)
    }

    override fun sample(target: PoseTarget, context: AnimatorEvaluationContext): LayerPose? =
        controller.sample(target, mask(target), context)?.let(::LayerPose)
}
