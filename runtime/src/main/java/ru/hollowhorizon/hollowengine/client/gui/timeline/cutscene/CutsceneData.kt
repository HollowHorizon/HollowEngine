package ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec3f
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.PropertyDriver

@Serializable
data class Vec3Serializable(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
)

@Serializable
data class FloatSerializable(
    val value: Float = 0f,
)

@Serializable
data class CutsceneKeyframe<T>(
    val time: Float,
    val value: T,
    val easing: String = "linear",
)

@Serializable
enum class CutsceneNodeKind {
    GROUP,
    TRACK,
}

@Serializable
data class CutsceneTrackKeyframeData(
    val time: Float,
    val value: JsonElement,
    val easing: String = "linear",
)

@Serializable
data class CutsceneTrackData(
    val type: String,
    val valueType: String,
    val keyframes: List<CutsceneTrackKeyframeData> = emptyList(),
)

@Serializable
data class CutsceneNodeData(
    val id: String,
    val name: String,
    val kind: CutsceneNodeKind,
    val children: List<CutsceneNodeData> = emptyList(),
    val track: CutsceneTrackData? = null,
)

@Serializable
data class CutsceneData(
    val name: String = "New Cutscene",
    val duration: Float = 10f,
    val nodes: List<CutsceneNodeData> = emptyList(),
    val positionKeyframes: List<CutsceneKeyframe<Vec3Serializable>> = emptyList(),
    val rotationKeyframes: List<CutsceneKeyframe<Vec3Serializable>> = emptyList(),
    val fovKeyframes: List<CutsceneKeyframe<FloatSerializable>> = emptyList(),
)

class CutsceneTrackType<T>(
    val id: String,
    val valueType: String,
    val defaultValue: T,
    val driverFactory: ((T) -> Unit) -> PropertyDriver<T>,
    val encode: (T) -> JsonElement,
    val decode: (JsonElement) -> T?,
)

object CutsceneTrackRegistry {
    private val types = linkedMapOf<String, CutsceneTrackType<*>>()

    fun <T> register(type: CutsceneTrackType<T>) {
        require(type.id !in types) { "Cutscene track type '${type.id}' is already registered" }
        types[type.id] = type
    }

    fun get(id: String): CutsceneTrackType<*>? {
        return types[id]
    }

    fun all(): Collection<CutsceneTrackType<*>> {
        return types.values
    }
}

fun Vec3f.toSerializable(): Vec3Serializable = Vec3Serializable(x, y, z)

fun Vec3Serializable.toVec3f(): Vec3f = Vec3f(x, y, z)

fun Float.toSerializable(): FloatSerializable = FloatSerializable(this)

object EasingRegistry {
    private val easings: Map<String, Easing.Easing> = mapOf(
        "linear" to Easing.linear,
        "smooth" to Easing.smooth,
        "easeInSine" to Easing.easeInSine,
        "easeOutSine" to Easing.easeOutSine,
        "easeInOutSine" to Easing.easeInOutSine,
        "easeInQuad" to Easing.easeInQuad,
        "easeOutQuad" to Easing.easeOutQuad,
        "easeInOutQuad" to Easing.easeInOutQuad,
        "easeInCubic" to Easing.easeInCubic,
        "easeOutCubic" to Easing.easeOutCubic,
        "easeInOutCubic" to Easing.easeInOutCubic,
        "easeInQuart" to Easing.easeInQuart,
        "easeOutQuart" to Easing.easeOutQuart,
        "easeInOutQuart" to Easing.easeInOutQuart,
        "easeInQuint" to Easing.easeInQuint,
        "easeOutQuint" to Easing.easeOutQuint,
        "easeInOutQuint" to Easing.easeInOutQuint,
        "easeInExpo" to Easing.easeInExpo,
        "easeOutExpo" to Easing.easeOutExpo,
        "easeInOutExpo" to Easing.easeInOutExpo,
        "easeInCirc" to Easing.easeInCirc,
        "easeOutCirc" to Easing.easeOutCirc,
        "easeInOutCirc" to Easing.easeInOutCirc,
        "easeInBack" to Easing.easeInBack,
        "easeOutBack" to Easing.easeOutBack,
        "easeInOutBack" to Easing.easeInOutBack,
        "easeInBounce" to Easing.easeInBounce,
        "easeOutBounce" to Easing.easeOutBounce,
        "easeInOutBounce" to Easing.easeInOutBounce,
        "easeInElastic" to Easing.easeInElastic,
        "easeOutElastic" to Easing.easeOutElastic,
        "easeInOutElastic" to Easing.easeInOutElastic,
    )

    fun resolve(name: String): Easing.Easing = easings[name] ?: Easing.linear

    fun nameOf(easing: Easing.Easing): String = easings.entries.firstOrNull { it.value == easing }?.key ?: "linear"
}

internal fun CutsceneKeyframe<Vec3Serializable>.toVec3Keyframe(): Keyframe<Vec3f> {
    return Keyframe(time, value.toVec3f(), EasingRegistry.resolve(easing))
}

internal fun CutsceneKeyframe<FloatSerializable>.toFloatKeyframe(): Keyframe<Float> {
    return Keyframe(time, value.value, EasingRegistry.resolve(easing))
}
