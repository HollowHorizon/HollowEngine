package ru.hollowhorizon.hollowengine.client.ui.ide.timeline.cutscene

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec3f
import kotlinx.serialization.Serializable

@Serializable
enum class CutsceneNodeKind {
    GROUP,
    TRACK,
}

@Serializable
sealed class KeyframeSnapshot {
    @Serializable
    class FloatSnapshot(val value: Float) : KeyframeSnapshot()

    @Serializable
    class Vec3fSnapshot(val x: Float, val y: Float, val z: Float) : KeyframeSnapshot() {
        constructor(value: Vec3f) : this(value.x, value.y, value.z)
        val vector get() = Vec3f(x, y, z)
    }

}

@Serializable
data class CutsceneTrackKeyframeData(
    val time: Float,
    val value: KeyframeSnapshot,
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
)

class CutsceneTrackType<T>(
    val id: String,
    val defaultValue: T,
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