package ru.hollowhorizon.hollowengine.client.utils.math

import androidx.compose.runtime.MutableState
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.takeWhile
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler
import ru.hollowhorizon.hollowengine.common.utils.math.Easing
import ru.hollowhorizon.hollowengine.common.utils.math.clamp

abstract class Animatable<T : Any>(initialValue: T) : MutableState<T> {
    val listeners = mutableListOf<(old: T, new: T) -> Unit>()

    override var value: T = initialValue
        set(value) {
            listeners.forEach { it(field, value) }
            field = value
        }
    private val setter = { value: T -> this.value = value }

    override fun component1(): T {
        return value
    }

    override fun component2(): (T) -> Unit = setter

    abstract suspend fun animateTo(targetValue: T, duration: Float = 0.3f, easing: Easing.Easing = Easing.smooth)
    abstract fun setLerp(start: T, target: T, progress: Float)

    fun onChange(action: (old: T, new: T) -> Unit): MutableState<T> {
        listeners.add(action)
        return this
    }
}

class AnimatableFloat(initialValue: Float) : Animatable<Float>(initialValue) {
    /**
     * Animates the value from its current value to the [targetValue].
     *
     * @param targetValue The target value of the animation.
     * @param duration The duration of the animation in seconds.
     * @param easing The easing function to use for the animation.
     */
    override suspend fun animateTo(targetValue: Float, duration: Float, easing: Easing.Easing) {
        if (duration <= 0f) {
            value = targetValue
            return
        }
        val startTime = TickHandler.gameTime
        val startValue = value

        TickHandler.frameFlow.takeWhile {
            val elapsed = TickHandler.gameTime - startTime
            val progress = (elapsed / duration).clamp()
            value = startValue.lerp(targetValue, easing.eased(progress))
            progress < 1f
        }.count()
    }

    override fun setLerp(start: Float, target: Float, progress: Float) {
        value = start.lerp(target, progress)
    }
}