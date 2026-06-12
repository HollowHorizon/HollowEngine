package ru.hollowhorizon.hollowengine.common.geary.components

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import java.util.*

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:animator")
@EditorIcon("hollowengine:textures/gui/icons/pose_editor.png")
data class AnimatorComponent(
    val enabled: Boolean = true,
    val layers: List<AnimatorLayerSpec> = emptyList(),
)

@Serializable
sealed class AnimatorLayerSpec {
    abstract val id: String
    abstract val weight: AnimationExpression
    abstract val priority: Int
    abstract val blendMode: LayerBlendMode
    abstract val mask: BoneMask
    abstract val fadeIn: Float
    abstract val fadeOut: Float
}

@Serializable
@SerialName("hollowengine:animator/clip")
data class ClipAnimationLayerSpec(
    override val id: String = newAnimationLayerId("clip"),
    val animation: String,
    val playMode: AnimationPlayMode = AnimationPlayMode.Once,
    val speed: AnimationExpression = AnimationExpression.ONE,
    override val weight: AnimationExpression = AnimationExpression.ONE,
    override val priority: Int = 0,
    override val blendMode: LayerBlendMode = LayerBlendMode.Override,
    override val mask: BoneMask = BoneMask.full(),
    override val fadeIn: Float = 0f,
    override val fadeOut: Float = 0f,
    val referencePose: String? = null,
    val removeOnEnd: Boolean = playMode == AnimationPlayMode.Once,
    val removeAtGameTime: Long? = null,
) : AnimatorLayerSpec()

@Serializable
@SerialName("hollowengine:animator/controller")
data class AnimationControllerLayerSpec(
    override val id: String = newAnimationLayerId("controller"),
    val states: List<AnimationControllerStateSpec> = emptyList(),
    val transitions: List<AnimationControllerTransitionSpec> = emptyList(),
    val entryState: String? = states.firstOrNull()?.id,
    override val weight: AnimationExpression = AnimationExpression.ONE,
    override val priority: Int = 0,
    override val blendMode: LayerBlendMode = LayerBlendMode.Override,
    override val mask: BoneMask = BoneMask.full(),
    override val fadeIn: Float = 0f,
    override val fadeOut: Float = 0f,
) : AnimatorLayerSpec()

@Serializable
@SerialName("hollowengine:animator/procedural")
data class ProceduralLayerSpec(
    override val id: String = newAnimationLayerId("procedural"),
    val transforms: List<ProceduralBoneTransformSpec> = emptyList(),
    override val weight: AnimationExpression = AnimationExpression.ONE,
    override val priority: Int = 0,
    override val blendMode: LayerBlendMode = LayerBlendMode.Additive,
    override val mask: BoneMask = BoneMask.full(),
    override val fadeIn: Float = 0f,
    override val fadeOut: Float = 0f,
) : AnimatorLayerSpec()

@Serializable
@SerialName("hollowengine:animator/material_override")
data class MaterialOverrideLayerSpec(
    override val id: String = newAnimationLayerId("material_override"),
    val overrides: List<MaterialTextureOverride> = emptyList(),
    override val weight: AnimationExpression = AnimationExpression.ONE,
    override val priority: Int = 0,
    override val blendMode: LayerBlendMode = LayerBlendMode.Override,
    override val mask: BoneMask = BoneMask.full(),
    override val fadeIn: Float = 0f,
    override val fadeOut: Float = 0f,
) : AnimatorLayerSpec()

@Serializable
data class MaterialTextureOverride(
    val materialIndex: Int,
    val texture: String,
)

@Serializable
data class AnimationControllerStateSpec(
    val id: String,
    val animation: String,
    val playMode: AnimationPlayMode = AnimationPlayMode.Loop,
    val speed: AnimationExpression = AnimationExpression.ONE,
    val referencePose: String? = null,
)

@Serializable
data class AnimationControllerTransitionSpec(
    val from: String = ANY_STATE,
    val to: String,
    val condition: AnimationExpression = AnimationExpression.TRUE,
    val duration: AnimationExpression = AnimationExpression.ZERO,
    val priority: Int = 0,
    val exitTime: Float? = null,
)

@Serializable
data class ProceduralBoneTransformSpec(
    val bone: String,
    val translation: AnimationVectorExpression? = null,
    val rotation: AnimationVectorExpression? = null,
    val scale: AnimationVectorExpression? = null,
)

@ScriptBinding
fun BoneMask() = BoneMask.full()

@Serializable
data class BoneMask(
    val includes: Set<String> = emptySet(),
    val excludes: Set<String> = emptySet(),
) {
    companion object {
        fun full() = BoneMask(emptySet(), emptySet())
        fun of(vararg bones: String): BoneMask {
            val includes = linkedSetOf<String>()
            val excludes = linkedSetOf<String>()
            bones.forEach { bone ->
                if (bone.startsWith("!")) excludes += bone.drop(1) else includes += bone
            }
            return BoneMask(includes, excludes)
        }
    }
}

@Serializable
@SerialName("hollowengine:katari/bone_mask")
@ScriptType("BoneMask")
class BoneMaskSnapshot(val mask: BoneMask) : ValueSnapshot(), ScriptSnapshot<BoneMask> {
    override suspend fun restore(context: ValueRestoreContext): BoneMask {
        return mask
    }

    companion object : ScriptSnapshotFactory<BoneMask, BoneMaskSnapshot> {
        override fun capture(value: BoneMask): BoneMaskSnapshot {
            return BoneMaskSnapshot(value)
        }

    }
}

@Serializable
data class AnimationExpression(
    val source: String = "0",
) {
    companion object {
        val ZERO = AnimationExpression("0")
        val ONE = AnimationExpression("1")
        val TRUE = AnimationExpression("true")
        val FALSE = AnimationExpression("false")
    }
}

@Serializable
data class AnimationVectorExpression(
    val x: AnimationExpression = AnimationExpression.ZERO,
    val y: AnimationExpression = AnimationExpression.ZERO,
    val z: AnimationExpression = AnimationExpression.ZERO,
) {
    companion object {
        val ZERO = AnimationVectorExpression()
        val ONE = AnimationVectorExpression(AnimationExpression.ONE, AnimationExpression.ONE, AnimationExpression.ONE)
    }
}

@Serializable
@SerialName("hollowengine:katari/animations/vector_expression")
@ScriptType("AnimationVectorExpression")
class AnimationVectorExpressionSnapshot(val expression: AnimationVectorExpression) : ValueSnapshot(), ScriptSnapshot<AnimationVectorExpression> {
    override suspend fun restore(context: ValueRestoreContext): AnimationVectorExpression {
        return expression
    }

    companion object : ScriptSnapshotFactory<AnimationVectorExpression, AnimationVectorExpressionSnapshot> {
        override fun capture(value: AnimationVectorExpression): AnimationVectorExpressionSnapshot {
            return AnimationVectorExpressionSnapshot(value)
        }

    }
}

enum class LayerBlendMode {
    Additive,
    Override,
}

enum class AnimationPlayMode {
    Once,
    Loop,
    ClampForever,
    PingPong,
}

const val ANY_STATE = "__any__"

fun newAnimationLayerId(prefix: String): String = "$prefix:${UUID.randomUUID()}"

fun AnimatorComponent.withLayer(layer: AnimatorLayerSpec): AnimatorComponent =
    copy(layers = layers.filterNot { it.id == layer.id } + layer)

fun AnimatorComponent.withoutLayer(layerId: String): AnimatorComponent =
    copy(layers = layers.filterNot { it.id == layerId })

fun AnimatorComponent.withoutClip(animation: String): AnimatorComponent =
    copy(layers = layers.filterNot { it is ClipAnimationLayerSpec && it.animation == animation })
