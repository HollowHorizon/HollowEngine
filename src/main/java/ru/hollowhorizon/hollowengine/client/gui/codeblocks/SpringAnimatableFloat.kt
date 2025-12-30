package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.clamp
import de.fabmax.kool.math.lerp
import de.fabmax.kool.modules.ui2.Animatable
import de.fabmax.kool.modules.ui2.LaunchedEffect
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.remember
import de.fabmax.kool.util.Time
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.takeWhile
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.Math.abs
import kotlin.math.sqrt

class SpringAnimatableFloat(initialValue: Float = 0f) : Animatable<Float>(initialValue) {
    private var velocity = 0f

    /**
     * Animates the value from its current value to the [targetValue].
     *
     * @param targetValue The target value of the animation.
     * @param duration The duration of the animation in seconds.
     * @param easing The easing function to use for the animation.
     */
    override suspend fun animateTo(targetValue: Float, duration: Float, easing: Easing.Easing) {
        if (duration <= 0f) {
            set(targetValue)
            return
        }
        val startTime = Time.gameTime
        val startValue = value

        Time.frameFlow.takeWhile {
            val elapsed = Time.gameTime - startTime
            val progress = (elapsed / duration).toFloat().clamp()
            set(startValue.lerp(targetValue, easing.eased(progress)))
            progress < 1f
        }.count()
    }

    suspend fun animateToSpring(targetValue: Float, stiffness: Float = 500f, damping: Float = 0.75f) {
        val startTime = Time.gameTime
        var lastTime = startTime

        Time.frameFlow.takeWhile {
            val currentTime = Time.gameTime
            val dt = (currentTime - lastTime).toFloat().coerceAtMost(0.1f)
            lastTime = currentTime

            // F = -k * x - c * v
            // k = stiffness, c = damping * 2 * sqrt(k)
            val displacement = value - targetValue
            val c = damping * 2f * sqrt(stiffness)
            val force = -stiffness * displacement - c * velocity

            // Integrate (Mass = 1)
            velocity += force * dt
            set(value + velocity * dt)

            abs(displacement) > 0.001f || kotlin.math.abs(velocity) > 0.01f
        }.count()

        set(targetValue)
        velocity = 0f
    }

    override fun setLerp(start: Float, target: Float, progress: Float) {
        set(start.lerp(target, progress))
    }
}

fun UiScope.animateSpringFloatAsState(
    target: Float,
    stiffness: Float = 500f,
    damping: Float = 0.75f
): SpringAnimatableFloat {
    val anim = remember { SpringAnimatableFloat(target) }

    LaunchedEffect(target) {
        anim.animateToSpring(target, stiffness, damping)
    }

    return anim
}