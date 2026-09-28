package ru.hollowhorizon.hollowengine.client.vfx

import ru.hollowhorizon.hollowengine.common.utils.MutableColor
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxColorValue
import ru.hollowhorizon.hollowengine.common.vfx.VfxCurveInput
import ru.hollowhorizon.hollowengine.common.vfx.VfxValue
import ru.hollowhorizon.hollowengine.common.vfx.VfxVec3Value

/**
 * An authored number, ready to read.
 *
 * Built once when an effect is compiled and then called per particle per step.
 */
fun interface VfxFloatSampler {
    fun eval(context: VfxEvalContext): Float
}

/** Three samplers that fill a vector without allocating one. */
class VfxVec3Sampler(
    private val x: VfxFloatSampler,
    private val y: VfxFloatSampler,
    private val z: VfxFloatSampler,
) {
    fun eval(context: VfxEvalContext, into: MutableVec3f): MutableVec3f {
        into.x = x.eval(context)
        into.y = y.eval(context)
        into.z = z.eval(context)
        return into
    }

    /** For properties whose three axes are driven by the X value alone. */
    fun evalUniform(context: VfxEvalContext, into: MutableVec3f): MutableVec3f {
        val value = x.eval(context)
        into.x = value
        into.y = value
        into.z = value
        return into
    }
}

class VfxColorSampler(private val eval: (VfxEvalContext, MutableColor) -> Unit) {
    fun eval(context: VfxEvalContext, into: MutableColor): MutableColor {
        eval.invoke(context, into)
        return into
    }
}

/**
 * What a timeline track writes for one property of one node.
 */
class VfxDrive(channels: Int) {
    val active = BooleanArray(channels)
    val values = FloatArray(channels)
}

/**
 * How a range is read.
 *
 * A value read once per particle can simply draw a fresh number. A value read every step cannot, a
 * size picked anew each step flickers.
 */
enum class VfxRangeMode {
    FRESH,
    PER_PARTICLE,
}

object VfxSamplers {
    fun scalar(
        value: VfxValue,
        expressions: VfxExpressions,
        default: Float = 0f,
        range: VfxRangeMode = VfxRangeMode.FRESH,
        salt: Int = 0,
    ): VfxFloatSampler = when (value) {
        is VfxValue.Const -> {
            val constant = value.value
            VfxFloatSampler { constant }
        }

        is VfxValue.Range -> {
            val from = value.from
            val span = value.to - value.from
            when {
                span == 0f -> VfxFloatSampler { from }
                range == VfxRangeMode.PER_PARTICLE -> VfxFloatSampler { from + stableRandom(it.particleRandom, salt) * span }
                else -> VfxFloatSampler { from + it.rng.nextFloat() * span }
            }
        }

        is VfxValue.OverTime -> {
            val curve = value.curve
            val scale = value.scale
            val axis = axis(value.input)
            VfxFloatSampler { curve.valueAt(axis.eval(it), default) * scale }
        }

        is VfxValue.Expr -> {
            val compiled = expressions.float(value.source, default)
            VfxFloatSampler { compiled(it) }
        }
    }

    fun vec3(
        value: VfxVec3Value,
        expressions: VfxExpressions,
        default: Float = 0f,
        range: VfxRangeMode = VfxRangeMode.FRESH,
        salt: Int = 0,
        drive: VfxDrive? = null,
    ) = VfxVec3Sampler(
        driven(scalar(value.x, expressions, default, range, salt), drive, 0),
        driven(scalar(value.y, expressions, default, range, salt + 1), drive, 1),
        driven(scalar(value.z, expressions, default, range, salt + 2), drive, 2),
    )

    fun color(
        value: VfxColorValue,
        expressions: VfxExpressions,
        salt: Int = 0,
        drive: VfxDrive? = null,
    ): VfxColorSampler {
        val authored = authoredColor(value, expressions, salt)
        if (drive == null) return authored
        return VfxColorSampler { context, into ->
            authored.eval(context, into)
            if (drive.active[0]) into.r = drive.values[0]
            if (drive.active[1]) into.g = drive.values[1]
            if (drive.active[2]) into.b = drive.values[2]
            if (drive.active[3]) into.a = drive.values[3]
        }
    }

    private fun authoredColor(value: VfxColorValue, expressions: VfxExpressions, salt: Int): VfxColorSampler =
        when (value) {
            is VfxColorValue.Solid -> {
                val color = value.color
                VfxColorSampler { _, into -> into.set(color.r, color.g, color.b, color.a) }
            }

            is VfxColorValue.Gradient -> {
                val gradient = value.gradient
                val axis = axis(value.input)
                VfxColorSampler { context, into ->
                    val stop = gradient.colorAt(axis.eval(context))
                    into.set(stop.r, stop.g, stop.b, stop.a)
                }
            }

            is VfxColorValue.Channels -> {
                val r = scalar(value.r, expressions, 1f, VfxRangeMode.PER_PARTICLE, salt)
                val g = scalar(value.g, expressions, 1f, VfxRangeMode.PER_PARTICLE, salt + 1)
                val b = scalar(value.b, expressions, 1f, VfxRangeMode.PER_PARTICLE, salt + 2)
                val a = scalar(value.a, expressions, 1f, VfxRangeMode.PER_PARTICLE, salt + 3)
                VfxColorSampler { context, into ->
                    into.set(r.eval(context), g.eval(context), b.eval(context), a.eval(context))
                }
            }
        }

    /** [sampler], unless the timeline has a curve for this channel. */
    fun driven(sampler: VfxFloatSampler, drive: VfxDrive?, channel: Int = 0): VfxFloatSampler {
        if (drive == null || channel >= drive.values.size) return sampler
        return VfxFloatSampler { if (drive.active[channel]) drive.values[channel] else sampler.eval(it) }
    }

    /** What the horizontal axis of a curve or gradient reads. */
    fun axis(input: VfxCurveInput): VfxFloatSampler = when (input) {
        VfxCurveInput.LIFETIME -> VfxFloatSampler { it.progress }
        VfxCurveInput.PARTICLE_AGE -> VfxFloatSampler { it.age }
        VfxCurveInput.EMITTER_AGE -> VfxFloatSampler { it.emitterAge }
        VfxCurveInput.EFFECT_TIME -> VfxFloatSampler { it.effectTime }
        VfxCurveInput.SPEED -> VfxFloatSampler { it.speed }
        VfxCurveInput.RANDOM -> VfxFloatSampler { it.particleRandom }
        VfxCurveInput.PARENT_RANDOM -> VfxFloatSampler { it.parentRandom }
    }

    /** Whether a value reads same for a particle every step. */
    fun isFixedPerParticle(value: VfxValue): Boolean = value is VfxValue.Const || value is VfxValue.Range

    fun isFixedPerParticle(value: VfxVec3Value): Boolean =
        isFixedPerParticle(value.x) && isFixedPerParticle(value.y) && isFixedPerParticle(value.z)

    fun isFixedPerParticle(value: VfxColorValue): Boolean = when (value) {
        is VfxColorValue.Solid -> true
        is VfxColorValue.Gradient -> false
        is VfxColorValue.Channels ->
            isFixedPerParticle(value.r) && isFixedPerParticle(value.g) &&
                    isFixedPerParticle(value.b) && isFixedPerParticle(value.a)
    }

    /** A number in 0..1 that depends only on the particle seed and [salt]. */
    fun stableRandom(seed: Float, salt: Int): Float {
        var hash = seed.toRawBits() xor (salt * -0x61c88647)
        hash = (hash xor (hash ushr 16)) * -0x7a143595
        hash = (hash xor (hash ushr 13)) * -0x3d4d51cb
        hash = hash xor (hash ushr 16)
        return (hash ushr 8) * (1f / (1 shl 24))
    }
}
