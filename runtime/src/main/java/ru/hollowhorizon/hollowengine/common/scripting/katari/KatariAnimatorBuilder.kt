package ru.hollowhorizon.hollowengine.common.scripting.katari

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import ru.hollowhorizon.hollowengine.common.geary.components.ANY_STATE
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationControllerLayerSpec
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationControllerStateSpec
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationControllerTransitionSpec
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationExpression
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationVectorExpression
import ru.hollowhorizon.hollowengine.common.geary.components.AnimatorComponent
import ru.hollowhorizon.hollowengine.common.geary.components.AnimatorLayerSpec
import ru.hollowhorizon.hollowengine.common.geary.components.BoneMask
import ru.hollowhorizon.hollowengine.common.geary.components.ClipAnimationLayerSpec
import ru.hollowhorizon.hollowengine.common.geary.components.LayerBlendMode
import ru.hollowhorizon.hollowengine.common.geary.components.ProceduralBoneTransformSpec
import ru.hollowhorizon.hollowengine.common.geary.components.ProceduralLayerSpec

class KatariAnimatorBuilder(
    enabled: Boolean = true,
    layers: List<AnimatorLayerSpec> = emptyList(),
) {
    var enabled: Boolean = enabled
        private set

    private val layers = layers.toMutableList()

    fun setEnabled(enabled: Boolean): KatariAnimatorBuilder = apply {
        this.enabled = enabled
    }

    fun clear(): KatariAnimatorBuilder = apply {
        layers.clear()
    }

    fun removeLayer(id: String): KatariAnimatorBuilder = apply {
        layers.removeAll { it.id == id }
    }

    fun clip(
        id: String,
        animation: String,
        playMode: AnimationPlayMode = AnimationPlayMode.Once,
        speed: String = "1",
        weight: String = "1",
        priority: Int = 0,
        blendMode: LayerBlendMode = LayerBlendMode.Override,
        mask: BoneMask = BoneMask.full(),
        fadeIn: Float = 0f,
        fadeOut: Float = 0f,
        referencePose: String? = null,
        removeOnEnd: Boolean = playMode == AnimationPlayMode.Once,
    ): KatariAnimatorBuilder = replace(
        ClipAnimationLayerSpec(
            id = id,
            animation = animation,
            playMode = playMode,
            speed = speed.expr(),
            weight = weight.expr(),
            priority = priority,
            blendMode = blendMode,
            mask = mask,
            fadeIn = fadeIn,
            fadeOut = fadeOut,
            referencePose = referencePose,
            removeOnEnd = removeOnEnd,
        )
    )

    fun controller(
        id: String,
        entryState: String? = null,
        weight: String = "1",
        priority: Int = 0,
        blendMode: LayerBlendMode = LayerBlendMode.Override,
        mask: BoneMask = BoneMask.full(),
        fadeIn: Float = 0f,
        fadeOut: Float = 0f,
    ): KatariAnimatorBuilder = replace(
        AnimationControllerLayerSpec(
            id = id,
            entryState = entryState,
            weight = weight.expr(),
            priority = priority,
            blendMode = blendMode,
            mask = mask,
            fadeIn = fadeIn,
            fadeOut = fadeOut,
        )
    )

    fun state(
        controllerId: String,
        stateId: String,
        animation: String,
        playMode: AnimationPlayMode = AnimationPlayMode.Loop,
        speed: String = "1",
        referencePose: String? = null,
    ): KatariAnimatorBuilder = updateController(controllerId) { layer ->
        layer.copy(
            states = layer.states.filterNot { it.id == stateId } + AnimationControllerStateSpec(
                id = stateId,
                animation = animation,
                playMode = playMode,
                speed = speed.expr(),
                referencePose = referencePose,
            ),
            entryState = layer.entryState ?: stateId,
        )
    }

    fun transition(
        controllerId: String,
        from: String,
        to: String,
        condition: String = "true",
        duration: String = "0",
        priority: Int = 0,
        exitTime: Float? = null,
    ): KatariAnimatorBuilder = updateController(controllerId) { layer ->
        layer.copy(
            transitions = layer.transitions.filterNot { it.from == from && it.to == to } +
                AnimationControllerTransitionSpec(
                    from = from.ifBlank { ANY_STATE },
                    to = to,
                    condition = condition.expr(),
                    duration = duration.expr(),
                    priority = priority,
                    exitTime = exitTime,
                )
        )
    }

    fun procedural(
        id: String,
        weight: String = "1",
        priority: Int = 0,
        blendMode: LayerBlendMode = LayerBlendMode.Additive,
        mask: BoneMask = BoneMask.full(),
        fadeIn: Float = 0f,
        fadeOut: Float = 0f,
    ): KatariAnimatorBuilder = replace(
        ProceduralLayerSpec(
            id = id,
            weight = weight.expr(),
            priority = priority,
            blendMode = blendMode,
            mask = mask,
            fadeIn = fadeIn,
            fadeOut = fadeOut,
        )
    )

    fun boneTransform(
        layerId: String,
        bone: String,
        translation: AnimationVectorExpression? = null,
        rotation: AnimationVectorExpression? = null,
        scale: AnimationVectorExpression? = null,
    ): KatariAnimatorBuilder = updateProcedural(layerId) { layer ->
        layer.copy(
            transforms = layer.transforms.filterNot { it.bone == bone } + ProceduralBoneTransformSpec(
                bone = bone,
                translation = translation,
                rotation = rotation,
                scale = scale,
            )
        )
    }

    fun build(): AnimatorComponent = AnimatorComponent(enabled = enabled, layers = layers.toList())

    fun snapshot(): KatariAnimatorBuilderSnapshot = KatariAnimatorBuilderSnapshot(build())

    private fun replace(layer: AnimatorLayerSpec): KatariAnimatorBuilder = apply {
        layers.removeAll { it.id == layer.id }
        layers += layer
    }

    private fun updateController(
        id: String,
        update: (AnimationControllerLayerSpec) -> AnimationControllerLayerSpec,
    ): KatariAnimatorBuilder = apply {
        val index = layers.indexOfFirst { it.id == id }
        val current = layers.getOrNull(index) as? AnimationControllerLayerSpec
            ?: AnimationControllerLayerSpec(id = id)
        val updated = update(current)
        if (index >= 0) layers[index] = updated else layers += updated
    }

    private fun updateProcedural(
        id: String,
        update: (ProceduralLayerSpec) -> ProceduralLayerSpec,
    ): KatariAnimatorBuilder = apply {
        val index = layers.indexOfFirst { it.id == id }
        val current = layers.getOrNull(index) as? ProceduralLayerSpec ?: ProceduralLayerSpec(id = id)
        val updated = update(current)
        if (index >= 0) layers[index] = updated else layers += updated
    }
}

@Serializable
@SerialName("hollowengine:katari_animator")
data class KatariAnimatorBuilderSnapshot(
    val component: AnimatorComponent,
) : ValueSnapshot() {
    fun restore(): KatariAnimatorBuilder = KatariAnimatorBuilder(component.enabled, component.layers)
}

internal fun vectorExpression(x: String, y: String, z: String) =
    AnimationVectorExpression(x.expr(), y.expr(), z.expr())

private fun String.expr() = AnimationExpression(ifBlank { "0" })
