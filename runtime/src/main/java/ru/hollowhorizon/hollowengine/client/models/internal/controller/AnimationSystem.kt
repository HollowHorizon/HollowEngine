package ru.hollowhorizon.hollowengine.client.models.internal.controller

import kotlinx.coroutines.*
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.client.utils.math.AnimatableFloat
import ru.hollowhorizon.hollowengine.common.utils.math.Easing

class AnimationSystem(val model: ModelAttachment) {

    val dispatcher = AnimationDispatcher("Animation System")
    val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val transitionJobs = HashMap<String, Job>()

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

    fun transition(
        from: String? = null,
        to: String? = null,
        duration: Float = 0.33f,
        wrapMode: WrapMode = WrapMode.Loop,
        easing: Easing.Easing = Easing.smooth,
    ) {
        val original = from?.let { model.animations[it] }
        val target = to?.let { model.animations[it] }

        val key = "${from ?: ""}->${to ?: ""}"
        transitionJobs.remove(key)?.cancel()

        val animatable = AnimatableFloat(0f)

        target?.time = 0f
        target?.wrapMode = wrapMode

        animatable.onChange { old, new ->
            target?.weight = new
            original?.weight = 1f - new
        }

        val job = scope.launch {
            animatable.animateTo(1f, duration, easing)
        }
        transitionJobs[key] = job
    }
}