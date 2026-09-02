package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.common.utils.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round


/** Name and color of one scalar component of a property. */
data class ChannelSpec(
    val name: String,
    val color: Color,
    /** Whether the channel is an angle in degrees, and so wraps at +-180. */
    val isAngle: Boolean = false,
)

/** How a layer's value is folded into layers below it produced. */
enum class BlendMode {
    /** Replaces the value below it (scaled by the layer's weight). */
    OVERRIDE,

    /** Adds on top, the mode for shakes, offsets and corrections. */
    ADD,

    /** Subtracts, the inverse of [ADD]. */
    SUBTRACT,

    /** Scales the value below it. */
    MULTIPLY,
}

/** A scalar keyframe on one channel curve. */
class Keyframe(
    var time: Float,
    var value: Float,
    var interpolation: KeyInterpolation = KeyInterpolation.BEZIER,
    var handleMode: HandleMode = HandleMode.AUTO,
    var incoming: KeyTangent = KeyTangent.ZERO,
    var outgoing: KeyTangent = KeyTangent.ZERO,
) {
    fun tangent(side: TangentSide): KeyTangent = when (side) {
        TangentSide.INCOMING -> incoming
        TangentSide.OUTGOING -> outgoing
    }

    fun setTangent(side: TangentSide, tangent: KeyTangent) {
        when (side) {
            TangentSide.INCOMING -> incoming = tangent
            TangentSide.OUTGOING -> outgoing = tangent
        }
    }

    fun copy(time: Float = this.time) = Keyframe(time, value, interpolation, handleMode, incoming, outgoing)
}

/** Which of the two keyframe handles is being modified. */
enum class TangentSide {
    INCOMING, OUTGOING,
}

/**
 * One scalar component of a property, with the curve, that graph editor draws.
 */
class ChannelCurve(val spec: ChannelSpec) {
    val keyframes = mutableStateListOf<Keyframe>()
    var isVisible by mutableStateOf(true)

    val name: String get() = spec.name
    val color: Color get() = spec.color

    fun sort() {
        if (isSorted()) return
        val sorted = keyframes.sortedBy { it.time }
        keyframes.clear()
        keyframes.addAll(sorted)
    }

    private fun isSorted(): Boolean {
        for (index in 1 until keyframes.size) {
            if (keyframes[index - 1].time > keyframes[index].time) return false
        }
        return true
    }

    private fun ordered(): List<Keyframe> = if (isSorted()) keyframes else keyframes.sortedBy { it.time }

    fun keyAt(time: Float, epsilon: Float = KEY_TIME_EPSILON): Keyframe? =
        keyframes.firstOrNull { abs(it.time - time) <= epsilon }

    fun valueAt(time: Float, fallback: Float): Float {
        val keys = ordered()
        if (keys.isEmpty()) return fallback
        val first = keys.first()
        val last = keys.last()
        if (keys.size == 1 || time <= first.time) return first.value
        if (time >= last.time) return last.value

        val index = keys.indexOfLast { it.time <= time }.coerceIn(0, keys.size - 2)
        val start = keys[index]
        val end = keys[index + 1]
        val duration = end.time - start.time
        if (duration <= KEY_TIME_EPSILON) return start.value

        return when (start.interpolation) {
            KeyInterpolation.CONSTANT -> start.value
            KeyInterpolation.LINEAR -> start.value + (end.value - start.value) * ((time - start.time) / duration)
            KeyInterpolation.BEZIER -> TimelineCurve.sampleSegment(
                startTime = start.time,
                startValue = start.value,
                outgoing = effectiveTangents(start).outgoing,
                endTime = end.time,
                endValue = end.value,
                incoming = effectiveTangents(end).incoming,
                time = time,
            )
        }
    }

    fun effectiveTangents(keyframe: Keyframe): ChannelTangents {
        if (keyframe.handleMode != HandleMode.AUTO) {
            return ChannelTangents(keyframe.incoming, keyframe.outgoing)
        }
        val keys = ordered()
        val index = keys.indexOfFirst { it === keyframe }
        if (index < 0) return ChannelTangents(keyframe.incoming, keyframe.outgoing)
        val previous = keys.getOrNull(index - 1)
        val next = keys.getOrNull(index + 1)
        return TimelineCurve.autoTangents(
            previousTime = previous?.time,
            previousValue = previous?.value,
            time = keyframe.time,
            value = keyframe.value,
            nextTime = next?.time,
            nextValue = next?.value,
        )
    }

    fun isTangentUsed(keyframe: Keyframe, side: TangentSide): Boolean {
        val keys = ordered()
        val index = keys.indexOfFirst { it === keyframe }
        if (index < 0) return false
        return when (side) {
            TangentSide.OUTGOING -> index < keys.lastIndex && keyframe.interpolation == KeyInterpolation.BEZIER

            TangentSide.INCOMING -> index > 0 && keys[index - 1].interpolation == KeyInterpolation.BEZIER
        }
    }

    fun useSpline(keyframe: Keyframe, side: TangentSide) {
        val keys = ordered()
        val index = keys.indexOfFirst { it === keyframe }
        if (index < 0) return
        when (side) {
            TangentSide.OUTGOING -> if (index < keys.lastIndex) {
                keyframe.interpolation = KeyInterpolation.BEZIER
            }

            TangentSide.INCOMING -> keys.getOrNull(index - 1)?.interpolation = KeyInterpolation.BEZIER
        }
    }

    companion object {
        const val KEY_TIME_EPSILON = 0.0001f
    }
}

class AnimLayer(name: String, val channels: List<ChannelCurve>) {
    var nameState by mutableStateOf(name)
    var isVisible by mutableStateOf(true)
    var isLocked by mutableStateOf(false)
    var isExpanded by mutableStateOf(true)
    var blendMode by mutableStateOf(BlendMode.OVERRIDE)
    var weight by mutableStateOf(1f)

    val keyframes: List<Keyframe> get() = channels.flatMap { it.keyframes }

    fun curveOf(keyframe: Keyframe): ChannelCurve? =
        channels.firstOrNull { curve -> curve.keyframes.any { it === keyframe } }
}

data class ChannelBounds(val minimum: Float? = null, val maximum: Float? = null) {
    fun clamp(value: Float): Float {
        var result = value
        minimum?.let { result = max(result, it) }
        maximum?.let { result = min(result, it) }
        return result
    }

    companion object {
        val Unbounded = ChannelBounds()
    }
}

class AnimProperty<T>(
    val id: String,
    name: String,
    type: PropertyType<T>,
    val defaultValue: T,
    val apply: ((T) -> Unit)? = null,
) {
    var nameState by mutableStateOf(name)

    var isExpanded by mutableStateOf(true)

    var type: PropertyType<T> by mutableStateOf(type)
        private set

    val layers = mutableStateListOf<AnimLayer>()

    val channels: List<ChannelSpec> get() = type.channels

    fun addLayer(name: String = "Layer ${layers.size + 1}", blendMode: BlendMode = BlendMode.OVERRIDE): AnimLayer {
        val layer = AnimLayer(name, type.channels.map { ChannelCurve(it) })
        layer.blendMode = if (layers.isEmpty()) BlendMode.OVERRIDE else blendMode
        layers.add(layer)
        return layer
    }

    fun retype(next: PropertyType<T>) {
        if (next.channels == type.channels) {
            type = next
            return
        }
        val resampled = layers.map { layer -> resample(layer, type, next) }
        type = next
        val rebuilt = layers.mapIndexed { index, layer ->
            val channels = next.channels.mapIndexed { channel, spec ->
                ChannelCurve(spec).also { it.keyframes.addAll(resampled[index][channel]) }
            }
            AnimLayer(layer.nameState, channels).also {
                it.blendMode = layer.blendMode
                it.weight = layer.weight
                it.isVisible = layer.isVisible
                it.isLocked = layer.isLocked
                it.isExpanded = layer.isExpanded
            }
        }
        layers.clear()
        layers.addAll(rebuilt)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun restoreState(type: PropertyType<*>, layers: List<AnimLayer>) {
        this.type = type as PropertyType<T>
        this.layers.clear()
        this.layers.addAll(layers)
    }

    private fun resample(layer: AnimLayer, from: PropertyType<T>, to: PropertyType<T>): List<List<Keyframe>> {
        val times = layer.channels.flatMap { curve -> curve.keyframes.map { it.time } }
            .distinctBy { round(it / ChannelCurve.KEY_TIME_EPSILON) }.sorted()
        val channelValues = to.channels.indices.map { mutableListOf<Keyframe>() }
        val buffer = FloatArray(from.channels.size)
        times.forEach { time ->
            from.decompose(defaultValue, buffer)
            layer.channels.forEachIndexed { index, curve ->
                buffer[index] = curve.valueAt(time, buffer[index])
            }
            val value = from.compose(buffer)
            val next = FloatArray(to.channels.size)
            to.decompose(value, next)
            next.forEachIndexed { index, component ->
                channelValues[index].add(Keyframe(time, component))
            }
        }
        return channelValues
    }

    fun bounds(channel: Int): ChannelBounds = type.bounds(channel)

    fun valueAt(time: Float): T {
        val size = type.channels.size
        val values = FloatArray(size)
        type.decompose(defaultValue, values)
        layers.forEach { layer ->
            if (!layer.isVisible) return@forEach
            val weight = layer.weight
            if (weight == 0f) return@forEach
            for (channel in 0 until size) {
                val curve = layer.channels.getOrNull(channel) ?: continue
                if (!curve.isVisible) continue
                val base = values[channel]
                val neutral = layer.blendMode.neutral(base)
                val sampled = curve.valueAt(time, neutral)
                values[channel] = layer.blendMode.blend(base, sampled, weight)
            }
        }
        for (channel in 0 until size) values[channel] = type.bounds(channel).clamp(values[channel])
        return type.compose(values)
    }

    fun update(time: Float) {
        apply?.invoke(valueAt(time))
    }

    fun curveOf(keyframe: Keyframe): ChannelCurve? = layers.firstNotNullOfOrNull { it.curveOf(keyframe) }

}

private fun BlendMode.neutral(base: Float): Float = when (this) {
    BlendMode.OVERRIDE -> base
    BlendMode.ADD, BlendMode.SUBTRACT -> 0f
    BlendMode.MULTIPLY -> 1f
}

private fun BlendMode.blend(base: Float, value: Float, weight: Float): Float = when (this) {
    BlendMode.OVERRIDE -> base + (value - base) * weight
    BlendMode.ADD -> base + value * weight
    BlendMode.SUBTRACT -> base - value * weight
    BlendMode.MULTIPLY -> base * (1f + (value - 1f) * weight)
}

interface PropertyType<T> {
    val id: String
    val channels: List<ChannelSpec>

    fun bounds(channel: Int): ChannelBounds = ChannelBounds.Unbounded

    val isChannelSpaceLinear: Boolean get() = true
    val blendModes: Set<BlendMode> get() = setOf(BlendMode.OVERRIDE, BlendMode.ADD, BlendMode.SUBTRACT)

    fun decompose(value: T, into: FloatArray)
    fun compose(values: FloatArray): T
}

class TrackGroup(name: String) {
    var nameState by mutableStateOf(name)
    var isCollapsed by mutableStateOf(false)
    var isLocked by mutableStateOf(false)
    var isVisible by mutableStateOf(true)

    val children = mutableStateListOf<TrackGroup>()
    val properties = mutableStateListOf<AnimProperty<*>>()

    fun allProperties(): List<AnimProperty<*>> = properties + children.flatMap { it.allProperties() }
}
