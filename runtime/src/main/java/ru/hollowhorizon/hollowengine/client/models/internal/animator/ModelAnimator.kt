package ru.hollowhorizon.hollowengine.client.models.internal.animator

import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.common.attachments.components.AnimationControllerLayerSpec
import ru.hollowhorizon.hollowengine.common.attachments.components.AnimatorComponent
import ru.hollowhorizon.hollowengine.common.attachments.components.AnimatorLayerSpec
import ru.hollowhorizon.hollowengine.common.attachments.components.ClipAnimationLayerSpec
import ru.hollowhorizon.hollowengine.common.attachments.components.ProceduralLayerSpec

/**
 * Animator for one model instance: a stack of layers, blended in priority order.
 */
class ModelAnimator {
    private val fromComponent = LinkedHashMap<String, SpecLayer>()
    private val clientLayers = LinkedHashMap<String, AnimationLayer>()
    private var component: AnimatorComponent? = null
    private var lastTicks: Float = Float.NaN

    /** Reconciles component layers by stable id; playback survives spec updates of the same layer kind. */
    fun configure(animator: AnimatorComponent?) {
        if (component == animator) return
        component = animator
        animator?.let(AnimatorExpressionEvaluator::prepare)

        val specs = animator?.takeIf { it.enabled }?.layers.orEmpty()
        val rebuilt = LinkedHashMap<String, SpecLayer>(specs.size)
        specs.forEach { spec ->
            val existing = fromComponent[spec.id]?.takeIf { it.reconfigure(spec) }
            rebuilt[spec.id] = existing ?: layerFor(spec)
        }
        fromComponent.clear()
        fromComponent.putAll(rebuilt)
    }

    /** Adds a layer the server knows nothing about; replaces one with the same id. */
    fun add(layer: AnimationLayer) {
        clientLayers[layer.id] = layer
    }

    fun remove(id: String) {
        clientLayers.remove(id)
    }

    /** Where a layer is inside its animation, for progress bars and the editor. */
    fun layerTime(id: String): Float? = (fromComponent[id] ?: clientLayers[id] as? SpecLayer)?.time

    val isEmpty: Boolean get() = fromComponent.isEmpty() && clientLayers.isEmpty()

    /**
     * Poses [attachment]'s nodes for this frame.
     */
    fun applyTo(attachment: ModelAttachment, context: AnimatorEvaluationContext) =
        applyTo(attachment.poseTarget(), context)

    fun applyTo(target: PoseTarget, context: AnimatorEvaluationContext) {
        context.deltaTime = deltaSeconds(context.time)
        if (isEmpty) return

        val layers = (fromComponent.values + clientLayers.values).sortedBy { it.priority }
        layers.forEach { layer ->
            val weight = layer.weight(context)
            if (weight <= 0f) return@forEach

            val sampled = layer.sample(target, context) ?: return@forEach
            val finalWeight = weight * sampled.weightScale
            if (finalWeight <= 0f) return@forEach

            target.apply(sampled.pose, layer.blendMode, finalWeight, sampled.reference)
        }

        fromComponent.values.removeIf(AnimationLayer::finished)
        clientLayers.values.removeIf(AnimationLayer::finished)
    }

    /**
     * Seconds since the last frame, from the tick clock.
     */
    private fun deltaSeconds(nowTicks: Float): Float {
        val previous = lastTicks
        lastTicks = nowTicks
        if (previous.isNaN()) return 0f
        return ((nowTicks - previous) / TICKS_PER_SECOND).coerceIn(0f, 1f)
    }

    private fun layerFor(spec: AnimatorLayerSpec): SpecLayer = when (spec) {
        is ClipAnimationLayerSpec -> ClipLayer(spec)
        is AnimationControllerLayerSpec -> ControllerLayer(spec)
        is ProceduralLayerSpec -> ProceduralLayer(spec)
    }

    private companion object {
        const val TICKS_PER_SECOND = 20f
    }
}
