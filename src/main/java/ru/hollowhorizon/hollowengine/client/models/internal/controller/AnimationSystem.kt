package ru.hollowhorizon.hollowengine.client.models.internal.controller

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.AnimatableFloat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment

class AnimationSystem(val model: ModelAttachment) {

    val dispatcher = AnimationDispatcher("Animation System")
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun update(dt: Float) {
        dispatcher.update(dt)
    }

    fun onUpdate(action: suspend () -> Unit) {
        scope.launch {
            while (isActive) {
                action()
                dispatcher.awaitNextFrame()
            }
        }
    }

    suspend fun transition(
        from: String? = null,
        to: String? = null,
        fadeTime: Float = 0.33f,
        easing: Easing.Easing = Easing.smooth,
    ) {
        val original = from?.let { model.animations[it] }
        val target = to?.let { model.animations[it] }

        val animatable = AnimatableFloat(0f)

        target?.time = 0f
        target?.wrapMode = WrapMode.Loop

        animatable.onChange { old, new ->
            target?.weight = new
            original?.weight = 1f - new
        }

        animatable.animateTo(1f, fadeTime, easing)
    }
}