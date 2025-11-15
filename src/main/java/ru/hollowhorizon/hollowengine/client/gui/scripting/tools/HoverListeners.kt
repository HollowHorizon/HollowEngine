package ru.hollowhorizon.hollowengine.client.gui.scripting.tools

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MutableColor

fun UiScope.hoverListener(
    duration: Float = 0.5f,
    condition: () -> Boolean = { true },
): Pair<MutableStateValue<Boolean>, AnimatedFloat> {
    val anim = remember { AnimatedFloat(duration) }

    return remember(false).apply {
        modifier
            .onEnter {
                set(true)
                if (condition()) {
                    anim.start()
                }
            }
            .onExit { set(false); if (condition()) anim.start() }

    } to anim
}

fun UiScope.hoverColors(
    duration: Float = 0.5f,
    color: Color,
    hoverColor: Color,
    condition: () -> Boolean = { true },
): MutableColor {
    val (isHovered, anim) = hoverListener(duration, condition)

    var factor = Easing.quadRev(anim.progressAndUse())
    if (!isHovered.use()) factor = 1f - factor
    return color.mix(hoverColor, factor)
}

fun UiScope.hoverColors(
    duration: Float = 0.5f,
    colors: List<Color>,
    hoverColors: List<Color>,
    condition: () -> Boolean = { true },
): List<MutableColor> {
    val (isHovered, anim) = hoverListener(duration, condition)

    var factor = Easing.quadRev(anim.progressAndUse())
    if (!isHovered.use()) factor = 1f - factor
    return colors.mapIndexed { i, color -> color.mix(hoverColors[i], factor) }
}

fun UiScope.hoverFactor(): MutableStateValue<Float> {
    val (isHovered, anim) = hoverListener()

    return remember(0f).apply {
        var factor = Easing.quadRev(anim.progressAndUse())
        if (!isHovered.use()) factor = 1f - factor
        set(factor)
    }
}

class AnimatedFloat(var duration: Float, initValue: Float = 1f) : AnimatedState<Float>(initValue) {
    override var isActive = false
        private set
    var progressionTime = duration

    fun start() {
        set(0f)
        stateChanged()
        progressionTime = duration - progressionTime
        isActive = true
    }

    override fun progress(deltaT: Float) {
        if (progressionTime < duration) {
            progressionTime += deltaT
            if (progressionTime >= duration) {
                set(1f)
                isActive = false
            } else {
                set(progressionTime / duration)
            }
        } else {
            progressionTime = duration
            isActive = false
        }
    }
}