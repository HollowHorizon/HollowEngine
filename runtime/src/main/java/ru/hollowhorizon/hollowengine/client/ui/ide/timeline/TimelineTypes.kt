package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import ru.hollowhorizon.hollowengine.common.utils.Color
import ru.hollowhorizon.hollowengine.common.utils.math.*
import kotlin.math.sqrt

/** Axis colors shared by every property type. */
object ChannelColors {
    val X = Color("E06C75")
    val Y = Color("98C379")
    val Z = Color("61AFEF")
    val W = Color("C678DD")
    val SCALAR = Color("E5C07B")
}

/** A single number: FOV, a weight, a speed. [bounds] is what the number is allowed to be. */
class FloatPropertyType(
    name: String = "Value",
    private val bounds: ChannelBounds = ChannelBounds.Unbounded,
) : PropertyType<Float> {
    override val id = ID
    override val channels = listOf(ChannelSpec(name, ChannelColors.SCALAR))
    override val blendModes = setOf(BlendMode.OVERRIDE, BlendMode.ADD, BlendMode.SUBTRACT, BlendMode.MULTIPLY)

    override fun bounds(channel: Int) = bounds

    override fun decompose(value: Float, into: FloatArray) {
        into[0] = value
    }

    override fun compose(values: FloatArray): Float = values[0]

    companion object {
        const val ID = "float"
    }
}

/** A position or an offset. */
class TranslationPropertyType : PropertyType<Vec3f> {
    override val id = ID
    override val channels = listOf(
        ChannelSpec("X", ChannelColors.X),
        ChannelSpec("Y", ChannelColors.Y),
        ChannelSpec("Z", ChannelColors.Z),
    )
    override val blendModes = setOf(BlendMode.OVERRIDE, BlendMode.ADD, BlendMode.SUBTRACT, BlendMode.MULTIPLY)

    override fun decompose(value: Vec3f, into: FloatArray) {
        into[0] = value.x
        into[1] = value.y
        into[2] = value.z
    }

    override fun compose(values: FloatArray): Vec3f = Vec3f(values[0], values[1], values[2])

    companion object {
        const val ID = "translation"
    }
}

enum class RotationMode {
    EULER, QUATERNION
}

class RotationPropertyType(val mode: RotationMode = RotationMode.EULER) : PropertyType<Vec3f> {
    override val id = ID

    override val channels = when (mode) {
        RotationMode.EULER -> listOf(
            ChannelSpec("Pitch", ChannelColors.X, isAngle = true),
            ChannelSpec("Yaw", ChannelColors.Y, isAngle = true),
            ChannelSpec("Roll", ChannelColors.Z, isAngle = true),
        )

        RotationMode.QUATERNION -> listOf(
            ChannelSpec("X", ChannelColors.X),
            ChannelSpec("Y", ChannelColors.Y),
            ChannelSpec("Z", ChannelColors.Z),
            ChannelSpec("W", ChannelColors.W),
        )
    }

    override val blendModes = setOf(BlendMode.OVERRIDE, BlendMode.ADD, BlendMode.SUBTRACT)

    override val isChannelSpaceLinear = mode == RotationMode.EULER

    override fun decompose(value: Vec3f, into: FloatArray) {
        when (mode) {
            RotationMode.EULER -> {
                into[0] = value.x
                into[1] = value.y
                into[2] = value.z
            }

            RotationMode.QUATERNION -> {
                val quat = cameraRotation(value.x, value.y, value.z)
                into[0] = quat.x
                into[1] = quat.y
                into[2] = quat.z
                into[3] = quat.w
            }
        }
    }

    override fun compose(values: FloatArray): Vec3f = when (mode) {
        RotationMode.EULER -> Vec3f(values[0], values[1], values[2])
        RotationMode.QUATERNION -> normalized(values[0], values[1], values[2], values[3]).toCameraEulers()
    }

    private fun normalized(x: Float, y: Float, z: Float, w: Float): QuatF {
        val length = sqrt(x * x + y * y + z * z + w * w)
        if (length <= 1e-6f) return QuatF.IDENTITY
        return QuatF(x / length, y / length, z / length, w / length)
    }

    companion object {
        const val ID = "rotation"
    }
}

fun cameraRotation(pitch: Float, yaw: Float, roll: Float): QuatF =
    MutableQuatF().rotateByEulers(Vec3f(pitch, yaw, roll))

fun QuatF.toCameraEulers(): Vec3f = toEulers()

fun AnimProperty<*>.setRotationMode(mode: RotationMode) {
    val current = type as? RotationPropertyType ?: return
    if (current.mode == mode) return
    @Suppress("UNCHECKED_CAST") (this as AnimProperty<Vec3f>).retype(RotationPropertyType(mode))
}
