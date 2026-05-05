package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.geary.api.set
import ru.hollowhorizon.hollowengine.common.geary.binding.EntitySnapshotPacket
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.*

@ScriptBinding
fun animatorController() = KatariAnimatorBuilder()

@ScriptBinding
@Serializable
class KatariAnimatorBuilder @ScriptIgnore constructor(
    val enabled: Boolean = true,
    @property:ScriptIgnore
    val layers: MutableList<AnimatorLayerSpec> = mutableListOf(),
) {

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

    @ScriptIgnore
    fun build(): AnimatorComponent = AnimatorComponent(enabled = enabled, layers = layers.toList())

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

@ScriptBinding
fun Entity.setAnimator(builder: KatariAnimatorBuilder) {
    set(builder.build())
    EntitySnapshotPacket(
        id,
        snapshotOf(this),
    ).sendTrackingEntityAndSelf(this)
}

@Serializable
@SerialName("hollowengine:katari_animator")
@ScriptType("Animator")
data class KatariAnimatorBuilderSnapshot(
    val component: KatariAnimatorBuilder,
) : ValueSnapshot(), ScriptSnapshot<KatariAnimatorBuilder> {
    override suspend fun restore(context: ValueRestoreContext): KatariAnimatorBuilder {
        return KatariAnimatorBuilder(component.enabled, component.layers.toMutableList())
    }

    companion object : ScriptSnapshotFactory<KatariAnimatorBuilder, KatariAnimatorBuilderSnapshot> {
        override fun capture(value: KatariAnimatorBuilder): KatariAnimatorBuilderSnapshot {
            return KatariAnimatorBuilderSnapshot(value)
        }
    }
}

internal fun vectorExpression(x: String, y: String, z: String) =
    AnimationVectorExpression(x.expr(), y.expr(), z.expr())

private fun String.expr() = AnimationExpression(ifBlank { "0" })
