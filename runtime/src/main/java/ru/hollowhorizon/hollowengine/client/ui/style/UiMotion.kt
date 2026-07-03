package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

sealed class TransitionEasing {
    abstract fun transform(progress: Float): Float

    open fun inverse(progress: Float): Float {
        val target = progress.coerceIn(0f, 1f)
        var low = 0f
        var high = 1f
        repeat(16) {
            val mid = (low + high) / 2f
            if (transform(mid) < target) low = mid else high = mid
        }
        return (low + high) / 2f
    }

    data object LINEAR : TransitionEasing() {
        override fun transform(progress: Float): Float = progress.coerceIn(0f, 1f)
        override fun inverse(progress: Float): Float = progress.coerceIn(0f, 1f)
    }

    data object EASE_IN : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            return linear * linear
        }
    }

    data object EASE_OUT : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            return 1f - (1f - linear) * (1f - linear)
        }
    }

    data object EASE_IN_OUT : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            return if (linear < 0.5f) 2f * linear * linear else 1f - 2f * (1f - linear) * (1f - linear)
        }
    }

    data class Steps(
        val count: Int,
        val position: StepPosition = StepPosition.END,
    ) : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val linear = progress.coerceIn(0f, 1f)
            if (linear >= 1f) return 1f
            val steps = count.coerceAtLeast(1).toFloat()
            return when (position) {
                StepPosition.START -> ceil(linear * steps) / steps
                StepPosition.END -> floor(linear * steps) / steps
            }.coerceIn(0f, 1f)
        }
    }

    data class CubicBezier(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
    ) : TransitionEasing() {
        override fun transform(progress: Float): Float {
            val targetX = progress.coerceIn(0f, 1f)
            var low = 0f
            var high = 1f
            repeat(18) {
                val mid = (low + high) / 2f
                if (sampleCurve(mid, x1, x2) < targetX) low = mid else high = mid
            }
            return sampleCurve((low + high) / 2f, y1, y2)
        }

        private fun sampleCurve(t: Float, a1: Float, a2: Float): Float {
            val inverse = 1f - t
            return 3f * inverse * inverse * t * a1 + 3f * inverse * t * t * a2 + t * t * t
        }
    }

    enum class StepPosition {
        START, END
    }
}

data class UiTransition(
    val property: String,
    val durationMillis: Long,
    val easing: TransitionEasing = TransitionEasing.LINEAR,
) {
    fun progress(elapsedMillis: Long): Float {
        if (durationMillis <= 0L) return 1f
        val linear = (elapsedMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
        return easing.transform(linear)
    }

    fun complete(elapsedMillis: Long): Boolean = durationMillis <= 0L || elapsedMillis >= durationMillis
}

data class UiKeyframes(
    val name: String,
    val frames: List<UiKeyframe>,
) {
    private val sortedFrames = frames.sortedWith(compareBy<UiKeyframe> { it.offset }.thenBy { frames.indexOf(it) })

    fun sample(base: UiComputedStyle, progress: Float, easing: TransitionEasing): UiComputedStyle {
        if (sortedFrames.isEmpty()) return base
        val offset = progress.coerceIn(0f, 1f)
        val previous = sortedFrames.lastOrNull { it.offset <= offset } ?: sortedFrames.first()
        val next = sortedFrames.firstOrNull { it.offset >= offset } ?: sortedFrames.last()
        if (previous == next || previous.offset == next.offset) return base.withKeyframePatch(next)
        val local = ((offset - previous.offset) / (next.offset - previous.offset)).coerceIn(0f, 1f)
        val eased = easing.transform(local)
        return base.withKeyframePatch(previous)
            .interpolate(base.withKeyframePatch(next), UiTransitionProgress.all(eased))
    }
}

data class UiKeyframe(
    val offset: Float,
    val style: UiStylePatch,
    val properties: Set<String> = emptySet(),
)

data class UiAnimation(
    val name: String,
    val durationMillis: Long = 0L,
    val easing: TransitionEasing = TransitionEasing.LINEAR,
    val delayMillis: Long = 0L,
    val iterationCount: Float = 1f,
    val direction: UiAnimationDirection = UiAnimationDirection.NORMAL,
    val fillMode: UiAnimationFillMode = UiAnimationFillMode.NONE,
    val playState: UiAnimationPlayState = UiAnimationPlayState.RUNNING,
)

enum class UiAnimationDirection {
    NORMAL, REVERSE, ALTERNATE, ALTERNATE_REVERSE
}

enum class UiAnimationFillMode {
    NONE, FORWARDS, BACKWARDS, BOTH
}

enum class UiAnimationPlayState {
    RUNNING, PAUSED
}

fun UiAnimation.totalDurationMillis(): Long? {
    if (playState != UiAnimationPlayState.RUNNING || name.isBlank()) return 0L
    if (iterationCount.isInfinite()) return null
    val iterations = iterationCount.coerceAtLeast(0f)
    if (iterations <= 0f) return 0L
    val activeDuration = ceil(durationMillis.toFloat() * iterations).toLong().coerceAtLeast(0L)
    return delayMillis.coerceAtLeast(0L) + activeDuration
}

fun UiComputedStyle.motionDurationMillis(previous: UiComputedStyle?): Long {
    val transitionDuration = if (previous == null) {
        0L
    } else {
        transitions.filter { transition -> previous.changed(transition.property, this) }
            .maxOfOrNull { it.durationMillis } ?: 0L
    }
    val animationDuration = if (previous == null || previous.animations != animations) {
        animations.maxOfOrNull { it.totalDurationMillis() ?: 0L } ?: 0L
    } else {
        0L
    }
    return max(transitionDuration, animationDuration)
}

internal fun List<UiTransition>?.mergeUiTransitions(other: List<UiTransition>): List<UiTransition> {
    if (other.isEmpty()) return emptyList()
    val merged = linkedMapOf<String, UiTransition>()
    orEmpty().forEach { merged[it.property] = it }
    other.forEach { merged[it.property] = it }
    return merged.values.toList()
}

/** Reverts transform components a keyframe did not explicitly declare back to the base style. */
private fun UiComputedStyle.withKeyframePatch(frame: UiKeyframe): UiComputedStyle {
    val patched = with(frame.style)
    if (frame.style.transform == null) return patched
    val properties = frame.properties
    val mask = UiStylePatch()
    mask.translate = maskVec3(this[UiProps.Translate], patched[UiProps.Translate], properties, "transform.translate")
    mask.rotate = maskVec3(this[UiProps.Rotate], patched[UiProps.Rotate], properties, "transform.rotate")
    mask.scale = maskVec3(this[UiProps.Scale], patched[UiProps.Scale], properties, "transform.scale")
    mask.pivot = if ("transform.pivot" in properties) patched[UiProps.Pivot] else this[UiProps.Pivot]
    mask.perspective = if ("transform.perspective" in properties) patched[UiProps.Perspective] else this[UiProps.Perspective]
    return patched.with(mask)
}

private fun maskVec3(base: UiVec3, next: UiVec3, properties: Set<String>, prefix: String) = UiVec3(
    x = if ("$prefix.x" in properties) next.x else base.x,
    y = if ("$prefix.y" in properties) next.y else base.y,
    z = if ("$prefix.z" in properties) next.z else base.z,
)

class UiTransitionState {
    private val rendered = WeakHashMap<UiNode, UiComputedStyle>()
    private val starts = WeakHashMap<UiNode, UiComputedStyle>()
    private val targets = WeakHashMap<UiNode, UiComputedStyle>()
    private val startedAt = WeakHashMap<UiNode, Long>()
    private val activeDurations = WeakHashMap<UiNode, Long>()
    private val startedDurations = WeakHashMap<UiNode, Long>()

    fun apply(node: UiNode, target: UiComputedStyle, nowMillis: Long): UiComputedStyle {
        startedDurations[node] = 0L
        val current = rendered[node]
        if (current == null) {
            rendered[node] = target
            targets[node] = target
            activeDurations[node] = 0L
            return target
        }
        val oldTarget = targets[node]
        var targetChanged = false
        if (oldTarget != target) {
            starts[node] = current
            targets[node] = target
            startedAt[node] = nowMillis
            targetChanged = true
        } else if (!startedAt.containsKey(node)) {
            activeDurations[node] = 0L
            return target
        }
        val startStyle = starts[node] ?: current
        val transitions = target.transitions.filter { transition ->
            startStyle.changed(transition.property, target)
        }
        if (transitions.isEmpty()) return target.also {
            rendered[node] = target
            targets[node] = target
            starts.remove(node)
            startedAt.remove(node)
            activeDurations[node] = 0L
            startedDurations[node] = 0L
        }
        val duration = transitions.maxOfOrNull { it.durationMillis } ?: 0L
        activeDurations[node] = duration
        if (targetChanged) startedDurations[node] = duration
        val start = startedAt[node] ?: nowMillis
        val elapsed = max(0L, nowMillis - start)
        val progress = transitions.progressAt(elapsed)
        val result = startStyle.interpolate(target, progress)
        rendered[node] = result
        if (progress.complete() && transitions.all { it.complete(elapsed) }) {
            rendered[node] = target
            targets[node] = target
            starts.remove(node)
            startedAt.remove(node)
            activeDurations[node] = 0L
            startedDurations[node] = 0L
        }
        return result
    }

    fun activeDurationMillis(node: UiNode): Long = activeDurations[node] ?: 0L

    fun startedDurationMillis(node: UiNode): Long = startedDurations[node] ?: 0L

    fun hasActiveTransitions(): Boolean = activeDurations.values.any { it > 0L }

    private fun List<UiTransition>.progressAt(elapsedMillis: Long): UiTransitionProgress {
        val fallback = firstOrNull { it.property == "all" }
        val values = HashMap<UiStyleProp<*>, Float>()
        for (prop in UiStyleProp.transitionable) {
            val transition = firstOrNull { it.property != "all" && prop in UiStyleProp.forTransitionProperty(it.property) }
                ?: fallback
                ?: continue
            values[prop] = transition.progress(elapsedMillis)
        }
        return UiTransitionProgress.of(values)
    }
}

class UiAnimationState {
    private val starts = WeakHashMap<UiNode, AnimationStart>()

    fun apply(
        node: UiNode,
        base: UiComputedStyle,
        keyframes: Map<String, UiKeyframes>,
        nowMillis: Long,
    ): UiComputedStyle {
        val animations = base.animations.filter { it.playState == UiAnimationPlayState.RUNNING && it.name.isNotBlank() }
        if (animations.isEmpty()) {
            starts.remove(node)
            return base
        }
        val signature = animations
        val start = starts[node]
        val startedAt = if (start == null || start.signature != signature) {
            starts[node] = AnimationStart(signature, nowMillis)
            nowMillis
        } else {
            start.startedAtMillis
        }
        return animations.fold(base) { style, animation ->
            val frames = keyframes[animation.name] ?: return@fold style
            val progress = animationProgress(animation, nowMillis - startedAt) ?: return@fold style
            frames.sample(style, progress, animation.easing)
        }
    }

    private fun animationProgress(animation: UiAnimation, elapsedMillis: Long): Float? {
        val activeElapsed = elapsedMillis - animation.delayMillis
        if (activeElapsed < 0L) {
            return if (animation.fillMode == UiAnimationFillMode.BACKWARDS || animation.fillMode == UiAnimationFillMode.BOTH) {
                directedProgress(animation, 0, 0f)
            } else {
                null
            }
        }
        if (animation.durationMillis <= 0L) return directedProgress(animation, 0, 1f)
        val duration = animation.durationMillis.toFloat()
        val iterations = animation.iterationCount
        val totalDuration =
            if (iterations.isInfinite()) Float.POSITIVE_INFINITY else duration * iterations.coerceAtLeast(0f)
        if (totalDuration <= 0f) return null
        if (activeElapsed.toFloat() >= totalDuration) {
            return if (animation.fillMode == UiAnimationFillMode.FORWARDS || animation.fillMode == UiAnimationFillMode.BOTH) {
                val finalIteration = floor(iterations.coerceAtLeast(1f) - 0.0001f).toInt().coerceAtLeast(0)
                directedProgress(animation, finalIteration, 1f)
            } else {
                null
            }
        }
        val iteration = floor(activeElapsed / duration).toInt()
        val local = ((activeElapsed % animation.durationMillis).toFloat() / duration).coerceIn(0f, 1f)
        return directedProgress(animation, iteration, local)
    }

    private fun directedProgress(animation: UiAnimation, iteration: Int, local: Float): Float {
        val reverse = when (animation.direction) {
            UiAnimationDirection.NORMAL -> false
            UiAnimationDirection.REVERSE -> true
            UiAnimationDirection.ALTERNATE -> iteration % 2 == 1
            UiAnimationDirection.ALTERNATE_REVERSE -> iteration % 2 == 0
        }
        return if (reverse) 1f - local else local
    }

    private data class AnimationStart(
        val signature: List<UiAnimation>,
        val startedAtMillis: Long,
    )
}
