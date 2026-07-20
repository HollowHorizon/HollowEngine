package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.fabmax.kool.math.Easing
import de.fabmax.kool.util.Color
import kotlin.math.abs

enum class PlaybackMode {
    LOOP,
    ONCE
}


class Keyframe<T>(
    var time: Float,
    var value: T,
    var easing: Easing.Easing = Easing.linear
)

class AnimTrack<T>(
    name: String,
    val driver: PropertyDriver<T>,
    val defaultValue: T,
    val keyframes: MutableList<Keyframe<T>> = mutableListOf(),
    trackColor: Color = Color.WHITE
) : BaseAnimTrack(name, trackColor) {

    override fun getKeysAsList(): MutableList<out Keyframe<*>> {
        return keyframes
    }

    fun getInsertionValue(atTime: Float): T {
        val nearest = keyframes.minByOrNull { abs(it.time - atTime) }
        return nearest?.value ?: defaultValue
    }

    override fun update(time: Float) {
        if (keyframes.isEmpty()) {
            driver.apply(defaultValue)
            return
        }

        if (keyframes.size == 1) {
            driver.apply(keyframes[0].value)
            return
        }

        keyframes.sortBy { it.time }

        val firstKey = keyframes.first()
        val lastKey = keyframes.last()

        if (time <= firstKey.time) {
            driver.apply(firstKey.value)
            return
        }

        if (time >= lastKey.time) {
            driver.apply(lastKey.value)
            return
        }

        for (i in 0 until keyframes.size - 1) {
            val startKey = keyframes[i]
            val endKey = keyframes[i + 1]

            if (time >= startKey.time && time < endKey.time) {
                val duration = endKey.time - startKey.time
                if (duration <= 0.0001f) {
                    driver.apply(startKey.value)
                } else {
                    val rawFraction = (time - startKey.time) / duration
                    val easedFraction = startKey.easing.eased(rawFraction)

                    val interpolatedValue = driver.interpolate(startKey.value, endKey.value, easedFraction)
                    driver.apply(interpolatedValue)
                }
                return
            }
        }
    }
}

class TrackGroup(
    name: String,
    val tracks: MutableList<BaseAnimTrack> = mutableListOf(),
    val children: MutableList<TrackGroup> = mutableStateListOf(),
) {
    var nameState by mutableStateOf(name)
    var isCollapsed by mutableStateOf(false)
    var isLocked by mutableStateOf(false)
    var isVisible by mutableStateOf(true)
}

data class EasingVariant(val name: String, val function: Easing.Easing)
data class EasingCategory(val name: String, val variants: List<EasingVariant>)

val easingTypes = listOf(
    EasingCategory("Linear", listOf(
        EasingVariant("Linear", Easing.linear),
        EasingVariant("Smooth", Easing.smooth)
    )),
    EasingCategory("Sine", listOf(
        EasingVariant("In", Easing.easeInSine),
        EasingVariant("Out", Easing.easeOutSine),
        EasingVariant("In Out", Easing.easeInOutSine)
    )),
    EasingCategory("Quad", listOf(
        EasingVariant("In", Easing.easeInQuad),
        EasingVariant("Out", Easing.easeOutQuad),
        EasingVariant("In Out", Easing.easeInOutQuad)
    )),
    EasingCategory("Cubic", listOf(
        EasingVariant("In", Easing.easeInCubic),
        EasingVariant("Out", Easing.easeOutCubic),
        EasingVariant("In Out", Easing.easeInOutCubic)
    )),
    EasingCategory("Quart", listOf(
        EasingVariant("In", Easing.easeInQuart),
        EasingVariant("Out", Easing.easeOutQuart),
        EasingVariant("In Out", Easing.easeInOutQuart)
    )),
    EasingCategory("Quint", listOf(
        EasingVariant("In", Easing.easeInQuint),
        EasingVariant("Out", Easing.easeOutQuint),
        EasingVariant("In Out", Easing.easeInOutQuint)
    )),
    EasingCategory("Expo", listOf(
        EasingVariant("In", Easing.easeInExpo),
        EasingVariant("Out", Easing.easeOutExpo),
        EasingVariant("In Out", Easing.easeInOutExpo)
    )),
    EasingCategory("Circ", listOf(
        EasingVariant("In", Easing.easeInCirc),
        EasingVariant("Out", Easing.easeOutCirc),
        EasingVariant("In Out", Easing.easeInOutCirc)
    )),
    EasingCategory("Back", listOf(
        EasingVariant("In", Easing.easeInBack),
        EasingVariant("Out", Easing.easeOutBack),
        EasingVariant("In Out", Easing.easeInOutBack)
    )),
    EasingCategory("Bounce", listOf(
        EasingVariant("In", Easing.easeInBounce),
        EasingVariant("Out", Easing.easeOutBounce),
        EasingVariant("In Out", Easing.easeInOutBounce)
    )),
    EasingCategory("Elastic", listOf(
        EasingVariant("In", Easing.easeInElastic),
        EasingVariant("Out", Easing.easeOutElastic),
        EasingVariant("In Out", Easing.easeInOutElastic)
    ))
)

